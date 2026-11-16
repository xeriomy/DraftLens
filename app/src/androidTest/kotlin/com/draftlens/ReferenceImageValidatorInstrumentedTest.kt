package com.draftlens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.draftlens.vision.heroes.AndroidReferenceImageValidator
import com.draftlens.vision.heroes.StructuralImageValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Device-side verification that cached assets are actually decodable, not just well-framed. */
@RunWith(AndroidJUnit4::class)
class ReferenceImageValidatorInstrumentedTest {

    private val realPng = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x08.toByte(), 0x02.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x90.toByte(), 0x77.toByte(), 0x53.toByte(),
        0xDE.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0C.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(),
        0x54.toByte(), 0x78.toByte(), 0x9C.toByte(), 0x63.toByte(), 0xF8.toByte(), 0xCF.toByte(), 0xC0.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x03.toByte(), 0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0xC9.toByte(), 0xFE.toByte(), 0x92.toByte(),
        0xEF.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(), 0x45.toByte(), 0x4E.toByte(),
        0x44.toByte(), 0xAE.toByte(), 0x42.toByte(), 0x60.toByte(), 0x82.toByte(),
    )

    @Test fun realPngDecodesOnDevice() {
        assertTrue(AndroidReferenceImageValidator.isDecodable(realPng))
        assertTrue(StructuralImageValidator.isDecodable(realPng))
    }

    @Test fun truncatedPngDoesNotDecode() {
        val truncated = realPng.copyOf(30)
        assertFalse(AndroidReferenceImageValidator.isDecodable(truncated))
        assertFalse(StructuralImageValidator.isDecodable(truncated))
    }

    @Test fun htmlErrorPageIsNotAnImage() {
        val html = "<html><body>502 Bad Gateway</body></html>".toByteArray()
        assertFalse(AndroidReferenceImageValidator.isDecodable(html))
        assertFalse(StructuralImageValidator.isDecodable(html))
    }

    @Test fun jpegPassingStructureCheckFailsDeviceDecode() {
        // Structure check is a first gate (SOI + EOI present); the device adds a real decode.
        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x01, 0x02, 0x03, 0xFF.toByte(), 0xD9.toByte())
        assertTrue(StructuralImageValidator.isDecodable(fakeJpeg))
        assertFalse(AndroidReferenceImageValidator.isDecodable(fakeJpeg))
    }
}
