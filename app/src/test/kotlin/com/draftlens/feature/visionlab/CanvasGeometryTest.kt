package com.draftlens.feature.visionlab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CanvasGeometryTest {

    private fun frame(width: Int = 1000, height: Int = 500) = OriginalFrame(
        id = FrameId("canvas-test"),
        payload = PixelArrayFramePayload(width, height, IntArray(width * height)),
        sourceWidth = width,
        sourceHeight = height,
        displayRotation = FrameRotation.ROTATION_0,
        rotation = FrameRotation.ROTATION_0,
        capturedAtElapsedRealtimeMs = 1_000,
        wallClockTimestampMs = 1_700_000_000_000,
        captureLatencyMs = 1,
    )

    /** A 1000x500 image inside a 1000x1000 canvas leaves 250px letterbox bands top and bottom. */
    private val geometry = CanvasGeometry.of(frame())
    private val fitted = fittedImageRect(Size(1000f, 1000f), geometry.orientedWidth, geometry.orientedHeight)

    @Test fun `a point inside the image is accepted`() {
        val point = geometry.canvasToNormalized(Offset(500f, 500f), fitted)
        assertNotNull(point)
        assertEquals(0.5f, point!!.x, 0.001f)
        assertEquals(0.5f, point.y, 0.001f)
    }

    @Test fun `a point in the top letterbox is rejected`() {
        assertNull(geometry.canvasToNormalized(Offset(500f, 100f), fitted))
    }

    @Test fun `a point in the bottom letterbox is rejected`() {
        assertNull(geometry.canvasToNormalized(Offset(500f, 900f), fitted))
    }

    @Test fun `a point left of the image is rejected`() {
        assertNull(geometry.canvasToNormalized(Offset(-10f, 500f), fitted))
    }

    @Test fun `a point right of the image is rejected`() {
        assertNull(geometry.canvasToNormalized(Offset(1010f, 500f), fitted))
    }

    @Test fun `the image edges themselves are accepted`() {
        assertNotNull(geometry.canvasToNormalized(Offset(0f, 250f), fitted))
        assertNotNull(geometry.canvasToNormalized(Offset(1000f, 750f), fitted))
    }
}
