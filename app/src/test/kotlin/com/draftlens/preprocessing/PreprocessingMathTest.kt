package com.draftlens.preprocessing

import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.geometry.PixelRect
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FramePayload
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.PixelFormat
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import com.draftlens.vision.preprocessing.AndroidFramePreprocessor
import com.draftlens.vision.preprocessing.PreprocessFailureCode
import com.draftlens.vision.preprocessing.PreprocessPlanResult
import com.draftlens.vision.preprocessing.PreprocessingMath
import com.draftlens.vision.preprocessing.ProcessFrameRequest
import com.draftlens.vision.preprocessing.ProcessFrameResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreprocessingMathTest {

    @Test fun `valid landscape frame is accepted and preserves aspect ratio`() {
        val frame = syntheticOriginal(width = 1920, height = 1080)
        val plan = PreprocessingMath.plan(frame, ProcessFrameRequest(maxProcessedDimension = 1280))
        assertTrue(plan.isSuccess)
        assertEquals(1280, plan.getOrThrow().outputWidth)
        assertEquals(720, plan.getOrThrow().outputHeight)
    }

    @Test fun `output beyond the int pixel limit is rejected instead of overflowing`() {
        // 100000x100000 source, no allocation: the payload lies about capacity but plan
        // only reads dimensions, so the Int-overflow guard must reject the 50000x50000
        // output (2.5e9 pixels) instead of crashing or wrapping.
        val frame = OriginalFrame(
            id = FrameId("huge-frame"),
            payload = HugePayload(width = 100_000, height = 100_000),
            sourceWidth = 100_000,
            sourceHeight = 100_000,
            displayRotation = FrameRotation.ROTATION_0,
            rotation = FrameRotation.ROTATION_0,
            capturedAtElapsedRealtimeMs = 1_000,
            wallClockTimestampMs = 1_700_000_000_000,
            captureLatencyMs = 2,
        )
        val result = PreprocessingMath.plan(
            frame,
            ProcessFrameRequest(maxProcessedDimension = 50_000, maxPixels = Long.MAX_VALUE),
        )
        assertEquals(
            PreprocessFailureCode.OUTPUT_TOO_LARGE,
            (result as PreprocessPlanResult.Failure).reason.code,
        )
    }

    @Test fun `invalid input reports honest failure`() {
        val frame = OriginalFrame(
            id = FrameId("invalid-frame"),
            payload = PixelArrayFramePayload(1, 100, IntArray(100)),
            sourceWidth = 0,
            sourceHeight = 100,
            displayRotation = FrameRotation.ROTATION_0,
            rotation = FrameRotation.ROTATION_0,
            capturedAtElapsedRealtimeMs = 1_000,
            wallClockTimestampMs = 1_700_000_000_000,
            captureLatencyMs = 2,
        )
        val result = AndroidFramePreprocessor.process(frame, ProcessFrameRequest())
        assertEquals(
            PreprocessFailureCode.INVALID_DIMENSIONS,
            (result as ProcessFrameResult.Failure).reason.code,
        )
    }

    @Test fun `android preprocessor rejects non bitmap payloads explicitly`() {
        val frame = syntheticOriginal(width = 4, height = 4)
        val result = AndroidFramePreprocessor.process(frame, ProcessFrameRequest())
        assertEquals(
            PreprocessFailureCode.UNSUPPORTED_FORMAT,
            (result as ProcessFrameResult.Failure).reason.code,
        )
    }

    @Test fun `payload dimensions must match the source dimensions`() {
        val frame = OriginalFrame(
            id = FrameId("mismatched-frame"),
            payload = PixelArrayFramePayload(9, 10, IntArray(90)),
            sourceWidth = 10,
            sourceHeight = 10,
            displayRotation = FrameRotation.ROTATION_0,
            rotation = FrameRotation.ROTATION_0,
            capturedAtElapsedRealtimeMs = 1_000,
            wallClockTimestampMs = 1_700_000_000_000,
            captureLatencyMs = 2,
        )
        val result = PreprocessingMath.plan(frame, ProcessFrameRequest())
        assertEquals(
            PreprocessFailureCode.PAYLOAD_MISMATCH,
            (result as PreprocessPlanResult.Failure).reason.code,
        )
    }

    @Test fun `source pixel budget is enforced`() {
        val frame = syntheticOriginal(width = 100, height = 100)
        val result = PreprocessingMath.plan(frame, ProcessFrameRequest(maxPixels = 5_000))
        assertEquals(
            PreprocessFailureCode.TOO_MANY_PIXELS,
            (result as PreprocessPlanResult.Failure).reason.code,
        )
    }

    @Test fun `invalid maximum processed dimension is rejected`() {
        val frame = syntheticOriginal(width = 100, height = 100)
        val result = PreprocessingMath.plan(frame, ProcessFrameRequest(maxProcessedDimension = 0))
        assertEquals(
            PreprocessFailureCode.INVALID_DIMENSIONS,
            (result as PreprocessPlanResult.Failure).reason.code,
        )
    }

    @Test fun `non-positive pixel budget is rejected`() {
        val frame = syntheticOriginal(width = 100, height = 100)
        val result = PreprocessingMath.plan(frame, ProcessFrameRequest(maxPixels = 0))
        assertEquals(
            PreprocessFailureCode.INVALID_DIMENSIONS,
            (result as PreprocessPlanResult.Failure).reason.code,
        )
    }

    @Test fun `crop is interpreted in oriented pixels after a quarter turn`() {
        val frame = syntheticOriginal(
            width = 1920,
            height = 1080,
            rotation = FrameRotation.ROTATION_90,
        )
        val plan = PreprocessingMath.plan(
            frame,
            ProcessFrameRequest(
                maxProcessedDimension = 1280,
                crop = NormalizedRect(left = 0f, top = 0.25f, right = 1f, bottom = 0.75f),
            ),
        ).getOrThrow()

        assertEquals(1080, plan.orientedWidth)
        assertEquals(1920, plan.orientedHeight)
        assertEquals(PixelRect(0, 480, 1080, 1440), plan.cropPixelRect)
        assertEquals(1080, plan.outputWidth)
        assertEquals(960, plan.outputHeight)
    }

    @Test fun `small frames are never upscaled`() {
        val frame = syntheticOriginal(width = 320, height = 200)
        val plan = PreprocessingMath.plan(frame, ProcessFrameRequest(maxProcessedDimension = 1280)).getOrThrow()
        assertEquals(320, plan.outputWidth)
        assertEquals(200, plan.outputHeight)
    }
}

/** Capacity-lying payload for plan-only checks; never allocates pixel data. */
private class HugePayload(
    override val width: Int,
    override val height: Int,
) : FramePayload {
    override val format: PixelFormat = PixelFormat.ARGB_8888
    override val byteCountEstimate: Long = width.toLong() * height.toLong() * 4
}

internal fun syntheticOriginal(
    width: Int,
    height: Int,
    rotation: FrameRotation = FrameRotation.ROTATION_0,
    pixels: IntArray = IntArray(width * height) { it },
): OriginalFrame = OriginalFrame(
    id = FrameId("source-frame"),
    payload = PixelArrayFramePayload(width, height, pixels),
    sourceWidth = width,
    sourceHeight = height,
    displayRotation = FrameRotation.ROTATION_90,
    rotation = rotation,
    capturedAtElapsedRealtimeMs = 5_000,
    wallClockTimestampMs = 1_700_000_000_000,
    captureLatencyMs = 3,
)
