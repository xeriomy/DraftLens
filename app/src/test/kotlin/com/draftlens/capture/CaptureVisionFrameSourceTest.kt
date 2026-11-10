package com.draftlens.capture

import com.draftlens.models.CapturedFrame
import com.draftlens.models.CaptureSessionState
import com.draftlens.models.CaptureStatus
import com.draftlens.vision.model.VisionSourceState
import com.draftlens.vision.testCapturedFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureVisionFrameSourceTest {

    private class Harness(
        val adapter: CaptureVisionFrameSource,
        val frames: MutableStateFlow<CapturedFrame?>,
        val capture: MutableStateFlow<CaptureSessionState>,
    )

    private fun harness(
        frame: CapturedFrame? = testCapturedFrame(),
        status: CaptureStatus = CaptureStatus.CAPTURING,
        scope: CoroutineScope,
    ): Harness {
        val frames = MutableStateFlow(frame)
        val capture = MutableStateFlow(CaptureSessionState(status = status))
        return Harness(CaptureVisionFrameSource(frames, capture, scope), frames, capture)
    }

    @Test fun `adapter maps paused capture to paused vision state`() = runTest {
        val harness = harness(status = CaptureStatus.PAUSED, scope = backgroundScope)
        assertEquals(VisionSourceState.PAUSED, harness.adapter.state.value)
        assertEquals("test-frame", harness.adapter.frames.value?.id?.value)
    }

    @Test fun `adapter maps every capture status to a vision state`() = runTest {
        val expected = mapOf(
            CaptureStatus.IDLE to VisionSourceState.IDLE,
            CaptureStatus.AWAITING_PERMISSION to VisionSourceState.UNKNOWN,
            CaptureStatus.STARTING to VisionSourceState.UNKNOWN,
            CaptureStatus.CAPTURING to VisionSourceState.CAPTURING,
            CaptureStatus.PAUSED to VisionSourceState.PAUSED,
            CaptureStatus.STOPPING to VisionSourceState.STOPPED,
            CaptureStatus.ERROR to VisionSourceState.ERROR,
        )
        val harness = harness(scope = backgroundScope)
        expected.forEach { (captureStatus, visionState) ->
            harness.capture.value = CaptureSessionState(status = captureStatus)
            advanceUntilIdle()
            assertEquals("capture $captureStatus", visionState, harness.adapter.state.value)
        }
    }

    @Test fun `adapter follows later capture state changes`() = runTest {
        val harness = harness(scope = backgroundScope)
        harness.capture.value = CaptureSessionState(status = CaptureStatus.ERROR)
        advanceUntilIdle()
        assertEquals(VisionSourceState.ERROR, harness.adapter.state.value)
    }

    @Test fun `adapter publishes the vision frame of the latest capture frame`() = runTest {
        val harness = harness(scope = backgroundScope)
        assertEquals("test-frame", harness.adapter.frames.value?.id?.value)

        harness.frames.value = null
        advanceUntilIdle()
        assertNull(harness.adapter.frames.value)
    }
}
