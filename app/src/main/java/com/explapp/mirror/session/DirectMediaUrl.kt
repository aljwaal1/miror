package com.explapp.mirror.session

import java.net.URI

/**
 * Validates and normalizes direct media URLs before a cast session is mutated.
 */
object DirectMediaUrl {
    fun normalize(rawUrl: String): String {
        val normalized = rawUrl.trim()
        val uri = runCatching { URI(normalized) }.getOrNull()

        require(uri != null &&
            (uri.scheme.equals("http", ignoreCase = true) || uri.scheme.equals("https", ignoreCase = true)) &&
            !uri.host.isNullOrBlank()
        ) {
            "أدخل رابطًا صحيحًا يبدأ بـ http:// أو https:// ويحتوي اسم موقع"
        }

        return normalized
    }
}
