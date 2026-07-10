package com.explapp.mirror.core

import com.explapp.mirror.model.CastDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class DlnaController {
    suspend fun play(device: CastDevice, mediaUrl: String, mimeType: String): DlnaControlResult = withContext(Dispatchers.IO) {
        val locationUrl = device.services.firstOrNull { it.startsWith("http", ignoreCase = true) }
            ?: return@withContext DlnaControlResult(false, "لم يتم العثور على رابط وصف DLNA للجهاز.")

        val controlUrl = runCatching { discoverAvTransportControlUrl(locationUrl) }.getOrNull()
            ?: return@withContext DlnaControlResult(false, "لم يتم العثور على خدمة AVTransport داخل وصف الجهاز.")

        val setUriOk = postSoap(
            controlUrl = controlUrl,
            soapAction = "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI",
            body = setUriBody(mediaUrl, mimeType)
        )

        if (!setUriOk) {
            return@withContext DlnaControlResult(false, "تم العثور على DLNA لكن فشل إرسال رابط الملف للتلفاز.")
        }

        val playOk = postSoap(
            controlUrl = controlUrl,
            soapAction = "urn:schemas-upnp-org:service:AVTransport:1#Play",
            body = playBody()
        )

        if (playOk) {
            DlnaControlResult(true, "تم إرسال أمر تشغيل DLNA للتلفاز.")
        } else {
            DlnaControlResult(false, "تم إرسال رابط الملف، لكن فشل أمر التشغيل.")
        }
    }

    private fun discoverAvTransportControlUrl(locationUrl: String): String? {
        val xml = URL(locationUrl).readText()
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray()))

        val services = document.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as? Element ?: continue
            val serviceType = childText(service, "serviceType").orEmpty()
            if (serviceType.contains("AVTransport", ignoreCase = true)) {
                val control = childText(service, "controlURL") ?: return null
                return URL(URL(locationUrl), control).toString()
            }
        }
        return null
    }

    private fun childText(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun postSoap(controlUrl: String, soapAction: String, body: String): Boolean {
        val connection = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2500
            readTimeout = 3500
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            setRequestProperty("SOAPAction", "\"$soapAction\"")
        }

        return runCatching {
            connection.outputStream.use { output ->
                output.write(body.toByteArray(Charsets.UTF_8))
            }
            connection.responseCode in 200..299
        }.getOrDefault(false).also {
            connection.disconnect()
        }
    }

    private fun setUriBody(mediaUrl: String, mimeType: String): String {
        val escapedUrl = mediaUrl
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>$escapedUrl</CurrentURI>
                  <CurrentURIMetaData>&lt;DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"&gt;&lt;item id="1" parentID="0" restricted="1"&gt;&lt;dc:title&gt;ExplApp Mirror&lt;/dc:title&gt;&lt;res protocolInfo="http-get:*:$mimeType:*"&gt;$escapedUrl&lt;/res&gt;&lt;/item&gt;&lt;/DIDL-Lite&gt;</CurrentURIMetaData>
                </u:SetAVTransportURI>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }

    private fun playBody(): String {
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <Speed>1</Speed>
                </u:Play>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
    }
}

data class DlnaControlResult(
    val success: Boolean,
    val message: String
)
