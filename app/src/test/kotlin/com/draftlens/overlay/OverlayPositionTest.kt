package com.draftlens.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPositionTest {

    private val size = 150 // px, ~52dp at xxhdpi
    private val portraitW = 1080
    private val portraitH = 2400
    private val landscapeW = 2400
    private val landscapeH = 1080

    @Test fun `pixel to normalized to pixel round-trips`() {
        val px = OverlayPosition.Pixels(left = 900, top = 300)
        val normalized = OverlayPosition.pixelsToNormalized(px.left, px.top, portraitW, portraitH, size)
        val back = OverlayPosition.normalizedToPixels(normalized, portraitW, portraitH, size)

        assertEquals(px.left, back.left)
        assertEquals(px.top, back.top)
    }

    @Test fun `normalized position survives portrait to landscape round-trip`() {
        val portrait = OverlayPosition.Pixels(left = 40, top = 600)
        val normalized = OverlayPosition.pixelsToNormalized(portrait.left, portrait.top, portraitW, portraitH, size)
        val landscape = OverlayPosition.normalizedToPixels(normalized, landscapeW, landscapeH, size)
        val normalizedAgain = OverlayPosition.pixelsToNormalized(landscape.left, landscape.top, landscapeW, landscapeH, size)

        assertEquals(normalized.x, normalizedAgain.x, 0.001f)
        assertEquals(normalized.y, normalizedAgain.y, 0.01f)
        assertTrue(landscape.top in 0..(landscapeH - size))
    }

    @Test fun `out-of-screen positions are clamped inside`() {
        val normalized = OverlayPosition.pixelsToNormalized(-500, 99999, portraitW, portraitH, size)
        val px = OverlayPosition.normalizedToPixels(normalized, portraitW, portraitH, size)

        assertEquals(0, px.left)
        assertEquals(portraitH - size, px.top)
    }

    @Test fun `snap chooses left edge when the button center is on the left half`() {
        val snapped = OverlayPosition.snapToNearestEdge(leftPx = 100, topPx = 800, screenWidthPx = portraitW, screenHeightPx = portraitH, sizePx = size)

        assertEquals(0, snapped.left)
        assertEquals(OverlayPosition.HorizontalEdge.LEFT, snapped.edge)
        assertEquals(800, snapped.top)
    }

    @Test fun `snap chooses right edge when the button center is on the right half`() {
        val snapped = OverlayPosition.snapToNearestEdge(leftPx = 800, topPx = 800, screenWidthPx = portraitW, screenHeightPx = portraitH, sizePx = size)

        assertEquals(portraitW - size, snapped.left)
        assertEquals(OverlayPosition.HorizontalEdge.RIGHT, snapped.edge)
    }

    @Test fun `snap at the exact halfway point resolves to left (stable tie-break)`() {
        val leftPx = portraitW / 2 - size / 2 // center exactly on the half
        val snapped = OverlayPosition.snapToNearestEdge(leftPx, 500, portraitW, portraitH, size)

        assertEquals(OverlayPosition.HorizontalEdge.LEFT, snapped.edge)
    }

    @Test fun `degenerate screen dimensions never produce NaN`() {
        val normalized = OverlayPosition.pixelsToNormalized(10, 10, screenWidthPx = 0, screenHeightPx = 0, sizePx = size)
        val px = OverlayPosition.normalizedToPixels(normalized, 0, 0, size)

        assertEquals(0f, normalized.x)
        assertEquals(0f, normalized.y)
        assertEquals(0, px.left)
        assertEquals(0, px.top)
    }

    @Test fun `button larger than the screen still clamps to the origin`() {
        val normalized = OverlayPosition.pixelsToNormalized(50, 50, screenWidthPx = 100, screenHeightPx = 100, sizePx = 150)
        val px = OverlayPosition.normalizedToPixels(normalized, 100, 100, 150)

        assertEquals(0f, normalized.x)
        assertEquals(0f, normalized.y)
        assertEquals(0, px.left)
        assertEquals(0, px.top)
    }


}
