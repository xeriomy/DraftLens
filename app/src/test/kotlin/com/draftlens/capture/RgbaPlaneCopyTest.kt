package com.draftlens.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

private fun planeLayoutOf(
    width: Int,
    height: Int,
    rowStride: Int = width * 4,
    pixelStride: Int = 4,
    cropLeft: Int = 0,
    cropTop: Int = 0,
    cropRight: Int = width,
    cropBottom: Int = height,
): RgbaPlaneCopy.PlaneLayout =
    RgbaPlaneCopy.layout(width, height, rowStride, pixelStride, cropLeft, cropTop, cropRight, cropBottom)!!

/** Deterministic, asymmetric pixel values so transposition/row-offset bugs are visible. */
private fun asymmetricPixel(x: Int, y: Int): Int {
    val r = (x * 37 + 11) and 0xFF
    val g = (y * 91 + 7) and 0xFF
    val b = ((x * 13) xor (y * 29)) and 0xFF
    return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
}

private fun pixels(width: Int, height: Int): IntArray =
    IntArray(width * height) { i -> asymmetricPixel(i % width, i / width) }

private fun bufferFor(
    pixels: IntArray,
    width: Int,
    height: Int,
    rowStride: Int = width * 4,
    pixelStride: Int = 4,
    bigEndian: Boolean = false,
): ByteBuffer {
    val bigEndianMode = if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN
    val buffer = ByteBuffer.allocate(rowStride * height)
    buffer.order(bigEndianMode)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val p = pixels[y * width + x]
            val offset = y * rowStride + x * pixelStride
            if (bigEndianMode == ByteOrder.BIG_ENDIAN) {
                buffer.put(offset, ((p shr 24) and 0xFF).toByte())      // A
                buffer.put(offset + 1, ((p shr 16) and 0xFF).toByte())  // R
                buffer.put(offset + 2, ((p shr 8) and 0xFF).toByte())   // G
                buffer.put(offset + 3, (p and 0xFF).toByte())           // B
            } else {
                buffer.put(offset, ((p shr 16) and 0xFF).toByte())      // R
                buffer.put(offset + 1, ((p shr 8) and 0xFF).toByte())   // G
                buffer.put(offset + 2, (p and 0xFF).toByte())           // B
                buffer.put(offset + 3, ((p shr 24) and 0xFF).toByte())  // A
            }
        }
    }
    return buffer
}

private fun subImage(pixels: IntArray, frameWidth: Int, left: Int, top: Int, width: Int, height: Int): IntArray {
    val out = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            out[y * width + x] = pixels[(top + y) * frameWidth + (left + x)]
        }
    }
    return out
}

class RgbaPlaneCopyTest {

    @Test fun `a landscape 2400x1080 frame converts pixel-exactly`() {
        val scaled = IntArray(2400 * 1080) { i -> asymmetricPixel(i % 2400, i / 2400) }
        val buffer = bufferFor(scaled, 2400, 1080)

        val layout = planeLayoutOf(2400, 1080)
        val out = RgbaPlaneCopy.copyArgb(buffer, layout)!!

        assertEquals(scaled.size, out.size)
        assertArrayEquals(scaled, out)
    }

    @Test fun `a portrait 1080x2400 frame converts pixel-exactly`() {
        val scaled = IntArray(1080 * 2400) { i -> asymmetricPixel(i % 1080, i / 1080) }
        val buffer = bufferFor(scaled, 1080, 2400)

        val out = RgbaPlaneCopy.copyArgb(buffer, planeLayoutOf(1080, 2400))!!

        assertArrayEquals(scaled, out)
    }

    @Test fun `a non-square asymmetric frame converts pixel-exactly`() {
        val source = pixels(5, 3)
        val buffer = bufferFor(source, 5, 3)

        val out = RgbaPlaneCopy.copyArgb(buffer, planeLayoutOf(5, 3))!!

        assertArrayEquals(source, out)
    }

    @Test fun `padded rows are skipped row by row`() {
        val source = pixels(17, 9)
        val width = 17
        val rowStride = width * 4 + 64 // 16 pad pixels per row
        val buffer = bufferFor(source, width, 9, rowStride = rowStride)

        val out = RgbaPlaneCopy.copyArgb(buffer, planeLayoutOf(width, 9, rowStride = rowStride))!!

        assertArrayEquals(source, out)
    }

    @Test fun `row padding of one pixel per row is skipped`() {
        val source = pixels(7, 4)
        val buffer = bufferFor(source, 7, 4, rowStride = 7 * 4 + 4) // one 4-byte pixel of padding

        val out = RgbaPlaneCopy.copyArgb(buffer, planeLayoutOf(7, 4, rowStride = 32))!!

        assertArrayEquals(source, out)
    }

    @Test fun `an offset cropRect reads only the crop region`() {
        val source = pixels(9, 6)
        val buffer = bufferFor(source, 9, 6)

        // Crop columns 2..6 and rows 1..4 of the 9x6 frame.
        val layout = planeLayoutOf(9, 6, cropLeft = 2, cropTop = 1, cropRight = 6, cropBottom = 4)
        val out = RgbaPlaneCopy.copyArgb(buffer, layout)!!

        assertArrayEquals(subImage(source, 9, left = 2, top = 1, width = 4, height = 3), out)
        assertEquals(4, layout.cropWidth)
        assertEquals(3, layout.cropHeight)
    }

