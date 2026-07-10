package com.explapp.mirror.media

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import com.explapp.mirror.model.CastDevice
import com.explapp.mirror.model.DeviceType

class MediaSender(context: Context) {
    private val appContext = context.applicationContext
    private val server = LocalMediaServer(appContext)
    private val dlna = DlnaController()

    suspend fun send(device: CastDevice, uri: Uri): Result<String> {
        val mime = appContext.contentResolver.getType(uri) ?: "application/octet-stream"
        server.setMedia(uri)
        if (!server.start()) return Result.failure(IllegalStateException("تعذر تشغيل الخادم المحلي"))
        val localIp = localIpAddress() ?: return Result.failure(IllegalStateException("تعذر معرفة IP الهاتف"))
        val url = server.mediaUrl(localIp)

        return when (device.type) {
            DeviceType.DLNA, DeviceType.UPNP, DeviceType.ANYVIEW, DeviceType.TV, DeviceType.UNKNOWN -> {
                dlna.play(device, url, mime).map { "تم إرسال الملف إلى ${device.name}" }
            }
            DeviceType.CHROMECAST -> Result.failure(IllegalStateException("دعم Chromecast قيد الإضافة"))
            else -> Result.failure(IllegalStateException("هذا النوع لا يعلن عن بروتوكول إرسال مدعوم بعد"))
        }
    }

    suspend fun stop(device: CastDevice): Result<String> {
        return dlna.stop(device).map { "تم إيقاف التشغيل" }
    }

    fun close() {
        server.stop()
    }

    private fun localIpAddress(): String? {
        val wifi = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        val value = wifi.connectionInfo?.ipAddress ?: return null
        if (value == 0) return null
        return listOf(
            value and 0xff,
            value shr 8 and 0xff,
            value shr 16 and 0xff,
            value shr 24 and 0xff
        ).joinToString(".")
    }
}
