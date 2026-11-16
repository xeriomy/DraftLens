package com.draftlens.models

import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import org.junit.Assert.assertEquals
import org.junit.Test

private fun frame(width: Int, height: Int, rotation: FrameRotation, pixels: IntArray) = OriginalFrame(
    id = FrameId("frame-$rotation"),
    payload = PixelArrayFramePayload(width, height, pixels),
    sourceWidth = width,
    sourceHeight = height,
    displayRotation = FrameRotation.ROTATION_90,
    rotation = rotation,
    capturedAtElapsedRealtimeMs = 11_000,
    wallClockTimestampMs = 1_700_000_000_000,
    captureLatencyMs = 7,
)

class FrameSnapshotsTest {

    @Test fun `the copy preserves dimensions rotation and timestamps exactly`() {
        val pixels = IntArray(6) { it + 1 }
        val source = frame(3, 2, FrameRotation.ROTATION_90, pixels)

        val copy = FrameSnapshots.copyOf(source)!!

        assertEquals(source.sourceWidth, copy.sourceWidth)
        assertEquals(source.sourceHeight, copy.sourceHeight)
        assertEquals(source.displayRotation, copy.displayRotation)
        assertEquals(source.rotation, copy.rotation)
        assertEquals(source.capturedAtElapsedRealtimeMs, copy.capturedAtElapsedRealtimeMs)
        assertEquals(source.wallClockTimestampMs, copy.wallClockTimestampMs)
        assertEquals(source.captureLatencyMs, copy.captureLatencyMs)
        assertEquals(source.id, copy.id)
    }

    @Test fun `the copy carries equal pixel values to the source`() {
        val pixels = IntArray(6) { it + 1 }
        val source = frame(3, 2, FrameRotation.ROTATION_0, pixels)

        val copy = FrameSnapshots.copyOf(source)!!

        val sourcePixels = (source.payload as PixelArrayFramePayload).pixels()
        val copyPixels = (copy.payload as PixelArrayFramePayload).pixels()
        assertEquals(sourcePixels.toList(), copyPixels.toList())
    }

    @Test fun `the copy does not change when the caller mutates the array it was built from`() {
        val pixels = IntArray(4) { it + 1 }
        val source = frame(2, 2, FrameRotation.ROTATION_0, pixels)

        val copy = FrameSnapshots.copyOf(source)!!
        pixels[0] = -1

        assertEquals(1, (copy.payload as PixelArrayFramePayload).pixels()[0])
    }

    @Test fun `the copy preserves capture latency metadata via the captured frame wrapper`() {
        val source = frame(2, 2, FrameRotation.ROTATION_0, intArrayOf(1, 2, 3, 4))
        val captured = CapturedFrame(source, processingLatencyMs = 42)

        val copy = FrameSnapshots.copyOf(captured)!!

        assertEquals(42L, copy.processingLatencyMs)
        assertEquals(1, (copy.frame.payload as PixelArrayFramePayload).pixels().first())
    }
}
