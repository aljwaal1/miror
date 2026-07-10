package com.explapp.mirror.core

import com.explapp.mirror.model.CastDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class DlnaController {
    suspend fun play(device: CastDevice, mediaUrl: String, mimeType: String): DlnaControlResult = withContext(Dispatchers.IO) {
        val controlUrl = device.avTransportControlUrl
            ?: return@withContext DlnaControlResult(false, "الجهاز لا يعلن عن خدمة AVTransport.")

        val setUri = postSoap(
            controlUrl,
            "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI",
            setUriBody(mediaUrl, mimeType)
        )
        if (!setUri.success) return@withContext setUri

        sendTransportAction(controlUrl, "Play", includeSpeed = true)
    }

    suspend fun resume(device: CastDevice): DlnaControlResult = transport(device, "Play", true)
    suspend fun pause(device: CastDevice): DlnaControlResult = transport(device, "Pause")
    suspend fun stop(device: CastDevice): DlnaControlResult = transport(device, "Stop")

    suspend fun setVolume(device: CastDevice, volume: Int): DlnaControlResult = withContext(Dispatchers.IO) {
        val controlUrl = device.renderingControlUrl
            ?: return@withContext DlnaControlResult(false, "الجهاز لا يدعم التحكم بالصوت عبر DLNA.")
        val safeVolume = volume.coerceIn(0, 100)
        postSoap(
            controlUrl,
            "urn:schemas-upnp-org:service:RenderingControl:1#SetVolume",
            volumeBody(safeVolume)
        )
    }

    private suspend fun transport(
        device: CastDevice,
        action: String,
        includeSpeed: Boolean = false
    ): DlnaControlResult = withContext(Dispatchers.IO) {
        val controlUrl = device.avTransportControlUrl
            ?: return@withContext DlnaControlResult(false, "الجهاز لا يعلن عن خدمة AVTransport.")
        sendTransportAction(controlUrl, action, includeSpeed)
    }

    private fun sendTransportAction(controlUrl: String, action: String, includeSpeed: Boolean = false): DlnaControlResult {
        return postSoap(
            controlUrl,
            "urn:schemas-upnp-org:service:AVTransport:1#$action",
            transportActionBody(action, includeSpeed)
        )
    }

    private fun postSoap(controlUrl: String, soapAction: String, body: String): DlnaControlResult {
        val connection = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 2500
            readTimeout = 4000
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            setRequestProperty("SOAPAction", "\"$soapAction\"")
        }

        return runCatching {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code in 200..299) {
                DlnaControlResult(true, "تم تنفيذ الأمر بنجاح.", code, response)
            } else {
                DlnaControlResult(false, "رفض الجهاز الأمر. HTTP $code", code, response)
            }
        }.getOrElse {
            DlnaControlResult(false, "تعذر الاتصال بخدمة DLNA: ${it.message.orEmpty()}")
        }.also { connection.disconnect() }
    }

    private fun setUriBody(mediaUrl: String, mimeType: String): String {
        val escapedUrl = escapeXml(mediaUrl)
        val safeMimeType = mimeType.ifBlank { "application/octet-stream" }
        val upnpClass = when {
            safeMimeType.startsWith("image/") -> "object.item.imageItem.photo"
            safeMimeType.startsWith("video/") -> "object.item.videoItem"
            safeMimeType.startsWith("audio/") -> "object.item.audioItem.musicTrack"
            else -> "object.item"
        }
        val title = when {
            safeMimeType.startsWith("image/") -> "ExplApp Mirror Photo"
            safeMimeType.startsWith("video/") -> "ExplApp Mirror Video"
            else -> "ExplApp Mirror Media"
        }
        val metadata = """
            <DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/">
              <item id="1" parentID="0" restricted="1">
                <dc:title>$title</dc:title>
                <upnp:class>$upnpClass</upnp:class>
                <res protocolInfo="http-get:*:$safeMimeType:*">$escapedUrl</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>
      <CurrentURI>$escapedUrl</CurrentURI>
      <CurrentURIMetaData>$metadata</CurrentURIMetaData>
    </u:SetAVTransportURI>
  </s:Body>
</s:Envelope>"""
    }

    private fun transportActionBody(action: String, includeSpeed: Boolean): String {
        val speed = if (includeSpeed) "\n      <Speed>1</Speed>" else ""
        return """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:$action xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
      <InstanceID>0</InstanceID>$speed
    </u:$action>
  </s:Body>
</s:Envelope>"""
    }

    private fun volumeBody(volume: Int): String = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
  <s:Body>
    <u:SetVolume xmlns:u="urn:schemas-upnp-org:service:RenderingControl:1">
      <InstanceID>0</InstanceID>
      <Channel>Master</Channel>
      <DesiredVolume>$volume</DesiredVolume>
    </u:SetVolume>
  </s:Body>
</s:Envelope>"""

    private fun escapeXml(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
}

data class DlnaControlResult(
    val success: Boolean,
    val message: String,
    val httpCode: Int? = null,
    val responseBody: String = ""
)
