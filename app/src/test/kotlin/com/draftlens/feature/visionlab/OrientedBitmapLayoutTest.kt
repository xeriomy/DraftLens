package com.draftlens.feature.visionlab

import androidx.compose.ui.geometry.Size
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import org.junit.Assert.assertEquals
import org.junit.Test

class OrientedBitmapLayoutTest {

    private fun frame(width: Int, height: Int, rotation: FrameRotation = FrameRotation.ROTATION_0) = OriginalFrame(
        id = FrameId("layout-test"),
        payload = PixelArrayFramePayload(width, height, IntArray(width * height)),
        sourceWidth = width,
        sourceHeight = height,
        displayRotation = rotation,
        rotation = rotation,
        capturedAtElapsedRealtimeMs = 1_000,
        wallClockTimestampMs = 1_700_000_000_000,
        captureLatencyMs = 1,
    )

    private fun orientedSize(width: Int, height: Int, rotation: FrameRotation): Pair<Int, Int> =
        if (rotation == FrameRotation.ROTATION_90 || rotation == FrameRotation.ROTATION_270) {
            height to width
        } else {
            width to height
        }

    @Test fun `rendered bounds match the fitted rectangle for every rotation`() {
        val source = 1000 to 500
        listOf(
            FrameRotation.ROTATION_0,
            FrameRotation.ROTATION_90,
            FrameRotation.ROTATION_180,
            FrameRotation.ROTATION_270,
        ).forEach { rotation ->
            val oriented = orientedSize(source.first, source.second, rotation)
            val fitted = fittedImageRect(Size(800f, 800f), oriented.first, oriented.second)
            val layout = OrientedBitmapLayout.of(source.first, source.second, rotation, fitted)

            assertEquals("left ${rotation.name}", fitted.left, layout.rotatedBounds.left, 0.01f)
            assertEquals("top ${rotation.name}", fitted.top, layout.rotatedBounds.top, 0.01f)
            assertEquals("right ${rotation.name}", fitted.right, layout.rotatedBounds.right, 0.01f)
            assertEquals("bottom ${rotation.name}", fitted.bottom, layout.rotatedBounds.bottom, 0.01f)
        }
    }

    @Test fun `the bitmap is scaled uniformly and never stretched`() {
        val source = 1000 to 500
        listOf(
            FrameRotation.ROTATION_0,
            FrameRotation.ROTATION_90,
            FrameRotation.ROTATION_180,
            FrameRotation.ROTATION_270,
        ).forEach { rotation ->
            val oriented = orientedSize(source.first, source.second, rotation)
            val fitted = fittedImageRect(Size(800f, 800f), oriented.first, oriented.second)
            val layout = OrientedBitmapLayout.of(source.first, source.second, rotation, fitted)

            val drawnAspect = layout.destination.width / layout.destination.height
            val sourceAspect = source.first.toFloat() / source.second
            assertEquals("aspect ${rotation.name}", sourceAspect, drawnAspect, 0.001f)
        }
    }

    @Test fun `a portrait frame maps its image edges onto the workspace edges`() {
        val workspace = Size(540f, 960f)
        val fitted = fittedImageRect(workspace, 1080, 1920)

        assertEquals(0f, fitted.left, 0.01f)
        assertEquals(0f, fitted.top, 0.01f)
        assertEquals(540f, fitted.right, 0.01f)
        assertEquals(960f, fitted.bottom, 0.01f)

        val layout = OrientedBitmapLayout.of(1080, 1920, FrameRotation.ROTATION_0, fitted)
        assertEquals(fitted.left, layout.rotatedBounds.left, 0.01f)
        assertEquals(fitted.top, layout.rotatedBounds.top, 0.01f)
        assertEquals(fitted.right, layout.rotatedBounds.right, 0.01f)
        assertEquals(fitted.bottom, layout.rotatedBounds.bottom, 0.01f)
    }

    @Test fun `a landscape frame maps its image edges onto the workspace edges`() {
        val workspace = Size(960f, 540f)
        val fitted = fittedImageRect(workspace, 1920, 1080)

        assertEquals(0f, fitted.left, 0.01f)
        assertEquals(0f, fitted.top, 0.01f)
        assertEquals(960f, fitted.right, 0.01f)
        assertEquals(540f, fitted.bottom, 0.01f)

        val layout = OrientedBitmapLayout.of(1920, 1080, FrameRotation.ROTATION_0, fitted)
        assertEquals(fitted.left, layout.rotatedBounds.left, 0.01f)
        assertEquals(fitted.top, layout.rotatedBounds.top, 0.01f)
        assertEquals(fitted.right, layout.rotatedBounds.right, 0.01f)
        assertEquals(fitted.bottom, layout.rotatedBounds.bottom, 0.01f)
    }

    @Test fun `the overlay of the whole frame aligns with the rendered bitmap bounds`() {
        listOf(
            FrameRotation.ROTATION_0,
            FrameRotation.ROTATION_90,
            FrameRotation.ROTATION_180,
            FrameRotation.ROTATION_270,
        ).forEach { rotation ->
            val source = 1000 to 500
            val oriented = orientedSize(source.first, source.second, rotation)
            val fitted = fittedImageRect(Size(800f, 800f), oriented.first, oriented.second)
            val geometry = CanvasGeometry.of(frame(source.first, source.second, rotation))
            val layout = OrientedBitmapLayout.of(source.first, source.second, rotation, fitted)

            val overlay = geometry.normalizedToCanvas(NormalizedRect(0f, 0f, 1f, 1f), fitted)

            assertEquals("left ${rotation.name}", layout.rotatedBounds.left, overlay.left, 0.01f)
            assertEquals("top ${rotation.name}", layout.rotatedBounds.top, overlay.top, 0.01f)
            assertEquals("right ${rotation.name}", layout.rotatedBounds.right, overlay.right, 0.01f)
            assertEquals("bottom ${rotation.name}", layout.rotatedBounds.bottom, overlay.bottom, 0.01f)
        }
    }

    @Test fun `the bitmap is drawn around the centre of the fitted rectangle`() {
        val fitted = fittedImageRect(Size(800f, 800f), 1000, 500)
        val layout = OrientedBitmapLayout.of(1000, 500, FrameRotation.ROTATION_0, fitted)

        assertEquals(fitted.center.x, layout.pivot.x, 0.01f)
        assertEquals(fitted.center.y, layout.pivot.y, 0.01f)
        assertEquals(fitted.center.x, layout.destination.center.x, 0.01f)
        assertEquals(fitted.center.y, layout.destination.center.y, 0.01f)
    }
}
