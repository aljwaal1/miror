package com.explapp.mirror.database

import android.content.Context
import com.explapp.mirror.model.CastDevice

class KnownDevicesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "known_devices",
        Context.MODE_PRIVATE
    )

    fun getSavedProfileId(device: CastDevice): String? {
        return prefs.getString(profileKey(device), null)
    }

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

    fun summary(device: CastDevice): String {
        val key = deviceKey(device)
        val profile = prefs.getString(profileKey(device), null) ?: "غير محفوظ"
        val success = prefs.getInt("${key}_success", 0)
        val failure = prefs.getInt("${key}_failure", 0)
        val error = prefs.getString("${key}_last_error", "").orEmpty()
        return buildString {
            append("الوضع المحفوظ: $profile\n")
            append("مرات النجاح: $success\n")
            append("مرات الفشل: $failure")
            if (error.isNotBlank()) append("\nآخر خطأ: $error")
        }
    }

    private fun profileKey(device: CastDevice): String = "${deviceKey(device)}_profile"

    private fun deviceKey(device: CastDevice): String {
        val raw = device.uuid.ifBlank { device.ipAddress }
        return raw.replace(Regex("[^A-Za-z0-9_.-]"), "_")
    }
}
