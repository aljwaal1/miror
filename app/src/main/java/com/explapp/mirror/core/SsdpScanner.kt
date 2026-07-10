package com.explapp.mirror.core

import com.explapp.mirror.model.CastDevice
import com.explapp.mirror.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class SsdpScanner {
    private val descriptionParser = DeviceDescriptionParser()

    suspend fun scan(timeoutMs: Int = 1800): List<CastDevice> = withContext(Dispatchers.IO) {
        val results = linkedMapOf<String, CastDevice>()
        val request = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: ssdp:all\r\n\r\n"

        runCatching {
            DatagramSocket().use { socket ->
                socket.soTimeout = 450
                val address = InetAddress.getByName("239.255.255.250")
                val packet = DatagramPacket(request.toByteArray(), request.length, address, 1900)
                repeat(2) { socket.send(packet) }

                val started = System.currentTimeMillis()
                while (System.currentTimeMillis() - started < timeoutMs) {
                    val buffer = ByteArray(4096)
                    val response = DatagramPacket(buffer, buffer.size)
                    runCatching { socket.receive(response) }.onSuccess {
                        val text = String(response.data, 0, response.length)
                        val ip = response.address.hostAddress ?: return@onSuccess
                        val location = extractHeader(text, "LOCATION")
                        val current = results[ip]
                        val services = (current?.services.orEmpty() + listOfNotNull(
                            extractHeader(text, "ST"),
                            extractHeader(text, "USN"),
                            location
                        )).distinct()

                        results[ip] = CastDevice(
                            name = current?.name ?: extractHeader(text, "SERVER") ?: "جهاز UPnP / DLNA",
                            ipAddress = ip,
                            type = current?.type ?: detectType(text),
                            manufacturer = current?.manufacturer.orEmpty(),
                            modelName = current?.modelName.orEmpty(),
                            modelNumber = current?.modelNumber.orEmpty(),
                            uuid = current?.uuid.orEmpty(),
                            locationUrl = location ?: current?.locationUrl,
                            avTransportControlUrl = current?.avTransportControlUrl,
                            renderingControlUrl = current?.renderingControlUrl,
                            services = services,
                            isReachable = true
                        )
                    }
                }
            }
        }

        coroutineScope {
            results.values.map { device ->
                async { descriptionParser.enrich(device) }
            }.awaitAll()
        }.distinctBy { it.ipAddress }
    }

    private fun extractHeader(text: String, key: String): String? {
        return text.lineSequence()
            .firstOrNull { it.startsWith("$key:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun detectType(text: String): DeviceType {
        val lower = text.lowercase()
        return when {
            "dial" in lower || "chromecast" in lower -> DeviceType.CHROMECAST
            "mediarenderer" in lower || "dlna" in lower -> DeviceType.DLNA
            "anyview" in lower || "hisense" in lower || "miracast" in lower -> DeviceType.ANYVIEW
            "upnp" in lower -> DeviceType.UPNP
            else -> DeviceType.UNKNOWN
        }
    }
}
