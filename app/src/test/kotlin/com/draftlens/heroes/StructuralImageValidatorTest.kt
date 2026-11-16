package com.draftlens.heroes

import com.draftlens.vision.heroes.StructuralImageValidator
import com.draftlens.vision.testRealPng
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuralImageValidatorTest {

    @Test fun `a real png passes`() {
        assertTrue(StructuralImageValidator.isDecodable(testRealPng()))
    }

    @Test fun `a truncated png fails`() {
        assertFalse(StructuralImageValidator.isDecodable(testRealPng().copyOf(30)))
        assertFalse(StructuralImageValidator.isDecodable(testRealPng().copyOf(8)))
    }

    @Test fun `a png with a corrupted crc byte fails`() {
        val broken = testRealPng().copyOf()
        broken[20] = (broken[20] + 1).toByte()

        assertFalse(StructuralImageValidator.isDecodable(broken))
    }

    @Test fun `an html document is not an image`() {
        assertFalse(StructuralImageValidator.isDecodable("<html><body>502 Bad Gateway</body></html>".toByteArray()))
    }

    @Test fun `a minimal well-formed gif passes`() {
        val bytes = "GIF89a".toByteArray() + ByteArray(14) { 0x01 } + byteArrayOf(0x3B)
        assertTrue(StructuralImageValidator.isDecodable(bytes))
    }

    @Test fun `a minimal well-formed webp passes`() {
        val bytes = "RIFF".toByteArray() +
            byteArrayOf(16, 0, 0, 0) + // little-endian RIFF size
            "WEBP".toByteArray() +
            "VP8L".toByteArray() +
            byteArrayOf(4, 0, 0, 0) + // little-endian chunk size
            byteArrayOf(1, 2, 3, 4)
        assertTrue(StructuralImageValidator.isDecodable(bytes))
    }

    @Test fun `a webp with an out-of-bounds chunk fails`() {
        val bytes = "RIFF".toByteArray() +
            byteArrayOf(100, 0, 0, 0) +
            "WEBP".toByteArray() +
            "VP8L".toByteArray() +
            byteArrayOf(99, 0, 0, 0) +
            byteArrayOf(1, 2)
        assertFalse(StructuralImageValidator.isDecodable(bytes))
    }

    @Test fun `random bytes are not an image`() {
        assertFalse(StructuralImageValidator.isDecodable(ByteArray(64) { (it * 67 + 3).toByte() }))
    }
}
