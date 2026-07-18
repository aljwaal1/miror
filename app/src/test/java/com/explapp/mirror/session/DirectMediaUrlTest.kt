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
}
