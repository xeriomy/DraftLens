package com.draftlens.heroes

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.heroes.PixelSlotImageExtractor
import com.draftlens.vision.heroes.slotCropBounds
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun frame(width: Int, height: Int, rotation: FrameRotation, pixels: IntArray = IntArray(width * height) { it }) =
    OriginalFrame(
        id = FrameId("frame-$width-$height-$rotation"),
        payload = PixelArrayFramePayload(width, height, pixels),
        sourceWidth = width,
        sourceHeight = height,
        displayRotation = FrameRotation.ROTATION_0,
        rotation = rotation,
        capturedAtElapsedRealtimeMs = 1,
        wallClockTimestampMs = 1,
        captureLatencyMs = 1,
    )

private fun slot(rect: NormalizedRect, id: String = "slot-1") = DraftSlot(
    id = id,
    type = SlotType.BAN,
    team = SlotTeam.ALLY,
    index = 1,
    rect = rect,
)

class SlotImageExtractorTest {

    @Test fun `portrait and landscape frames extract without mutating the source`() {
        val portrait = frame(4, 8, FrameRotation.ROTATION_0)
        val landscape = frame(8, 4, FrameRotation.ROTATION_0)
        val before = portrait.payload as PixelArrayFramePayload

        val result = PixelSlotImageExtractor.extract(portrait, slot(NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)))

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrThrow().payload.width)
        assertEquals(4, result.getOrThrow().payload.height)
        assertEquals(before.pixels().toList(), (portrait.payload as PixelArrayFramePayload).pixels().toList())

        val wide = PixelSlotImageExtractor.extract(landscape, slot(NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)))
        assertTrue(wide.isSuccess)
        assertEquals(4, wide.getOrThrow().payload.width)
        assertEquals(2, wide.getOrThrow().payload.height)
    }

    /**
     * Asymmetric 3x2 source for rotation tests (not square on purpose):
     *   row 0: 10 11 12
     *   row 1: 20 21 22
     * Independently calculated by hand against FrameTransform.sourceToOriented().
     */
    private val asymmetricPixels = intArrayOf(10, 11, 12, 20, 21, 22)

    @Test fun `the extracted image matches the sourceToOriented convention for every rotation`() {
        val rect = NormalizedRect(0f, 0f, 1f, 1f) // full frame; rotation is translation-invariant
        val expected = mapOf(
            FrameRotation.ROTATION_0 to Triple(3, 2, intArrayOf(10, 11, 12, 20, 21, 22)),
            FrameRotation.ROTATION_90 to Triple(2, 3, intArrayOf(20, 10, 21, 11, 22, 12)),
            FrameRotation.ROTATION_180 to Triple(3, 2, intArrayOf(22, 21, 20, 12, 11, 10)),
            FrameRotation.ROTATION_270 to Triple(2, 3, intArrayOf(12, 22, 11, 21, 10, 20)),
        )
        for (rotation in FrameRotation.entries) {
            val source = frame(3, 2, rotation, asymmetricPixels.copyOf())
            val result = PixelSlotImageExtractor.extract(source, slot(rect))

            assertTrue("rotation $rotation must succeed", result.isSuccess)
            val image = result.getOrThrow().payload as PixelArrayFramePayload
            val (expectedWidth, expectedHeight, expectedPixels) = expected.getValue(rotation)
            assertEquals("dims[$rotation]", expectedWidth, image.width)
            assertEquals("dims[$rotation]", expectedHeight, image.height)
            assertEquals("pixels[$rotation]", expectedPixels.toList(), image.pixels().toList())
        }
    }

    /**
     * 5x3 frame; crop covers columns 1..3, all rows (top and bottom edge, half the width):
     *   row 0: 01 02
     *   row 1: 11 12
     *   row 2: 21 22   (crop is 2 wide x 3 tall)
     * Expected values computed by hand from FrameTransform.sourceToOriented().
     */
    private val edgeFramePixels = intArrayOf(
        0, 1, 2, 3, 4,
        10, 11, 12, 13, 14,
        20, 21, 22, 23, 24,
    )

    @Test fun `a crop touching the top and bottom edges extracts the edge columns exactly`() {
        val rect = NormalizedRect(0.2f, 0f, 0.6f, 1f) // left=1px, right=3px of 5
        val expected = mapOf(
            FrameRotation.ROTATION_0 to Triple(2, 3, intArrayOf(1, 2, 11, 12, 21, 22)),
            FrameRotation.ROTATION_90 to Triple(3, 2, intArrayOf(21, 11, 1, 22, 12, 2)),
            FrameRotation.ROTATION_180 to Triple(2, 3, intArrayOf(22, 21, 12, 11, 2, 1)),
            FrameRotation.ROTATION_270 to Triple(3, 2, intArrayOf(2, 12, 22, 1, 11, 21)),
        )
        for (rotation in FrameRotation.entries) {
            val source = frame(5, 3, rotation, edgeFramePixels.copyOf())
            val result = PixelSlotImageExtractor.extract(source, slot(rect))

            assertTrue("rotation $rotation must succeed", result.isSuccess)
            val image = result.getOrThrow().payload as PixelArrayFramePayload
            val (expectedWidth, expectedHeight, expectedPixels) = expected.getValue(rotation)
            assertEquals("dims[$rotation]", expectedWidth, image.width)
            assertEquals("dims[$rotation]", expectedHeight, image.height)
            assertEquals("pixels[$rotation]", expectedPixels.toList(), image.pixels().toList())
        }
    }

    /**
     * Same 5x3 frame; crop covers rows 1..3, columns 3..5 (bottom and right edges):
     *   row 1: 13 14
     *   row 2: 23 24   (crop is 2 wide x 2 tall)
     */
    @Test fun `a crop touching the bottom and right edges extracts exactly those pixels`() {
        val rect = NormalizedRect(0.6f, 1f / 3f, 1f, 1f) // left=3px, top=1px
        val expected = mapOf(
            FrameRotation.ROTATION_0 to Triple(2, 2, intArrayOf(13, 14, 23, 24)),
            FrameRotation.ROTATION_90 to Triple(2, 2, intArrayOf(23, 13, 24, 14)),
            FrameRotation.ROTATION_180 to Triple(2, 2, intArrayOf(24, 23, 14, 13)),
            FrameRotation.ROTATION_270 to Triple(2, 2, intArrayOf(14, 24, 13, 23)),
        )
        for (rotation in FrameRotation.entries) {
            val source = frame(5, 3, rotation, edgeFramePixels.copyOf())
            val result = PixelSlotImageExtractor.extract(source, slot(rect))

            assertTrue("rotation $rotation must succeed", result.isSuccess)
            val image = result.getOrThrow().payload as PixelArrayFramePayload
            val (expectedWidth, expectedHeight, expectedPixels) = expected.getValue(rotation)
            assertEquals("dims[$rotation]", expectedWidth, image.width)
            assertEquals("dims[$rotation]", expectedHeight, image.height)
            assertEquals("pixels[$rotation]", expectedPixels.toList(), image.pixels().toList())
        }
    }

    @Test fun `extracting does not mutate the source pixel array for any rotation`() {
        val rect = NormalizedRect(0.2f, 0f, 0.6f, 1f)
        for (rotation in FrameRotation.entries) {
            val pixels = edgeFramePixels.copyOf()
            val source = frame(5, 3, rotation, pixels)

            PixelSlotImageExtractor.extract(source, slot(rect))

            assertEquals("source mutated for $rotation", edgeFramePixels.toList(), pixels.toList())
        }
    }

    @Test fun `a rectangle touching the image edges extracts the edge pixels`() {
        val pixels = IntArray(16) { it }
        val source = frame(4, 4, FrameRotation.ROTATION_0, pixels)
        val result = PixelSlotImageExtractor.extract(source, slot(NormalizedRect(0.5f, 0.5f, 1f, 1f)))

        val image = result.getOrThrow().payload as PixelArrayFramePayload
        assertEquals(2, image.width)
        assertEquals(2, image.height)
        assertEquals(listOf(10, 11, 14, 15), image.pixels().toList())
    }

    @Test fun `the minimum-size rectangle extracts exactly one pixel`() {
        // 128x128 keeps 1/128-steps exact in float; 0.125..0.1328125 spans exactly one pixel.
        val source = frame(128, 128, FrameRotation.ROTATION_0, IntArray(128 * 128) { it })
        val result = PixelSlotImageExtractor.extract(source, slot(NormalizedRect(0.125f, 0.125f, 0.1328125f, 0.1328125f)))

        val image = result.getOrThrow().payload as PixelArrayFramePayload
        assertEquals(1, image.width)
        assertEquals(1, image.height)
        assertEquals(16 * 128 + 16, image.pixels().single())
    }

    @Test fun `a rectangle smaller than one pixel is refused`() {
        val source = frame(10, 10, FrameRotation.ROTATION_0)

        val result = PixelSlotImageExtractor.extract(source, slot(NormalizedRect(0f, 0f, 0.05f, 0.05f), "tiny"))

        assertTrue(result.isFailure)
    }

    @Test fun `a payload that does not match the source dimensions is refused`() {
        val source = OriginalFrame(
            id = FrameId("mismatch"),
            payload = PixelArrayFramePayload(2, 2, IntArray(4)),
            sourceWidth = 4,
            sourceHeight = 4,
            displayRotation = FrameRotation.ROTATION_0,
            rotation = FrameRotation.ROTATION_0,
            capturedAtElapsedRealtimeMs = 1,
            wallClockTimestampMs = 1,
            captureLatencyMs = 1,
        )

        val result = PixelSlotImageExtractor.extract(source, slot(NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f)))

        assertTrue(result.isFailure)
    }

    @Test fun `the crop bounds are floored at the origin and ceiled at the far edge`() {
        val bounds = slotCropBounds(NormalizedRect(0.2f, 0.25f, 0.5f, 0.75f), 100, 200)

        assertEquals(com.draftlens.vision.geometry.PixelRect(20, 50, 50, 150), bounds)
    }
}
