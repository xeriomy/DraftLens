package com.draftlens.vision.model

import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.geometry.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VisionFrameTest {

    @Test fun `pixel array payload copies input and estimates bytes`() {
        val source = intArrayOf(1, 2, 3, 4)
        val payload = PixelArrayFramePayload(width = 2, height = 2, pixels = source)
        source[0] = 99
        assertEquals(1, payload.pixels()[0])
        assertEquals(16L, payload.byteCountEstimate)
    }

    @Test fun `pixel array payload rejects empty or mismatched input`() {
        assertThrows(IllegalArgumentException::class.java) {
            PixelArrayFramePayload(width = 0, height = 1, pixels = IntArray(0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelArrayFramePayload(width = 2, height = 2, pixels = IntArray(3))
        }
    }

    @Test fun `orientation category is explicit`() {
        assertEquals(OrientationCategory.PORTRAIT, OrientationCategory.from(720, 1280))
        assertEquals(OrientationCategory.LANDSCAPE, OrientationCategory.from(1280, 720))
        assertEquals(OrientationCategory.SQUARE, OrientationCategory.from(100, 100))
    }

    @Test fun `invalid normalized and pixel rectangles throw`() {
        assertThrows(IllegalArgumentException::class.java) { NormalizedRect(0.5f, 0f, 0.2f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { PixelRect(0, 0, 0, 10) }
    }
}
