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

    @Test fun `the extracted image matches the oriented appearance for every rotation`() {
        // Asymmetric image so orientation mistakes are visible.
        val pixels = intArrayOf(
            0xFF000000.toInt(), 0xFF000001.toInt(), 0xFF000002.toInt(), 0xFF000003.toInt(),
            0xFF000010.toInt(), 0xFF000011.toInt(), 0xFF000012.toInt(), 0xFF000013.toInt(),
            0xFF000100.toInt(), 0xFF000101.toInt(), 0xFF000102.toInt(), 0xFF000103.toInt(),
            0xFF001000.toInt(), 0xFF001001.toInt(), 0xFF001002.toInt(), 0xFF001003.toInt(),
        )
        val rect = NormalizedRect(0f, 0f, 0.5f, 0.5f) // source top-left quadrant
        for (rotation in FrameRotation.entries) {
            val source = frame(4, 4, rotation, pixels.copyOf())
            val result = PixelSlotImageExtractor.extract(source, slot(rect))

            assertTrue("rotation $rotation must succeed", result.isSuccess)
            val image = result.getOrThrow().payload as PixelArrayFramePayload
            assertEquals("dims[$rotation]", 2, image.width)
            assertEquals("dims[$rotation]", 2, image.height)
            val content = image.pixels().toList()
            assertEquals("upright[$rotation]", expectedQuadrant(rotation), content)
        }
    }

    private fun expectedQuadrant(rotation: FrameRotation): List<Int> {
        val a = 0xFF000000.toInt()
        val b = 0xFF000001.toInt()
        val e = 0xFF000010.toInt()
        val f = 0xFF000011.toInt()
        // Axis-aligned 2x2 crop of the source top-left quadrant, upright-rotated per frame rotation.
        return when (rotation) {
            FrameRotation.ROTATION_0 -> listOf(a, b, e, f)
            FrameRotation.ROTATION_90 -> listOf(e, f, a, b)
            FrameRotation.ROTATION_180 -> listOf(f, e, b, a)
            FrameRotation.ROTATION_270 -> listOf(b, a, f, e)
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
