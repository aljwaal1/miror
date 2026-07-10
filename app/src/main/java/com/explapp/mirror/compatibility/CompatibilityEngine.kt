package com.explapp.mirror.compatibility

import com.explapp.mirror.model.CastDevice
import com.explapp.mirror.model.DeviceType

class CompatibilityEngine {
    fun selectProfile(device: CastDevice, savedProfileId: String? = null): DeviceProfile {
        if (!savedProfileId.isNullOrBlank()) {
            DeviceProfiles.ALL.firstOrNull { it.id == savedProfileId }?.let { return it }
        }

        val text = buildString {
            append(device.name)
            append(' ')
            append(device.manufacturer)
            append(' ')
            append(device.modelName)
            append(' ')
            append(device.modelNumber)
            append(' ')
            append(device.services.joinToString(" "))
        }.lowercase()

        return when {
            listOf("ghazal", "غزال", "forever", "901 turbo").any { it in text } -> DeviceProfiles.GHAZAL
            listOf("g-guard", "gguard", "anyview", "hisense").any { it in text } || device.type == DeviceType.ANYVIEW -> DeviceProfiles.GGUARD
            "samsung" in text -> DeviceProfiles.SAMSUNG
            "lg" in text || "webos" in text -> DeviceProfiles.LG
            else -> DeviceProfiles.GENERIC
        }
    }

    fun explain(device: CastDevice, profile: DeviceProfile): String = buildString {
        append("الجهاز: ${device.name}\n")
        append("الوضع المختار: ${profile.displayName}\n")
        append("Stop قبل التشغيل: ${if (profile.stopBeforePlay) "نعم" else "لا"}\n")
        append("ترتيب Metadata: ${profile.metadataOrder.joinToString()}")
    }
}
