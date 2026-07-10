package com.explapp.mirror.core

import android.app.Activity
import android.content.Intent
import android.provider.Settings

class MirroringLauncher(private val activity: Activity) {

    fun openBestAvailable(): MirroringLaunchResult {
        val candidates = listOf(
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent("android.settings.CAST_SETTINGS"),
            Intent(Settings.ACTION_WIRELESS_SETTINGS),
            Intent(Settings.ACTION_WIFI_SETTINGS)
        )

        val intent = candidates.firstOrNull { candidate ->
            candidate.resolveActivity(activity.packageManager) != null
        }

        return if (intent != null) {
            runCatching {
                activity.startActivity(intent)
                MirroringLaunchResult(
                    opened = true,
                    message = "تم فتح إعدادات مشاركة الشاشة. اختر شاشة AnyView / العرض اللاسلكي من قائمة الهاتف."
                )
            }.getOrElse {
                MirroringLaunchResult(false, "تعذر فتح إعدادات مشاركة الشاشة: ${it.message.orEmpty()}")
            }
        } else {
            MirroringLaunchResult(
                opened = false,
                message = "الهاتف لا يوفر صفحة مشاركة شاشة يمكن فتحها من التطبيقات. ما زال بإمكانك استخدام مسار DLNA للصور والفيديو."
            )
        }
    }

    fun isMirroringSettingsAvailable(): Boolean {
        return listOf(
            Intent("android.settings.WIFI_DISPLAY_SETTINGS"),
            Intent("android.settings.CAST_SETTINGS")
        ).any { it.resolveActivity(activity.packageManager) != null }
    }
}

data class MirroringLaunchResult(
    val opened: Boolean,
    val message: String
)
