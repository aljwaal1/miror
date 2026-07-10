package com.explapp.mirror.core

import android.content.Context
import android.net.Uri
import com.explapp.mirror.model.CastDevice
import com.explapp.mirror.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaSender(private val context: Context) {
    suspend fun prepareSend(device: CastDevice, mediaUri: Uri): MediaSendResult = withContext(Dispatchers.IO) {
        val mimeType = context.contentResolver.getType(mediaUri).orEmpty()
        val mediaKind = when {
            mimeType.startsWith("image/") -> MediaKind.IMAGE
            mimeType.startsWith("video/") -> MediaKind.VIDEO
            else -> MediaKind.UNKNOWN
        }

        val route = chooseRoute(device)
        val message = when (route) {
            MediaRoute.CHROMECAST -> {
                "تم اختيار مسار Chromecast. الخطوة التالية تحتاج إضافة Google Cast SDK لإرسال الرابط إلى الجهاز."
            }
            MediaRoute.DLNA -> {
                "تم اختيار مسار DLNA / UPnP. الخطوة التالية هي إنشاء خادم محلي مؤقت داخل الهاتف حتى يقرأ التلفاز الملف."
            }
            MediaRoute.ANYVIEW -> {
                "تم اختيار مسار AnyView. بعض الشاشات تدعم استقبال الوسائط عبر DLNA، وبعضها يحتاج Miracast خاص."
            }
            MediaRoute.BASIC_HTTP -> {
                "الجهاز يملك منفذ HTTP مفتوح. يمكن تجربة خادم محلي مؤقت في المرحلة التالية."
            }
            MediaRoute.UNKNOWN -> {
                "لم يتم تحديد بروتوكول مناسب لهذا الجهاز بعد. جرّب جهازًا يظهر كـ DLNA أو Chromecast."
            }
        }

        MediaSendResult(
            deviceIp = device.ipAddress,
            mediaUri = mediaUri.toString(),
            mimeType = mimeType.ifBlank { "غير معروف" },
            mediaKind = mediaKind,
            route = route,
            isReadyForNextStep = route != MediaRoute.UNKNOWN,
            arabicMessage = message
        )
    }

    private fun chooseRoute(device: CastDevice): MediaRoute {
        val servicesText = device.services.joinToString(" ").lowercase()
        return when {
            device.type == DeviceType.CHROMECAST || "chromecast" in servicesText || "dial" in servicesText -> MediaRoute.CHROMECAST
            device.type == DeviceType.DLNA || device.type == DeviceType.UPNP || "mediarenderer" in servicesText || "dlna" in servicesText -> MediaRoute.DLNA
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
    val isReadyForNextStep: Boolean,
    val arabicMessage: String
) {
    val arabicSummary: String
        get() = buildString {
            append("الملف: ${mediaKind.arabicName}\n")
            append("النوع: $mimeType\n")
            append("الجهاز: $deviceIp\n")
            append("المسار المختار: ${route.arabicName}\n")
            append(arabicMessage)
        }
}
