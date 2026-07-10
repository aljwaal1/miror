package com.explapp.mirror.core

import com.explapp.mirror.model.CastDevice

enum class CompatibilityProfile {
    STANDARD,
    RECEIVER_FRIENDLY,
    HISENSE_ANYVIEW,
    SAMSUNG,
    LG
}

object CompatibilityResolver {
    fun resolve(device: CastDevice): CompatibilityProfile {
        val text = listOf(
            device.name,
            device.manufacturer,
            device.modelName,
            device.modelNumber,
            device.services.joinToString(" ")
        ).joinToString(" ").lowercase()

        return when {
            "ghazal" in text || "غزال" in text || "forever" in text || "receiver" in text || "stb" in text -> CompatibilityProfile.RECEIVER_FRIENDLY
            "hisense" in text || "anyview" in text || "g-guard" in text || "gguard" in text -> CompatibilityProfile.HISENSE_ANYVIEW
            "samsung" in text -> CompatibilityProfile.SAMSUNG
            "lg" in text || "webos" in text -> CompatibilityProfile.LG
            else -> CompatibilityProfile.STANDARD
        }
    }
}
