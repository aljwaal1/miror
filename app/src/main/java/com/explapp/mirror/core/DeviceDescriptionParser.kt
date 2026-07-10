package com.explapp.mirror.core

import com.explapp.mirror.model.CastDevice
import com.explapp.mirror.model.DeviceType
import org.w3c.dom.Element
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class DeviceDescriptionParser {
    fun enrich(base: CastDevice): CastDevice {
        val location = base.locationUrl ?: return base
        val xml = download(location) ?: return base
        val document = runCatching {
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(xml.byteInputStream())
        }.getOrNull() ?: return base

        val rootDevice = document.getElementsByTagName("device").item(0) as? Element ?: return base
        val friendlyName = childText(rootDevice, "friendlyName").orEmpty()
        val manufacturer = childText(rootDevice, "manufacturer").orEmpty()
        val modelName = childText(rootDevice, "modelName").orEmpty()
        val modelNumber = childText(rootDevice, "modelNumber").orEmpty()
        val udn = childText(rootDevice, "UDN").orEmpty().removePrefix("uuid:")

        var avTransport: String? = null
        var renderingControl: String? = null
        val serviceNames = mutableListOf<String>()
        val services = document.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val serviceType = childText(service, "serviceType").orEmpty()
            val controlUrl = childText(service, "controlURL")
            if (serviceType.isNotBlank()) serviceNames += serviceType
            if (controlUrl != null && serviceType.contains("AVTransport", true)) {
                avTransport = URL(URL(location), controlUrl).toString()
            }
            if (controlUrl != null && serviceType.contains("RenderingControl", true)) {
                renderingControl = URL(URL(location), controlUrl).toString()
            }
        }

        val allText = listOf(friendlyName, manufacturer, modelName, base.services.joinToString(" ")).joinToString(" ")
        val type = detectType(allText, avTransport)

        return base.copy(
            name = friendlyName.ifBlank { base.name },
            type = type,
            manufacturer = manufacturer.ifBlank { base.manufacturer },
            modelName = modelName,
            modelNumber = modelNumber,
            uuid = udn,
            avTransportControlUrl = avTransport,
            renderingControlUrl = renderingControl,
            services = (base.services + serviceNames).distinct()
        )
    }

    private fun download(url: String): String? {
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 1800
                readTimeout = 2500
                requestMethod = "GET"
            }
            connection.inputStream.bufferedReader().use { it.readText() }
                .also { connection.disconnect() }
        }.getOrNull()
    }

    private fun childText(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun detectType(text: String, avTransport: String?): DeviceType {
        val lower = text.lowercase()
        return when {
            "chromecast" in lower || "google cast" in lower || "dial" in lower -> DeviceType.CHROMECAST
            "anyview" in lower || "hisense" in lower || "miracast" in lower -> DeviceType.ANYVIEW
            avTransport != null || "mediarenderer" in lower || "dlna" in lower -> DeviceType.DLNA
            "android tv" in lower || "google tv" in lower -> DeviceType.ANDROID_TV
            "television" in lower || "smart tv" in lower || "tv" in lower -> DeviceType.TV
            else -> DeviceType.UPNP
        }
    }
}
