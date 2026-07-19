package com.explapp.mirror.session

import android.net.Uri
import com.explapp.mirror.core.MediaSendResult
import com.explapp.mirror.core.MediaSender
import com.explapp.mirror.model.CastDevice

/**
 * Coordinates a cast request with the shared session state.
 *
 * UI screens should use this class instead of updating CastSessionManager
 * independently. This guarantees that local files, direct links, and media
 * discovered by the browser follow the same lifecycle.
 */
class SessionCastCoordinator(
    private val mediaSender: MediaSender
) {
    suspend fun castLocal(
        device: CastDevice,
        uri: Uri,
        title: String = uri.lastPathSegment.orEmpty().ifBlank { "وسائط محلية" }
    ): MediaSendResult {
        prepareSession(device, title, uri.toString())
        return runRequest { mediaSender.prepareSend(device, uri) }
    }

    suspend fun castUrl(
        device: CastDevice,
        url: String,
        title: String = titleFromUrl(url)
    ): MediaSendResult {
        val normalizedUrl = DirectMediaUrl.normalize(url)
        prepareSession(device, title, normalizedUrl)
        return runRequest { mediaSender.prepareSendUrl(device, normalizedUrl) }
    }

    private fun prepareSession(device: CastDevice, title: String, source: String) {
        CastSessionManager.selectDevice(device)
        CastSessionManager.begin(
            mediaTitle = title.ifBlank { "وسائط قيد التشغيل" },
            mediaSource = source
        )
    }

    private suspend fun runRequest(request: suspend () -> MediaSendResult): MediaSendResult {
        return try {
            request().also(::applyResult)
        } catch (error: Exception) {
            CastSessionManager.updateState(
                PlaybackState.ERROR,
                "تعذر بدء البث: ${error.message.orEmpty()}"
            )
            throw error
        }
    }

    private fun applyResult(result: MediaSendResult) {
        val successful = result.dlnaSuccess && result.isReadyForNextStep
        CastSessionManager.updateState(
            value = if (successful) PlaybackState.PLAYING else PlaybackState.ERROR,
            message = result.arabicMessage
        )
    }

    companion object {
        private fun titleFromUrl(url: String): String {
            val path = url.substringBefore('?').substringBefore('#')
            return path.substringAfterLast('/').ifBlank { "رابط وسائط مباشر" }
        }
    }
}
