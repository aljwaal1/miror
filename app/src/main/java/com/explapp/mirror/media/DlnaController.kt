package com.explapp.mirror.media

import com.explapp.mirror.model.CastDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class DlnaController {
    suspend fun play(device: CastDevice, mediaUrl: String, mimeType: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val descriptionUrl = device.services.firstOrNull { it.startsWith("http", ignoreCase = true) }
                ?: error("لم يتم العثور على رابط وصف DLNA")

            val description = httpGet(descriptionUrl)
            val controlPath = findAvTransportControlUrl(description)
                ?: error("الجهاز لا يعلن عن خدمة AVTransport")

            val base = URI(descriptionUrl)
            val controlUrl = if (controlPath.startsWith("http")) {
                controlPath
            } else {
                URI(base.scheme, base.authority, controlPath, null, null).toString()
            }

            val metadata = buildMetadata(mediaUrl, mimeType)
            soap(
                controlUrl,
                "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI",
                """
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>${xml(mediaUrl)}</CurrentURI>
                  <CurrentURIMetaData>${xml(metadata)}</CurrentURIMetaData>
                </u:SetAVTransportURI>
                """.trimIndent()
            )

            soap(
                controlUrl,
                "urn:schemas-upnp-org:service:AVTransport:1#Play",
                """
                <u:Play xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <Speed>1</Speed>
                </u:Play>
                """.trimIndent()
            )
        }
    }

    suspend fun stop(device: CastDevice): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val descriptionUrl = device.services.firstOrNull { it.startsWith("http", ignoreCase = true) }
                ?: error("لم يتم العثور على رابط وصف DLNA")
            val description = httpGet(descriptionUrl)
            val controlPath = findAvTransportControlUrl(description)
                ?: error("الجهاز لا يعلن عن خدمة AVTransport")
            val base = URI(descriptionUrl)
            val controlUrl = if (controlPath.startsWith("http")) controlPath else URI(base.scheme, base.authority, controlPath, null, null).toString()
            soap(
                controlUrl,
                "urn:schemas-upnp-org:service:AVTransport:1#Stop",
                """
                <u:Stop xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                </u:Stop>
                """.trimIndent()
            )
        }
    }

    private fun findAvTransportControlUrl(xml: String): String? {
        val serviceBlocks = Regex("<service>(.*?)</service>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(xml)
            .map { it.groupValues[1] }
        val block = serviceBlocks.firstOrNull { it.contains("AVTransport", ignoreCase = true) } ?: return null
        return Regex("<controlURL>(.*?)</controlURL>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(block)?.groupValues?.get(1)?.trim()
    }

    private fun httpGet(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 2500
        connection.readTimeout = 2500
        connection.requestMethod = "GET"
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun soap(url: String, action: String, body: String) {
        val envelope = """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>$body</s:Body>
            </s:Envelope>
        """.trimIndent()

        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 3000
        connection.readTimeout = 3000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPACTION", "\"$action\"")
        connection.outputStream.use { it.write(envelope.toByteArray()) }
        val code = connection.responseCode
        if (code !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            error("فشل أمر DLNA: HTTP $code $error")
        }
    }

    private fun buildMetadata(url: String, mimeType: String): String = """
        <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
          <item id="0" parentID="0" restricted="1">
            <dc:title>ExplApp Mirror</dc:title>
            <upnp:class>${if (mimeType.startsWith("image/")) "object.item.imageItem.photo" else "object.item.videoItem"}</upnp:class>
            <res protocolInfo="http-get:*:$mimeType:*">${xml(url)}</res>
          </item>
        </DIDL-Lite>
    """.trimIndent()

    private fun xml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
