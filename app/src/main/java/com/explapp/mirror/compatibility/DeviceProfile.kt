package com.explapp.mirror.compatibility

enum class MetadataMode {
    FULL,
    SIMPLE,
    EMPTY
}

data class DeviceProfile(
    val id: String,
    val displayName: String,
    val stopBeforePlay: Boolean,
    val delayAfterStopMs: Long,
    val delayAfterSetUriMs: Long,
    val metadataOrder: List<MetadataMode>,
    val preferredMimeFallbacks: List<String> = emptyList()
)

object DeviceProfiles {
    val GENERIC = DeviceProfile(
        id = "generic",
        displayName = "وضع عام",
        stopBeforePlay = false,
        delayAfterStopMs = 0,
        delayAfterSetUriMs = 250,
        metadataOrder = listOf(MetadataMode.FULL, MetadataMode.SIMPLE, MetadataMode.EMPTY)
    )

    val GHAZAL = DeviceProfile(
        id = "ghazal",
        displayName = "غزال / Forever",
        stopBeforePlay = true,
        delayAfterStopMs = 350,
        delayAfterSetUriMs = 700,
        metadataOrder = listOf(MetadataMode.SIMPLE, MetadataMode.EMPTY, MetadataMode.FULL),
        preferredMimeFallbacks = listOf("image/jpeg", "video/mp4")
    )

    val GGUARD = DeviceProfile(
        id = "gguard",
        displayName = "G-Guard / AnyView",
        stopBeforePlay = true,
        delayAfterStopMs = 250,
        delayAfterSetUriMs = 500,
        metadataOrder = listOf(MetadataMode.EMPTY, MetadataMode.SIMPLE, MetadataMode.FULL)
    )

    val SAMSUNG = DeviceProfile(
        id = "samsung",
        displayName = "Samsung",
        stopBeforePlay = false,
        delayAfterStopMs = 0,
        delayAfterSetUriMs = 350,
        metadataOrder = listOf(MetadataMode.FULL, MetadataMode.SIMPLE, MetadataMode.EMPTY)
    )

    val LG = DeviceProfile(
        id = "lg",
        displayName = "LG",
        stopBeforePlay = false,
        delayAfterStopMs = 0,
        delayAfterSetUriMs = 350,
        metadataOrder = listOf(MetadataMode.FULL, MetadataMode.EMPTY, MetadataMode.SIMPLE)
    )

    val ALL = listOf(GHAZAL, GGUARD, SAMSUNG, LG, GENERIC)
}
