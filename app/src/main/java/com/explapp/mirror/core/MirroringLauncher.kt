package com.explapp.mirror.core

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings

class MirroringLauncher(private val activity: Activity) {

    fun openBestAvailable(): MirroringLaunchResult {
        return openFirstAvailable(
            candidates = mirroringCandidates(),
            successPrefix = "تم فتح إعدادات مرآة الشاشة"
        )
    }

    fun openWifiDirect(): MirroringLaunchResult {
        return openFirstAvailable(
            candidates = wifiDirectCandidates(),
            successPrefix = "تم فتح إعدادات Wi‑Fi Direct"
        )
    }

    fun isMirroringSettingsAvailable(): Boolean {
        return mirroringCandidates().any { it.intent.isAvailable() }
    }

    fun availabilitySummary(): String {
        val mirroring = mirroringCandidates().filter { it.intent.isAvailable() }.map { it.label }
        val wifiDirect = wifiDirectCandidates().filter { it.intent.isAvailable() }.map { it.label }
        return buildString {
            append("مسار المرآة: ")
            append(if (mirroring.isEmpty()) "غير مباشر" else mirroring.joinToString(" / "))
            append("\nWi‑Fi Direct: ")
            append(if (wifiDirect.isEmpty()) "غير مباشر" else wifiDirect.joinToString(" / "))
            append("\nملاحظة: التطبيق يفتح صفحة النظام، والاختيار النهائي للشاشة يتم من الهاتف حفاظًا على أمان أندرويد.")
        }
    }

    private fun openFirstAvailable(
        candidates: List<MirrorIntentCandidate>,
        successPrefix: String
    ): MirroringLaunchResult {
        val candidate = candidates.firstOrNull { it.intent.isAvailable() }
        return if (candidate != null) {
            runCatching {
                activity.startActivity(candidate.intent)
                MirroringLaunchResult(
                    opened = true,
                    openedTarget = candidate.label,
                    message = "$successPrefix: ${candidate.label}. اختر شاشة AnyView / Miracast من القائمة إن ظهرت."
                )
            }.getOrElse {
                MirroringLaunchResult(false, candidate.label, "تعذر فتح ${candidate.label}: ${it.message.orEmpty()}")
            }
        } else {
            MirroringLaunchResult(
                opened = false,
                openedTarget = "",
                message = "الهاتف لا يوفر صفحة Miracast مباشرة يمكن فتحها من التطبيقات. استخدم زر DLNA للصور والفيديو، أو افتح المرآة من لوحة الاختصارات إن كانت موجودة."
            )
        }
    }

    private fun mirroringCandidates(): List<MirrorIntentCandidate> {
        return listOf(
            MirrorIntentCandidate("Wi‑Fi Display", Intent("android.settings.WIFI_DISPLAY_SETTINGS")),
            MirrorIntentCandidate("Cast", Intent("android.settings.CAST_SETTINGS")),
            MirrorIntentCandidate("Wireless Display", Intent("com.android.settings.WIFI_DISPLAY_SETTINGS")),
            MirrorIntentCandidate(
                "Settings WifiDisplay Activity",
                Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.Settings\$WifiDisplaySettingsActivity"))
            ),
            MirrorIntentCandidate("Wireless Settings", Intent(Settings.ACTION_WIRELESS_SETTINGS)),
            MirrorIntentCandidate("Wi‑Fi Settings", Intent(Settings.ACTION_WIFI_SETTINGS)),
            MirrorIntentCandidate("Main Settings", Intent(Settings.ACTION_SETTINGS))
        )
    }

    private fun wifiDirectCandidates(): List<MirrorIntentCandidate> {
        return listOf(
            MirrorIntentCandidate("Wi‑Fi Direct", Intent("android.settings.WIFI_DIRECT_SETTINGS")),
            MirrorIntentCandidate("Wi‑Fi Settings", Intent(Settings.ACTION_WIFI_SETTINGS)),
            MirrorIntentCandidate("Wireless Settings", Intent(Settings.ACTION_WIRELESS_SETTINGS)),
            MirrorIntentCandidate("Main Settings", Intent(Settings.ACTION_SETTINGS))
        )
    }

    private fun Intent.isAvailable(): Boolean {
        return resolveActivity(activity.packageManager) != null
    }
}

data class MirroringLaunchResult(
    val opened: Boolean,
    val openedTarget: String = "",
    val message: String
)

private data class MirrorIntentCandidate(
    val label: String,
    val intent: Intent
)