    @Test fun `an offset cropRect with padded rows is still read exactly`() {
        val source = pixels(11, 7)
        val rowStride = 11 * 4 + 128
        val buffer = bufferFor(source, 11, 7, rowStride = rowStride)

        val layout = planeLayoutOf(11, 7, rowStride = rowStride, cropLeft = 3, cropTop = 2, cropRight = 9, cropBottom = 6)
        val out = RgbaPlaneCopy.copyArgb(buffer, layout)!!

        assertArrayEquals(subImage(source, 11, left = 3, top = 2, width = 6, height = 4), out)
    }

    @Test fun `a crop touching the right and bottom edges reads exactly those pixels`() {
        val source = pixels(6, 4)
        val buffer = bufferFor(source, 6, 4)

        val layout = planeLayoutOf(6, 4, cropLeft = 4, cropTop = 2, cropRight = 6, cropBottom = 4)
        val out = RgbaPlaneCopy.copyArgb(buffer, layout)!!

        assertArrayEquals(subImage(source, 6, left = 4, top = 2, width = 2, height = 2), out)
    }

    @Test fun `a crop smaller than one pixel is refused`() {
        val buffer = bufferFor(pixels(4, 4), 4, 4)

        assertNull(RgbaPlaneCopy.layout(4, 4, 16, 4, 1, 1, 1, 2))
        assertNull(RgbaPlaneCopy.layout(4, 4, 16, 4, 2, 2, 2, 2))
    }

    @Test fun `a crop outside the image bounds is refused`() {
        assertNull(RgbaPlaneCopy.layout(4, 4, 16, 4, -1, 0, 2, 2))
        assertNull(RgbaPlaneCopy.layout(4, 4, 16, 4, 0, -1, 2, 2))
        assertNull(RgbaPlaneCopy.layout(4, 4, 16, 4, 3, 3, 5, 4))
        assertNull(RgbaPlaneCopy.layout(4, 4, 16, 4, 0, 0, 4, 5))
    }

    @Test fun `a row stride smaller than the image row is refused`() {
        assertNull(RgbaPlaneCopy.layout(10, 4, 8 * 4, 4, 0, 0, 10, 4))
        assertNull(RgbaPlaneCopy.layout(10, 4, 0, 4, 0, 0, 10, 4))
    }

    @Test fun `pixelStride variations are supported`() {
        // RGBA_8888 is 4 bytes per pixel; 8 exercises a per-pixel padding variant.
        val source = pixels(4, 2)
        for (pixelStride in listOf(4, 8)) {
            val rowStride = 4 * pixelStride + 2 * pixelStride // two pad elements
            val buffer = bufferFor(source, 4, 2, rowStride = rowStride, pixelStride = pixelStride)
            val layout = planeLayoutOf(4, 2, rowStride = rowStride, pixelStride = pixelStride)
            val out = RgbaPlaneCopy.copyArgb(buffer, layout)!!
            assertArrayEquals("pixelStride=$pixelStride", source, out)
        }
    }

    @Test fun `a big-endian buffer is read with its own byte order`() {
        val source = pixels(5, 3)
        val buffer = bufferFor(source, 5, 3, bigEndian = true)

        val out = RgbaPlaneCopy.copyArgb(buffer, planeLayoutOf(5, 3))!!

        assertArrayEquals(source, out)
    }

    @Test fun `the caller buffer keeps its position and remaining bytes`() {
        val source = pixels(6, 2)
        val buffer = bufferFor(source, 6, 2)
        buffer.position(4)
        val remainingBefore = buffer.remaining()

        val out = RgbaPlaneCopy.copyArgb(buffer, planeLayoutOf(6, 2))!!

        assertArrayEquals(source, out)
        assertEquals(remainingBefore, buffer.remaining())
        assertEquals(4, buffer.position())
    }

    @Test fun `a buffer smaller than the declared plane is refused`() {
        val source = pixels(4, 1)
        val buffer = ByteBuffer.allocate(source.size) // too small for rowStride * height
        val layout = planeLayoutOf(4, 1)

        assertNull(RgbaPlaneCopy.copyArgb(buffer, layout))
    }

    @Test fun `a zero-sized image is refused`() {
        assertNull(RgbaPlaneCopy.layout(0, 2, 0, 4, 0, 0, 0, 2))
        assertNull(RgbaPlaneCopy.layout(2, 0, 8, 4, 0, 0, 2, 0))
    }

    @Test fun `asymmetric pixel values survive a full crop at a different scale`() {
        // The "captured only part of the game" layout: a landscape game letterboxed
        // inside a portrait-sized buffer. The converter must return exactly the
        // letterboxed region, not the buffer with garbage around it.
        val game = pixels(24, 8)
        val bufferWidth = 24
        val bufferHeight = 32
        val frame = IntArray(bufferWidth * bufferHeight) { i ->
            // letterbox: rows 12..19 hold the game
            val y = i / bufferWidth
            if (y in 12 until 20) game[(y - 12) * bufferWidth + (i % bufferWidth)] else 0xFF111111.toInt()
        }
        val buffer = bufferFor(frame, bufferWidth, bufferHeight)

        val layout = planeLayoutOf(bufferWidth, bufferHeight, cropLeft = 0, cropTop = 12, cropRight = 24, cropBottom = 20)
        val out = RgbaPlaneCopy.copyArgb(buffer, layout)!!

        assertArrayEquals(game, out)
        assertNotEquals(frame.size, out.size)
    }

    @Test fun `the full-crop case equals a direct read of the same plane`() {
        val source = pixels(13, 5)
        val buffer = bufferFor(source, 13, 5)

        val layout = planeLayoutOf(13, 5)
        val out = RgbaPlaneCopy.copyArgb(buffer, layout)!!

        assertTrue(layout.isFullCrop)
        assertArrayEquals(source, out)
    }
}
