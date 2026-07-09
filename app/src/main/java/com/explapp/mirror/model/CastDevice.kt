package com.explapp.mirror.model

data class CastDevice(
    val name: String,
    val ipAddress: String,
    val type: DeviceType = DeviceType.UNKNOWN,
    val manufacturer: String = "",
    val services: List<String> = emptyList(),
    val isReachable: Boolean = false
) {
    val displayType: String
        get() = type.arabicName
}
