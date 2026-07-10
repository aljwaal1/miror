package com.explapp.mirror.database

import android.content.Context
import com.explapp.mirror.model.CastDevice

class KnownDevicesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "known_devices",
        Context.MODE_PRIVATE
    )

    fun getSavedProfileId(device: CastDevice): String? = prefs.getString(profileKey(device), null)

    fun recordSuccess(device: CastDevice, profileId: String) {
        val key = deviceKey(device)
        val successes = prefs.getInt("${key}_success", 0) + 1
        prefs.edit()
            .putString(profileKey(device), profileId)
            .putLong("${key}_last_success", System.currentTimeMillis())
            .putInt("${key}_success", successes)
            .putString("${key}_last_error", "")
            .apply()
    }

    fun recordFailure(device: CastDevice, error: String) {
        val key = deviceKey(device)
        val failures = prefs.getInt("${key}_failure", 0) + 1
        prefs.edit()
            .putInt("${key}_failure", failures)
            .putString("${key}_last_error", error.take(500))
            .apply()
    }

    fun resetProfile(device: CastDevice) {
        prefs.edit()
            .remove(profileKey(device))
            .remove("${deviceKey(device)}_last_error")
            .apply()
    }

    fun clearDeviceHistory(device: CastDevice) {
        val key = deviceKey(device)
        prefs.edit()
            .remove(profileKey(device))
            .remove("${key}_last_success")
            .remove("${key}_success")
            .remove("${key}_failure")
            .remove("${key}_last_error")
            .apply()
    }

    fun stats(device: CastDevice): DeviceCompatibilityStats {
        val key = deviceKey(device)
        return DeviceCompatibilityStats(
            profileId = prefs.getString(profileKey(device), null),
            successes = prefs.getInt("${key}_success", 0),
            failures = prefs.getInt("${key}_failure", 0),
            lastSuccessTime = prefs.getLong("${key}_last_success", 0L),
            lastError = prefs.getString("${key}_last_error", "").orEmpty()
        )
    }

    fun summary(device: CastDevice): String {
        val stats = stats(device)
        return buildString {
            append("الوضع المحفوظ: ${stats.profileId ?: "غير محفوظ"}\n")
            append("مرات النجاح: ${stats.successes}\n")
            append("مرات الفشل: ${stats.failures}")
            if (stats.lastSuccessTime > 0) append("\nآخر نجاح: ${stats.lastSuccessTime}")
            if (stats.lastError.isNotBlank()) append("\nآخر خطأ: ${stats.lastError}")
        }
    }

    private fun profileKey(device: CastDevice): String = "${deviceKey(device)}_profile"

    private fun deviceKey(device: CastDevice): String {
        val raw = device.uuid.ifBlank { device.ipAddress }
        return raw.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    }
}

data class DeviceCompatibilityStats(
    val profileId: String?,
    val successes: Int,
    val failures: Int,
    val lastSuccessTime: Long,
    val lastError: String
)
