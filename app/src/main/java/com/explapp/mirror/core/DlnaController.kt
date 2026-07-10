package com.explapp.mirror.core

import com.explapp.mirror.model.CastDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class DlnaController {
    suspend fun play(device: CastDevice, mediaUrl: String, mimeType: String): DlnaControlResult = withContext(Dispatchers.IO) {
        val controlUrl = device.avTransportControlUrl
            ?: return@withContext DlnaControlResult(false, "الجهاز لا يعلن عن خدمة AVTransport.")

        val profile = CompatibilityResolver.resolve(device)
        val attempts = metadataStrategies(profile)
        var lastResult = DlnaControlResult(false, "لم تنجح محاولات التشغيل.")

        if (profile == CompatibilityProfile.RECEIVER_FRIENDLY) {
            sendTransportAction(controlUrl, "Stop")
            delay(180)
        }

        for (strategy in attempts) {
            val setUri = postSoap(
                controlUrl,
                "urn:schemas-upnp-org:service:AVTransport:1#SetAVTransportURI",
                setUriBody(mediaUrl, mimeType, strategy)
            )
            lastResult = setUri.copy(strategy = strategy.name)
            if (!setUri.success) continue

            delay(if (profile == CompatibilityProfile.RECEIVER_FRIENDLY) 350 else 140)
            val play = sendTransportAction(controlUrl, "Play", includeSpeed = true)
            lastResult = play.copy(strategy = strategy.name)
            if (play.success) {
                return@withContext play.copy(
                    message = "تم إرسال أمر التشغيل بنجاح باستخدام وضع ${strategy.arabicName}.",
                    strategy = strategy.name
                )
            }
        }

        lastResult.copy(message = "فشلت جميع أوضاع التوافق. ${lastResult.message}")
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

    private fun metadataStrategies(profile: CompatibilityProfile): List<MetadataStrategy> = when (profile) {
        CompatibilityProfile.RECEIVER_FRIENDLY -> listOf(MetadataStrategy.EMPTY, MetadataStrategy.SIMPLE, MetadataStrategy.FULL)
        CompatibilityProfile.HISENSE_ANYVIEW -> listOf(MetadataStrategy.SIMPLE, MetadataStrategy.EMPTY, MetadataStrategy.FULL)
        else -> listOf(MetadataStrategy.FULL, MetadataStrategy.SIMPLE, MetadataStrategy.EMPTY)
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
            connectTimeout = 3000
            readTimeout = 5000
            doOutput = true
            setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            setRequestProperty("SOAPAction", "\"$soapAction\"")
            setRequestProperty("Connection", "close")
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

    private fun setUriBody(mediaUrl: String, mimeType: String, strategy: MetadataStrategy): String {
        val escapedUrl = escapeXml(mediaUrl)
        val safeMimeType = mimeType.ifBlank { "application/octet-stream" }
        val metadata = when (strategy) {
            MetadataStrategy.EMPTY -> ""
            MetadataStrategy.SIMPLE -> {
                val raw = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item id="1" parentID="0" restricted="1"><res protocolInfo="http-get:*:$safeMimeType:*">$escapedUrl</res></item></DIDL-Lite>"""
                escapeXml(raw)
            }
            MetadataStrategy.FULL -> {
                val upnpClass = when {
                    safeMimeType.startsWith("image/") -> "object.item.imageItem.photo"
                    safeMimeType.startsWith("video/") -> "object.item.videoItem"
                    safeMimeType.startsWith("audio/") -> "object.item.audioItem.musicTrack"
                    else -> "object.item"
                }
                val raw = """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"><item id="1" parentID="0" restricted="1"><dc:title>ExplApp Mirror</dc:title><upnp:class>$upnpClass</upnp:class><res protocolInfo="http-get:*:$safeMimeType:*">$escapedUrl</res></item></DIDL-Lite>"""
                escapeXml(raw)
            }
        }

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

enum class MetadataStrategy(val arabicName: String) {
    FULL("Metadata كاملة"),
    SIMPLE("Metadata مبسطة"),
    EMPTY("بدون Metadata")
}

data class DlnaControlResult(
    val success: Boolean,
    val message: String,
    val httpCode: Int? = null,
    val responseBody: String = "",
    val strategy: String? = null
)
