package com.explapp.mirror.core

import android.content.Context
import android.net.Uri
import com.explapp.mirror.compatibility.CompatibilityEngine
import com.explapp.mirror.database.KnownDevicesStore
import com.explapp.mirror.model.CastDevice
import com.explapp.mirror.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaSender(private val context: Context) {
    private val localMediaServer = LocalMediaServer(context.applicationContext)
    private val dlnaController = DlnaController()
    private val compatibilityEngine = CompatibilityEngine()
    private val knownDevicesStore = KnownDevicesStore(context.applicationContext)
    private var lastResult: MediaSendResult? = null

    suspend fun prepareSend(device: CastDevice, mediaUri: Uri): MediaSendResult = withContext(Dispatchers.IO) {
        val rawMimeType = context.contentResolver.getType(mediaUri).orEmpty()
        val mimeType = rawMimeType.ifBlank { "application/octet-stream" }
        val mediaKind = mediaKindFromMime(mimeType)

        val savedProfileId = knownDevicesStore.getSavedProfileId(device)
        val profile = compatibilityEngine.selectProfile(device, savedProfileId)
        val route = chooseRoute(device)
        val localServerResult = if (route != MediaRoute.UNKNOWN) {
            runCatching { localMediaServer.start(mediaUri, mimeType) }.getOrNull()
        } else null

        val dlnaResult = if (localServerResult != null && (route == MediaRoute.DLNA || route == MediaRoute.ANYVIEW)) {
            dlnaController.play(device, localServerResult.url, mimeType)
        } else null

        recordCompatibility(device, profile.id, dlnaResult)

        val message = when (route) {
            MediaRoute.CHROMECAST -> if (localServerResult != null) {
                "تم تجهيز رابط محلي. Chromecast يحتاج Google Cast SDK للتشغيل المباشر."
            } else "تعذر تجهيز الرابط المحلي لـ Chromecast."

            MediaRoute.DLNA -> when {
                dlnaResult?.success == true -> "${dlnaResult.message}\nتم حفظ وضع التوافق: ${profile.displayName}"
                dlnaResult != null -> "فشل تشغيل DLNA: ${dlnaResult.message}"
                localServerResult != null -> "تم تجهيز الرابط، لكن لا يوجد رابط تحكم DLNA صالح."
                else -> "تعذر تجهيز الملف لمسار DLNA."
            }

            MediaRoute.ANYVIEW -> when {
                dlnaResult?.success == true -> "${dlnaResult.message}\nتم حفظ وضع التوافق: ${profile.displayName}"
                dlnaResult != null -> "AnyView لم يقبل أمر DLNA: ${dlnaResult.message}"
                localServerResult != null -> "تم تجهيز الرابط. استخدم زر مرآة الشاشة إذا لم يدعم الجهاز DLNA."
                else -> "تعذر تجهيز الملف لمسار AnyView."
            }

            MediaRoute.BASIC_HTTP -> if (localServerResult != null) {
                "تم تجهيز رابط محلي، لكن لم يتم العثور على بروتوكول تشغيل مباشر."
            } else "تعذر تجهيز الرابط المحلي."

            MediaRoute.UNKNOWN -> "لم يتم تحديد بروتوكول مناسب لهذا الجهاز."
        }

        MediaSendResult(
            deviceIp = device.ipAddress,
            mediaUri = mediaUri.toString(),
            mimeType = mimeType,
            mediaKind = mediaKind,
            route = route,
            profileId = profile.id,
            profileName = profile.displayName,
            localUrl = localServerResult?.url,
            dlnaAttempted = dlnaResult != null,
            dlnaSuccess = dlnaResult?.success == true,
            dlnaHttpCode = dlnaResult?.httpCode,
            dlnaResponsePreview = dlnaResult?.responseBody.orEmpty().take(300),
            isReadyForNextStep = localServerResult != null,
            arabicMessage = message
        ).also { lastResult = it }
    }

    suspend fun prepareSendUrl(device: CastDevice, rawUrl: String): MediaSendResult = withContext(Dispatchers.IO) {
        val url = rawUrl.trim()
        require(url.startsWith("http://") || url.startsWith("https://")) {
            "الرابط يجب أن يبدأ بـ http:// أو https://"
        }

        val mimeType = inferMimeType(url)
        val mediaKind = mediaKindFromMime(mimeType)
        val savedProfileId = knownDevicesStore.getSavedProfileId(device)
        val profile = compatibilityEngine.selectProfile(device, savedProfileId)
        val route = chooseRoute(device)

        val dlnaResult = if (route == MediaRoute.DLNA || route == MediaRoute.ANYVIEW) {
            dlnaController.play(device, url, mimeType)
        } else null

        recordCompatibility(device, profile.id, dlnaResult)

        val message = when (route) {
            MediaRoute.DLNA, MediaRoute.ANYVIEW -> when {
                dlnaResult?.success == true -> "${dlnaResult.message}\nتم إرسال الرابط المباشر إلى الجهاز."
                dlnaResult != null -> "لم يقبل الجهاز الرابط المباشر: ${dlnaResult.message}"
                else -> "تعذر بدء إرسال الرابط المباشر."
            }
            MediaRoute.CHROMECAST -> "الرابط صحيح، لكن Chromecast يحتاج Google Cast SDK."
            MediaRoute.BASIC_HTTP -> "الجهاز ظاهر على الشبكة، لكنه لا يعلن عن تحكم DLNA لتشغيل الرابط."
            MediaRoute.UNKNOWN -> "لم يتم تحديد بروتوكول مناسب لهذا الجهاز."
        }

        MediaSendResult(
            deviceIp = device.ipAddress,
            mediaUri = url,
            mimeType = mimeType,
            mediaKind = mediaKind,
            route = route,
            profileId = profile.id,
            profileName = profile.displayName,
            localUrl = url,
            dlnaAttempted = dlnaResult != null,
            dlnaSuccess = dlnaResult?.success == true,
            dlnaHttpCode = dlnaResult?.httpCode,
            dlnaResponsePreview = dlnaResult?.responseBody.orEmpty().take(300),
            isReadyForNextStep = dlnaResult?.success == true,
            arabicMessage = message
        ).also { lastResult = it }
    }

    suspend fun pause(device: CastDevice): String = dlnaController.pause(device).message
    suspend fun resume(device: CastDevice): String = dlnaController.resume(device).message
    suspend fun stop(device: CastDevice): String = dlnaController.stop(device).message
    suspend fun setVolume(device: CastDevice, volume: Int): String = dlnaController.setVolume(device, volume).message

    fun compatibilitySummary(device: CastDevice): String {
        val profile = compatibilityEngine.selectProfile(device, knownDevicesStore.getSavedProfileId(device))
        return compatibilityEngine.explain(device, profile) + "\n" + knownDevicesStore.summary(device)
    }

    fun resetCompatibility(device: CastDevice, clearHistory: Boolean = false): String {
        if (clearHistory) knownDevicesStore.clearDeviceHistory(device) else knownDevicesStore.resetProfile(device)
        return if (clearHistory) {
            "تم مسح وضع التوافق وسجل الجهاز بالكامل."
        } else {
            "تمت إعادة ضبط وضع التوافق. سيختار التطبيق وضعًا جديدًا في المحاولة القادمة."
        }
    }

    fun stopLocalServer() = localMediaServer.stop()

    fun diagnosticsSummary(): String {
        val server = localMediaServer.diagnosticsSnapshot()
        val result = lastResult
        return buildString {
            append("تشخيص الإرسال\n")
            if (result == null) {
                append("لا توجد محاولة إرسال بعد.\n")
            } else {
                append("المسار: ${result.route.arabicName}\n")
                append("وضع التوافق: ${result.profileName} (${result.profileId})\n")
                append("DLNA: ${if (result.dlnaAttempted) "تمت المحاولة" else "لم تتم"}\n")
                append("نجاح DLNA: ${if (result.dlnaSuccess) "نعم" else "لا"}\n")
                result.dlnaHttpCode?.let { append("HTTP DLNA: $it\n") }
                result.localUrl?.let { append("الرابط: $it\n") }
            }
            append("\n")
            append(server.arabicSummary)
        }
    }

    private fun recordCompatibility(device: CastDevice, profileId: String, dlnaResult: DlnaControlResult?) {
        if (dlnaResult?.success == true) {
            knownDevicesStore.recordSuccess(device, profileId)
        } else if (dlnaResult != null) {
            knownDevicesStore.recordFailure(device, dlnaResult.message)
        }
    }

    private fun mediaKindFromMime(mimeType: String): MediaKind = when {
        mimeType.startsWith("image/") -> MediaKind.IMAGE
        mimeType.startsWith("video/") || mimeType.contains("mpegurl") || mimeType.contains("dash") -> MediaKind.VIDEO
        mimeType.startsWith("audio/") -> MediaKind.AUDIO
        else -> MediaKind.UNKNOWN
    }

    private fun inferMimeType(url: String): String {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            path.endsWith(".m3u8") -> "application/vnd.apple.mpegurl"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".mkv") -> "video/x-matroska"
            path.endsWith(".avi") -> "video/x-msvideo"
            path.endsWith(".mp3") -> "audio/mpeg"
            path.endsWith(".aac") -> "audio/aac"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".wav") -> "audio/wav"
            path.endsWith(".flac") -> "audio/flac"
            path.endsWith(".jpg") || path.endsWith(".jpeg") -> "image/jpeg"
            path.endsWith(".png") -> "image/png"
            path.endsWith(".webp") -> "image/webp"
            else -> "video/mp4"
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
    IMAGE("صورة"), VIDEO("فيديو"), AUDIO("صوت"), UNKNOWN("ملف غير معروف")
}

enum class MediaRoute(val arabicName: String) {
    CHROMECAST("Chromecast"), DLNA("DLNA / UPnP"), ANYVIEW("AnyView"), BASIC_HTTP("HTTP مبدئي"), UNKNOWN("غير معروف")
}

data class MediaSendResult(
    val deviceIp: String,
    val mediaUri: String,
    val mimeType: String,
    val mediaKind: MediaKind,
    val route: MediaRoute,
    val profileId: String,
    val profileName: String,
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
            append("الوسيط: ${mediaKind.arabicName}\n")
            append("النوع: $mimeType\n")
            append("الجهاز: $deviceIp\n")
            append("المسار: ${route.arabicName}\n")
            append("وضع التوافق: $profileName\n")
            localUrl?.let { append("الرابط: $it\n") }
            if (dlnaAttempted) append("محاولة DLNA: ${if (dlnaSuccess) "نجحت" else "لم تكتمل"}\n")
            dlnaHttpCode?.let { append("HTTP DLNA: $it\n") }
            append(arabicMessage)
        }
}
