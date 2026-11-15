package com.draftlens

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.draftlens.models.FrameSnapshots
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.heroes.AndroidBitmapSlotImageExtractor
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.SlotImage
import com.draftlens.vision.model.android.BitmapFramePayload
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side verification of the Android extractor: exact pixels, ownership and failure
 * paths. Run on an emulator or device; without one this class is compiled but not executed.
 */
@RunWith(AndroidJUnit4::class)
class AndroidBitmapExtractionInstrumentedTest {

    private val ownedBitmaps = mutableListOf<Bitmap>()

    @After fun tearDown() {
        ownedBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        ownedBitmaps.clear()
    }

    private fun track(bitmap: Bitmap): Bitmap = bitmap.also { ownedBitmaps.add(it) }

    private fun slot(rect: NormalizedRect, id: String = "slot-1") = DraftSlot(
        id = id,
        type = SlotType.BAN,
        team = SlotTeam.ALLY,
        index = 1,
        rect = rect,
    )

    private fun frame(width: Int, height: Int, rotation: FrameRotation, pixels: IntArray): OriginalFrame {
        val bitmap = track(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return OriginalFrame(
            id = FrameId("ft-$width-$height-$rotation"),
            payload = BitmapFramePayload(bitmap),
            sourceWidth = width,
            sourceHeight = height,
            displayRotation = FrameRotation.ROTATION_0,
            rotation = rotation,
            capturedAtElapsedRealtimeMs = 1,
            wallClockTimestampMs = 1,
            captureLatencyMs = 1,
        )
    }

    private fun pixelsOf(image: SlotImage): Pair<IntArray, Int> {
        val bitmap = (image.payload as BitmapFramePayload).bitmap
        assertFalse(bitmap.isRecycled)
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels to bitmap.width
    }

    // Asymmetric 3x2 source; values chosen so every rotation is distinguishable.
    private val asymmetric = intArrayOf(10, 11, 12, 20, 21, 22)

    // 5x3 source used for edge crops.
    private val edgeFrame = intArrayOf(
        0, 1, 2, 3, 4,
        10, 11, 12, 13, 14,
        20, 21, 22, 23, 24,
    )

    @Test fun `all four rotations produce the exact expected pixels for a non-square crop`() {
        val rect = NormalizedRect(0f, 0f, 1f, 1f)
        val expected = mapOf(
            FrameRotation.ROTATION_0 to Triple(3, 2, intArrayOf(10, 11, 12, 20, 21, 22)),
            FrameRotation.ROTATION_90 to Triple(2, 3, intArrayOf(20, 10, 21, 11, 22, 12)),
            FrameRotation.ROTATION_180 to Triple(3, 2, intArrayOf(22, 21, 20, 12, 11, 10)),
            FrameRotation.ROTATION_270 to Triple(2, 3, intArrayOf(12, 22, 11, 21, 10, 20)),
        )
        for (rotation in FrameRotation.entries) {
            val source = frame(3, 2, rotation, asymmetric.copyOf())
            val result = AndroidBitmapSlotImageExtractor.extract(source, slot(rect))

            assertTrue("rotation $rotation must succeed", result.isSuccess)
            val image = result.getOrThrow()
            val (pixels, width) = pixelsOf(image)
            val (expectedWidth, expectedHeight, expectedPixels) = expected.getValue(rotation)
            assertEquals("width[$rotation]", expectedWidth, width)
            assertEquals("height[$rotation]", expectedHeight, (image.payload as BitmapFramePayload).bitmap.height)
            assertArrayEquals("pixels[$rotation]", expectedPixels, pixels)
        }
    }

    @Test fun `crops touching the top and bottom edges extract exactly the edge columns`() {
        val rect = NormalizedRect(0.2f, 0f, 0.6f, 1f) // left=1px, right=3px of 5
        val expected = mapOf(
            FrameRotation.ROTATION_0 to Triple(2, 3, intArrayOf(1, 2, 11, 12, 21, 22)),
            FrameRotation.ROTATION_90 to Triple(3, 2, intArrayOf(21, 11, 1, 22, 12, 2)),
            FrameRotation.ROTATION_180 to Triple(2, 3, intArrayOf(22, 21, 12, 11, 2, 1)),
            FrameRotation.ROTATION_270 to Triple(3, 2, intArrayOf(2, 12, 22, 1, 11, 21)),
        )
        for (rotation in FrameRotation.entries) {
            val source = frame(5, 3, rotation, edgeFrame.copyOf())
            val result = AndroidBitmapSlotImageExtractor.extract(source, slot(rect))

            assertTrue("rotation $rotation must succeed", result.isSuccess)
            val (pixels, width) = pixelsOf(result.getOrThrow())
            val (expectedWidth, expectedHeight, expectedPixels) = expected.getValue(rotation)
            assertEquals("width[$rotation]", expectedWidth, width)
            assertEquals("height[$rotation]", expectedHeight, (result.getOrThrow().payload as BitmapFramePayload).bitmap.height)
            assertArrayEquals("pixels[$rotation]", expectedPixels, pixels)
        }
    }

    @Test fun `a crop on the bottom and right edges extracts exactly those pixels`() {
        val rect = NormalizedRect(0.6f, 1f / 3f, 1f, 1f) // left=3px, top=1px of 5x3
        val expected = mapOf(
            FrameRotation.ROTATION_0 to Triple(2, 2, intArrayOf(13, 14, 23, 24)),
            FrameRotation.ROTATION_90 to Triple(2, 2, intArrayOf(23, 13, 24, 14)),
            FrameRotation.ROTATION_180 to Triple(2, 2, intArrayOf(24, 23, 14, 13)),
            FrameRotation.ROTATION_270 to Triple(2, 2, intArrayOf(14, 24, 13, 23)),
        )
        for (rotation in FrameRotation.entries) {
            val source = frame(5, 3, rotation, edgeFrame.copyOf())
            val result = AndroidBitmapSlotImageExtractor.extract(source, slot(rect))

            assertTrue("rotation $rotation must succeed", result.isSuccess)
            val (pixels, width) = pixelsOf(result.getOrThrow())
            val (expectedWidth, expectedHeight, expectedPixels) = expected.getValue(rotation)
            assertEquals("width[$rotation]", expectedWidth, width)
            assertEquals("height[$rotation]", expectedHeight, (result.getOrThrow().payload as BitmapFramePayload).bitmap.height)
            assertArrayEquals("pixels[$rotation]", expectedPixels, pixels)
        }
    }

    @Test fun `a payload whose bitmap dimensions do not match the source is refused`() {
        val bitmap = track(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888))
        val frame = OriginalFrame(
            id = FrameId("mismatch"),
            payload = BitmapFramePayload(bitmap),
            sourceWidth = 4,
            sourceHeight = 4,
            displayRotation = FrameRotation.ROTATION_0,
            rotation = FrameRotation.ROTATION_0,
            capturedAtElapsedRealtimeMs = 1,
            wallClockTimestampMs = 1,
            captureLatencyMs = 1,
        )

        val result = AndroidBitmapSlotImageExtractor.extract(frame, slot(NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f)))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("does not match"))
    }

    @Test fun `the source bitmap stays valid and unchanged after extraction`() {
        val source = frame(4, 3, FrameRotation.ROTATION_90, edgeFrame.copyOf())
        val bitmap = (source.payload as BitmapFramePayload).bitmap
        val before = IntArray(edgeFrame.size).also { bitmap.getPixels(it, 0, 4, 0, 0, 4, 3) }

        val result = AndroidBitmapSlotImageExtractor.extract(source, slot(NormalizedRect(0.2f, 0f, 0.6f, 1f)))

        assertTrue(result.isSuccess)
        assertFalse("the source bitmap must not be recycled", bitmap.isRecycled)
        val after = IntArray(edgeFrame.size).also { bitmap.getPixels(it, 0, 4, 0, 0, 4, 3) }
        assertArrayEquals("the source pixels must be unchanged", before, after)
    }

    @Test fun `the minimum valid crop produces exactly one pixel`() {
        val source = frame(128, 128, FrameRotation.ROTATION_0, IntArray(128 * 128) { it })
        val result = AndroidBitmapSlotImageExtractor.extract(
            source,
            slot(NormalizedRect(0.125f, 0.125f, 0.1328125f, 0.1328125f)),
        )

        assertTrue(result.isSuccess)
        val (pixels, width) = pixelsOf(result.getOrThrow())
        assertEquals(1, width)
        assertEquals(1, (result.getOrThrow().payload as BitmapFramePayload).bitmap.height)
        assertEquals(16 * 128 + 16, pixels.single())
    }

    @Test fun `a rectangle smaller than one pixel is refused`() {
        val source = frame(10, 10, FrameRotation.ROTATION_0, IntArray(100) { 7 })

        val result = AndroidBitmapSlotImageExtractor.extract(source, slot(NormalizedRect(0f, 0f, 0.05f, 0.05f)))

        assertTrue(result.isFailure)
    }

    @Test fun `a recycled source bitmap is refused without a crash`() {
        val source = frame(4, 3, FrameRotation.ROTATION_0, edgeFrame.copyOf())
        val bitmap = (source.payload as BitmapFramePayload).bitmap
        bitmap.recycle()

        val result = AndroidBitmapSlotImageExtractor.extract(source, slot(NormalizedRect(0f, 0f, 1f, 1f)))

        assertTrue(result.isFailure)
    }

    @Test fun `a bitmap frame snapshot shares no pixels with the pipeline copy`() {
        val original = frame(4, 2, FrameRotation.ROTATION_0, asymmetric.copyOf())
        val snapshot = FrameSnapshots.copyOf(original)

        assertTrue(snapshot != null)
        val sourceBitmap = (original.payload as BitmapFramePayload).bitmap
        val snapshotBitmap = (snapshot!!.payload as BitmapFramePayload).bitmap
        assertNotSame(sourceBitmap, snapshotBitmap)
        ownedBitmaps.add(snapshotBitmap)
        assertFalse("the source must not be recycled by snapshotting", sourceBitmap.isRecycled)
        assertEquals(
            snapshotBitmap.width,
            sourceBitmap.width,
        )
        sourceBitmap.setPixels(IntArray(8) { -1 }, 0, 4, 0, 0, 4, 2)
        val snapshotAfter = IntArray(8).also { snapshotBitmap.getPixels(it, 0, 4, 0, 0, 4, 2) }
        assertTrue(
            "mutating the live bitmap must not leak into the frozen snapshot",
            snapshotAfter.contains(10),
        )
    }
}
