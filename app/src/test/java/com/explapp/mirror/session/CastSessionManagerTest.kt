package com.explapp.mirror.session

import com.explapp.mirror.model.CastDevice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CastSessionManagerTest {
    private val device = CastDevice(
        name = "Living Room TV",
        ipAddress = "192.168.1.20",
        avTransportControlUrl = "http://192.168.1.20/avtransport",
        renderingControlUrl = "http://192.168.1.20/rendering"
    )

    @Before
    fun setUp() {
        CastSessionManager.clearAll()
    }

    @After
    fun tearDown() {
        CastSessionManager.clearAll()
    }

    @Test
    fun selectingDeviceStoresDeviceAndKeepsIdleState() {
        CastSessionManager.selectDevice(device)

        assertEquals(device, CastSessionManager.device)
        assertEquals(PlaybackState.IDLE, CastSessionManager.state)
    }

    @Test
    fun beginCreatesConnectingPlaybackSession() {
        CastSessionManager.selectDevice(device)
        CastSessionManager.begin("Demo video", "https://example.com/video.mp4")

        assertEquals("Demo video", CastSessionManager.mediaTitle)
        assertEquals("https://example.com/video.mp4", CastSessionManager.mediaSource)
        assertEquals(PlaybackState.CONNECTING, CastSessionManager.state)
        assertEquals("جاري بدء البث", CastSessionManager.lastMessage)
    }

    @Test
    fun volumeIsClampedToSupportedRange() {
        CastSessionManager.updateVolume(-10)
        assertEquals(0, CastSessionManager.volume)

        CastSessionManager.updateVolume(150)
        assertEquals(100, CastSessionManager.volume)
    }

    @Test
    fun clearPlaybackKeepsSelectedDeviceAndClearsMedia() {
        CastSessionManager.selectDevice(device)
        CastSessionManager.begin("Demo", "local://demo")
        CastSessionManager.updateState(PlaybackState.PLAYING, "قيد التشغيل")

        CastSessionManager.clearPlayback()

        assertEquals(device, CastSessionManager.device)
        assertEquals("", CastSessionManager.mediaTitle)
        assertEquals("", CastSessionManager.mediaSource)
        assertEquals(PlaybackState.STOPPED, CastSessionManager.state)
    }

    @Test
    fun clearAllRestoresInitialSessionValues() {
        CastSessionManager.selectDevice(device)
        CastSessionManager.begin("Demo", "local://demo")
        CastSessionManager.updateVolume(80)
        CastSessionManager.updateState(PlaybackState.ERROR, "فشل الاتصال")

        CastSessionManager.clearAll()

        assertNull(CastSessionManager.device)
        assertEquals("", CastSessionManager.mediaTitle)
        assertEquals("", CastSessionManager.mediaSource)
        assertEquals(PlaybackState.IDLE, CastSessionManager.state)
        assertEquals("", CastSessionManager.lastMessage)
        assertEquals(30, CastSessionManager.volume)
    }
}
