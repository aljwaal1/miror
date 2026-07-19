package com.explapp.mirror.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DirectMediaUrlTest {
    @Test
    fun trimsAndAcceptsValidHttpUrl() {
        assertEquals(
            "https://example.com/video.mp4",
            DirectMediaUrl.normalize("  https://example.com/video.mp4  ")
        )
    }

    @Test
    fun acceptsUppercaseScheme() {
        assertEquals(
            "HTTPS://example.com/video.mp4",
            DirectMediaUrl.normalize("HTTPS://example.com/video.mp4")
        )
    }

    @Test
    fun acceptsValidCustomPort() {
        assertEquals(
            "http://192.168.1.20:8080/video.mp4",
            DirectMediaUrl.normalize("http://192.168.1.20:8080/video.mp4")
        )
    }

    @Test
    fun rejectsMissingHost() {
        assertThrows(IllegalArgumentException::class.java) {
            DirectMediaUrl.normalize("https://")
        }
    }

    @Test
    fun rejectsUnsupportedScheme() {
        assertThrows(IllegalArgumentException::class.java) {
            DirectMediaUrl.normalize("ftp://example.com/video.mp4")
        }
    }

    @Test
    fun rejectsPlainText() {
        assertThrows(IllegalArgumentException::class.java) {
            DirectMediaUrl.normalize("example.com/video.mp4")
        }
    }

    @Test
    fun rejectsEmbeddedUserInfo() {
        assertThrows(IllegalArgumentException::class.java) {
            DirectMediaUrl.normalize("https://viewer@example.com/video.mp4")
        }
    }

    @Test
    fun rejectsOutOfRangePort() {
        assertThrows(IllegalArgumentException::class.java) {
            DirectMediaUrl.normalize("https://example.com:70000/video.mp4")
        }
    }

    @Test
    fun rejectsZeroPort() {
        assertThrows(IllegalArgumentException::class.java) {
            DirectMediaUrl.normalize("https://example.com:0/video.mp4")
        }
    }
}
