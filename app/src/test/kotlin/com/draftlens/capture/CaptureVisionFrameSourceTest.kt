package com.draftlens.capture

import com.draftlens.models.CapturedFrame
import com.draftlens.models.CaptureSessionState
import com.draftlens.models.CaptureStatus
import com.draftlens.vision.model.VisionSourceState
import com.draftlens.vision.testCapturedFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class CaptureVisionFrameSourceTest {

    private lateinit var scope: CoroutineScope

    @Before fun setUp() {
        scope = CoroutineScope(Dispatchers.Unconfined + Job())
    }

    @After fun tearDown() {
        scope.cancel()
    }

    private class Harness(
        val adapter: CaptureVisionFrameSource,
        val frames: MutableStateFlow<CapturedFrame?>,
        val capture: MutableStateFlow<CaptureSessionState>,
    )

    private fun harness(
        frame: CapturedFrame? = testCapturedFrame(),
        status: CaptureStatus = CaptureStatus.CAPTURING,
    ): Harness {
        val frames = MutableStateFlow(frame)
        val capture = MutableStateFlow(CaptureSessionState(status = status))
        return Harness(CaptureVisionFrameSource(frames, capture, scope), frames, capture)
    }

    /** Waits for the adapter to publish [expected]; the state may arrive on another thread. */
    private fun awaitState(adapter: CaptureVisionFrameSource, expected: VisionSourceState) {
        val state = runBlocking {
            withTimeout(AWAIT_TIMEOUT_MS) { adapter.state.first { it == expected } }
        }
        assertEquals(expected, state)
    }

    @Test fun `adapter maps paused capture to paused vision state`() {
        val harness = harness(status = CaptureStatus.PAUSED)
        assertEquals(VisionSourceState.PAUSED, harness.adapter.state.value)
        assertEquals("test-frame", harness.adapter.frames.value?.id?.value)
    }

    @Test fun `adapter maps every capture status to a vision state`() {
        val expected = mapOf(
            CaptureStatus.IDLE to VisionSourceState.IDLE,
            CaptureStatus.AWAITING_PERMISSION to VisionSourceState.UNKNOWN,
            CaptureStatus.STARTING to VisionSourceState.UNKNOWN,
            CaptureStatus.CAPTURING to VisionSourceState.CAPTURING,
            CaptureStatus.PAUSED to VisionSourceState.PAUSED,
            CaptureStatus.STOPPING to VisionSourceState.STOPPED,
            CaptureStatus.ERROR to VisionSourceState.ERROR,
        )
        val harness = harness()
        expected.forEach { (captureStatus, visionState) ->
            harness.capture.value = CaptureSessionState(status = captureStatus)
            awaitState(harness.adapter, visionState)
        }
    }

    @Test fun `adapter follows later capture state changes`() {
        val harness = harness()
        harness.capture.value = CaptureSessionState(status = CaptureStatus.ERROR)
        awaitState(harness.adapter, VisionSourceState.ERROR)
    }

    @Test fun `adapter publishes the vision frame of the latest capture frame`() {
        val harness = harness()
        assertEquals("test-frame", harness.adapter.frames.value?.id?.value)

        harness.frames.value = null
        val cleared = runBlocking {
            withTimeout(AWAIT_TIMEOUT_MS) { harness.adapter.frames.first { it == null } }
        }
        assertNull(cleared)
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 5_000L
    }
}
