package com.draftlens.preprocessing

import com.draftlens.vision.geometry.FloatPoint
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FramePayload
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import com.draftlens.vision.model.PixelFormat
import com.draftlens.vision.preprocessing.PixelArrayFramePreprocessor
import com.draftlens.vision.preprocessing.PreprocessFailureCode
import com.draftlens.vision.preprocessing.ProcessFrameRequest
import com.draftlens.vision.preprocessing.ProcessFrameResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelArrayFramePreprocessorTest {

    @Test fun `90 degree correction swaps output axes without stretching`() {
        val frame = syntheticOriginal(
            width = 1920,
            height = 1080,
            rotation = FrameRotation.ROTATION_90,
        )
        val result = PixelArrayFramePreprocessor.process(frame, ProcessFrameRequest(maxProcessedDimension = 500))
        val success = result as ProcessFrameResult.Success
        assertEquals(281, success.frame.payload.width)
        assertEquals(500, success.frame.payload.height)
    }

    @Test fun `identity path reuses the source payload reference`() {
        val frame = syntheticOriginal(width = 4, height = 3)
        val result = PixelArrayFramePreprocessor.process(frame, ProcessFrameRequest())
        val success = result as ProcessFrameResult.Success

        assertSame(frame.payload, success.frame.payload)
        assertEquals(1f, success.frame.transform.scale, 0f)
        val mapped = success.frame.transform.sourceToProcessed(FloatPoint(2.5f, 1.5f))
        assertEquals(2.5f, mapped.x, 0.0001f)
        assertEquals(1.5f, mapped.y, 0.0001f)
        assertEquals(frame.id, success.frame.sourceId)
        assertEquals(4, success.frame.sourceWidth)
        assertEquals(3, success.frame.sourceHeight)
        assertEquals(FrameRotation.ROTATION_90, success.frame.displayRotation)
        assertEquals(FrameRotation.ROTATION_0, success.frame.rotation)
    }

    @Test fun `rotation is applied clockwise to pixels`() {
        val frame = syntheticOriginal(
            width = 2,
            height = 2,
            rotation = FrameRotation.ROTATION_90,
            pixels = intArrayOf(0, 1, 2, 3),
        )
        val result = PixelArrayFramePreprocessor.process(frame, ProcessFrameRequest()) as ProcessFrameResult.Success
        val payload = result.frame.payload as PixelArrayFramePayload
        assertEquals(2, payload.width)
        assertEquals(2, payload.height)
        assertEquals(listOf(2, 0, 3, 1), payload.pixels().toList())
    }

    @Test fun `crop samples normalized oriented coordinates`() {
        val frame = syntheticOriginal(width = 4, height = 4, pixels = IntArray(16) { it })
        val result = PixelArrayFramePreprocessor.process(
            frame,
            ProcessFrameRequest(crop = NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f)),
        ) as ProcessFrameResult.Success
        val payload = result.frame.payload as PixelArrayFramePayload

        assertEquals(2, payload.width)
        assertEquals(2, payload.height)
        assertEquals(listOf(5, 6, 9, 10), payload.pixels().toList())
    }

    @Test fun `downscale uses nearest neighbor sampling`() {
        val frame = syntheticOriginal(width = 4, height = 4, pixels = IntArray(16) { it })
        val result = PixelArrayFramePreprocessor.process(
            frame,
            ProcessFrameRequest(maxProcessedDimension = 2),
        ) as ProcessFrameResult.Success
        val payload = result.frame.payload as PixelArrayFramePayload

        assertEquals(2, payload.width)
        assertEquals(2, payload.height)
        assertEquals(listOf(5, 7, 13, 15), payload.pixels().toList())
    }

    @Test fun `non pixel array payload is unsupported by the pure Kotlin renderer`() {
        val frame = OriginalFrame(
            id = FrameId("foreign-frame"),
            payload = ForeignPayload(2, 2),
            sourceWidth = 2,
            sourceHeight = 2,
            displayRotation = FrameRotation.ROTATION_0,
            rotation = FrameRotation.ROTATION_0,
            capturedAtElapsedRealtimeMs = 1_000,
            wallClockTimestampMs = 1_700_000_000_000,
            captureLatencyMs = 2,
        )
        val result = PixelArrayFramePreprocessor.process(frame, ProcessFrameRequest())
        assertEquals(
            PreprocessFailureCode.UNSUPPORTED_FORMAT,
            (result as ProcessFrameResult.Failure).reason.code,
        )
    }

    @Test fun `render failure is reported as RENDER_FAILED instead of throwing`() {
        PixelArrayFramePreprocessor.renderHook = { _, _ ->
            throw IllegalStateException("forced render failure")
        }
        try {
            val frame = syntheticOriginal(
                width = 2,
                height = 2,
                rotation = FrameRotation.ROTATION_90,
                pixels = intArrayOf(0, 1, 2, 3),
            )
            val result = PixelArrayFramePreprocessor.process(frame, ProcessFrameRequest())
            val failure = result as ProcessFrameResult.Failure
            assertEquals(PreprocessFailureCode.RENDER_FAILED, failure.reason.code)
            assertTrue(
                failure.reason.message.contains("forced render failure"),
            )
        } finally {
            PixelArrayFramePreprocessor.renderHook = null
        }
    }

    private class ForeignPayload(
        override val width: Int,
        override val height: Int,
    ) : FramePayload {
        override val format: PixelFormat = PixelFormat.ARGB_8888
        override val byteCountEstimate: Long = width.toLong() * height.toLong() * 4
    }
}
