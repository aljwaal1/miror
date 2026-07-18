package com.explapp.mirror.session

import java.net.URI

/**
 * Validates and normalizes direct media URLs before a cast session is mutated.
 */
object DirectMediaUrl {
    private const val MAX_PORT = 65_535

    fun normalize(rawUrl: String): String {
        val normalized = rawUrl.trim()
        val uri = runCatching { URI(normalized) }.getOrNull()
        val supportedScheme = uri?.scheme.equals("http", ignoreCase = true) ||
            uri?.scheme.equals("https", ignoreCase = true)
        val validPort = uri?.port == -1 || (uri?.port ?: -1) in 1..MAX_PORT

        require(
            uri != null &&
                uri.isAbsolute &&
                supportedScheme &&
                !uri.host.isNullOrBlank() &&
                uri.userInfo.isNullOrBlank() &&
                validPort
        ) {
            "أدخل رابط HTTP أو HTTPS صحيحًا دون بيانات دخول وبمنفذ صالح"
        }

        return normalized
    }
}
