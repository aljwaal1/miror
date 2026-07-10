package com.explapp.mirror.model

data class CastDevice(
    val name: String,
    val ipAddress: String,
    val type: DeviceType = DeviceType.UNKNOWN,
    val manufacturer: String = "",
    val modelName: String = "",
    val modelNumber: String = "",
    val uuid: String = "",
    val locationUrl: String? = null,
    val avTransportControlUrl: String? = null,
    val renderingControlUrl: String? = null,
    val services: List<String> = emptyList(),
    val isReachable: Boolean = false
) {
    val displayType: String
        get() = type.arabicName

    val supportsDlna: Boolean
        get() = avTransportControlUrl != null || type == DeviceType.DLNA || type == DeviceType.UPNP

    val supportsVolumeControl: Boolean
        get() = renderingControlUrl != null

    val detailsSummary: String
        get() = buildString {
            append(name)
            append("\nIP: $ipAddress")
            append("\nالنوع: $displayType")
            if (manufacturer.isNotBlank()) append("\nالشركة: $manufacturer")
            if (modelName.isNotBlank()) append("\nالموديل: $modelName")
            if (modelNumber.isNotBlank()) append(" ($modelNumber)")
            if (uuid.isNotBlank()) append("\nUUID: $uuid")
            append("\nDLNA: ${if (supportsDlna) "مدعوم" else "غير مؤكد"}")
            append("\nالتحكم بالصوت: ${if (supportsVolumeControl) "مدعوم" else "غير متوفر"}")
        }
}
