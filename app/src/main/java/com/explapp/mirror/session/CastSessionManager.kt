package com.explapp.mirror.session

import com.explapp.mirror.model.CastDevice

enum class PlaybackState {
    IDLE, CONNECTING, PLAYING, PAUSED, STOPPED, ERROR
}

object CastSessionManager {
    var device: CastDevice? = null
        private set
    var mediaTitle: String = ""
        private set
    var mediaSource: String = ""
        private set
    var state: PlaybackState = PlaybackState.IDLE
        private set
    var volume: Int = 30
        private set
    var lastMessage: String = ""
        private set

    fun selectDevice(value: CastDevice) {
        device = value
        if (state == PlaybackState.ERROR || state == PlaybackState.STOPPED) {
            state = PlaybackState.IDLE
            lastMessage = ""
        }
    }

    fun begin(mediaTitle: String, mediaSource: String) {
        this.mediaTitle = mediaTitle
        this.mediaSource = mediaSource
        state = PlaybackState.CONNECTING
        lastMessage = "جاري بدء البث"
    }

    fun updateState(value: PlaybackState, message: String = "") {
        state = value
        if (message.isNotBlank()) lastMessage = message
    }

    fun updateVolume(value: Int) {
        volume = value.coerceIn(0, 100)
    }

    fun clearPlayback(message: String = "تم إيقاف البث") {
        mediaTitle = ""
        mediaSource = ""
        state = PlaybackState.STOPPED
        lastMessage = message
    }

    fun clearAll() {
        device = null
        mediaTitle = ""
        mediaSource = ""
        state = PlaybackState.IDLE
        lastMessage = ""
        volume = 30
    }
}
