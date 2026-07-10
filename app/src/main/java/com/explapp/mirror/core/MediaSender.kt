package com.explapp.mirror.core

import android.content.Context
import android.net.Uri
import com.explapp.mirror.model.CastDevice
import com.explapp.mirror.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaSender(private val context: Context) {
    private val localMediaServer = LocalMediaServer(context.applicationContext)
    private val dlnaController = DlnaController()
    private var lastResult: MediaSendResult? = null

    suspend fun prepareSend(device: CastDevice, mediaUri: Uri): MediaSendResult = withContext(Dispatchers.IO) {
        val rawMimeType = context.contentResolver.getType(mediaUri).orEmpty()
        val mimeType = rawMimeType.ifBlank { "application/octet-stream" }
        val mediaKind = when {
            mimeType.startsWith("image/") -> MediaKind.IMAGE
            mimeType.startsWith("video/") -> MediaKind.VIDEO
            else -> MediaKind.UNKNOWN
        }

        val route = chooseRoute(device)
        val localServerResult = if (route != MediaRoute.UNKNOWN) {
            runCatching { localMediaServer.start(mediaUri, mimeType) }.getOrNull()
        } else null

        val dlnaResult = if (localServerResult != null && (route == MediaRoute.DLNA || route == MediaRoute.ANYVIEW)) {
            dlnaController.play(device, localServerResult.url, mimeType)
        } else null

        val message = when (route) {
            MediaRoute.CHROMECAST -> {
                if (localServerResult != null) {
                    "تم تجهيز رابط محلي للملف. Chromecast يحتاج Google Cast SDK لإرسال هذا الرابط."
                } else {
                    "تم اختيار مسار Chromecast، لكن لم يتم تجهيز الرابط المحلي للملف."
                }
            }
            MediaRoute.DLNA -> {
                when {
                    dlnaResult?.success == true -> dlnaResult.message
                    dlnaResult != null -> "تم تجهيز الرابط المحلي، لكن لم يكتمل تشغيل DLNA: ${dlnaResult.message}"
                    localServerResult != null -> "تم تجهيز رابط محلي للملف، لكن الجهاز لا يعلن عن رابط تحكم DLNA صالح."
                    else -> "تم اختيار مسار DLNA / UPnP، لكن لم يتم تجهيز الرابط المحلي للملف."
                }
            }
            MediaRoute.ANYVIEW -> {
                when {
                    dlnaResult?.success == true -> dlnaResult.message
                    dlnaResult != null -> "تم تجهيز الرابط المحلي، لكن AnyView لم يقبل أمر DLNA: ${dlnaResult.message}"
                    localServerResult != null -> "تم تجهيز رابط محلي للملف. جهاز AnyView يحتاج بروتوكولًا متوافقًا فعليًا للإرسال."
                    else -> "تم اختيار مسار AnyView، لكن لم يتم تجهيز الرابط المحلي للملف."
                }
            }
            MediaRoute.BASIC_HTTP -> {
                if (localServerResult != null) {
                    "تم تجهيز رابط محلي للملف. لم يتم العثور على بروتوكول تشغيل مباشر لهذا الجهاز."
                } else {
                    "الجهاز يملك مسار HTTP مبدئي، لكن لم يتم تجهيز الرابط المحلي للملف."
                }
            }
            MediaRoute.UNKNOWN -> "لم يتم تحديد بروتوكول مناسب لهذا الجهاز بعد."
        }

        val result = MediaSendResult(
            deviceIp = device.ipAddress,
            mediaUri = mediaUri.toString(),
            mimeType = mimeType,
            mediaKind = mediaKind,
            route = route,
            localUrl = localServerResult?.url,
            dlnaAttempted = dlnaResult != null,
            dlnaSuccess = dlnaResult?.success == true,
            dlnaHttpCode = dlnaResult?.httpCode,
            dlnaResponsePreview = dlnaResult?.responseBody.orEmpty().take(300),
            isReadyForNextStep = localServerResult != null,
            arabicMessage = message
        )
        lastResult = result
        result
    }

    suspend fun pause(device: CastDevice): String = dlnaController.pause(device).message

    suspend fun resume(device: CastDevice): String = dlnaController.resume(device).message

    suspend fun stop(device: CastDevice): String = dlnaController.stop(device).message

    suspend fun setVolume(device: CastDevice, volume: Int): String {
        return dlnaController.setVolume(device, volume).message
    }

    fun stopLocalServer() {
        localMediaServer.stop()
    }

    fun diagnosticsSummary(): String {
        val server = localMediaServer.diagnosticsSnapshot()
        val result = lastResult
        return buildString {
            append("تشخيص الإرسال\n")
            if (result == null) {
                append("لا توجد محاولة إرسال بعد.\n")
            } else {
                append("المسار: ${result.route.arabicName}\n")
                append("DLNA: ${if (result.dlnaAttempted) "تمت المحاولة" else "لم تتم"}\n")
                append("نجاح DLNA: ${if (result.dlnaSuccess) "نعم" else "لا"}\n")
                if (result.dlnaHttpCode != null) append("HTTP DLNA: ${result.dlnaHttpCode}\n")
                if (result.localUrl != null) append("الرابط المحلي: ${result.localUrl}\n")
            }
            append("\n")
            append(server.arabicSummary)
        }
    }

    private fun chooseRoute(device: CastDevice): MediaRoute {
        val servicesText = device.services.joinToString(" ").lowercase()
        return when {
            device.type == DeviceType.CHROMECAST || "chromecast" in servicesText || "dial" in servicesText -> MediaRoute.CHROMECAST
            device.type == DeviceType.DLNA || device.type == DeviceType.UPNP || device.supportsDlna || "mediarenderer" in servicesText || "dlna" in servicesText -> MediaRoute.DLNA
            device.type == DeviceType.ANYVIEW || "anyview" in servicesText || "hisense" in servicesText -> MediaRoute.ANYVIEW
            device.services.any { it.contains("80") || it.contains("8080") || it.contains("49152") } -> MediaRoute.BASIC_HTTP
            else -> MediaRoute.UNKNOWN
        }
    }
}

enum class MediaKind(val arabicName: String) {
    IMAGE("صورة"),
    VIDEO("فيديو"),
    UNKNOWN("ملف غير معروف")
}

enum class MediaRoute(val arabicName: String) {
    CHROMECAST("Chromecast"),
    DLNA("DLNA / UPnP"),
    ANYVIEW("AnyView"),
    BASIC_HTTP("HTTP مبدئي"),
    UNKNOWN("غير معروف")
}

data class MediaSendResult(
    val deviceIp: String,
    val mediaUri: String,
    val mimeType: String,
    val mediaKind: MediaKind,
    val route: MediaRoute,
    val localUrl: String?,
    val dlnaAttempted: Boolean,
    val dlnaSuccess: Boolean,
    val dlnaHttpCode: Int?,
    val dlnaResponsePreview: String,
    val isReadyForNextStep: Boolean,
    val arabicMessage: String
) {
    val arabicSummary: String
        get() = buildString {
            append("الملف: ${mediaKind.arabicName}\n")
            append("النوع: $mimeType\n")
            append("الجهاز: $deviceIp\n")
            append("المسار المختار: ${route.arabicName}\n")
            if (!localUrl.isNullOrBlank()) append("الرابط المحلي: $localUrl\n")
            if (dlnaAttempted) append("محاولة DLNA: ${if (dlnaSuccess) "نجحت" else "لم تكتمل"}\n")
            if (dlnaHttpCode != null) append("HTTP DLNA: $dlnaHttpCode\n")
            append(arabicMessage)
        }
}
