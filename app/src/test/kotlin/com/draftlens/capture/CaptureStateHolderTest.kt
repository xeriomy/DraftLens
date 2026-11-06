package com.draftlens.capture

import com.draftlens.models.CaptureStatus
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.testCapturedFrame
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CaptureStateHolderTest {

    @Before fun resetHolder() {
        CaptureStateHolder.reset()
    }

    @Test fun `termination without error returns to IDLE and clears permission`() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        CaptureStateHolder.recordFrame(testCapturedFrame())

        CaptureStateHolder.terminateSession()

        val state = CaptureStateHolder.state.value
        assertEquals(CaptureStatus.IDLE, state.status)
        assertNull(CaptureStateHolder.latestFrame.value)
        assertFalse(state.hasPermission)
        assertEquals(0L, state.frameCount)
        assertEquals(0, state.displayWidth)
    }

    @Test fun `resetSessionMetrics clears permission metadata while preserving Error`() {
        CaptureStateHolder.setStatus(CaptureStatus.ERROR, "permission cleanup")
        CaptureStateHolder.resetSessionMetrics()

        val state = CaptureStateHolder.state.value
        assertEquals(CaptureStatus.ERROR, state.status)
        assertFalse(state.hasPermission)
    }

    @Test fun `recordFrame forwards non-null vision frame metadata`() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        val frame = testCapturedFrame()

        CaptureStateHolder.recordFrame(frame)

        assertSame(frame, CaptureStateHolder.latestFrame.value)
        assertSame(frame.frame, frame.toVisionFrame())
        assertEquals(2, frame.width)
        assertEquals(2, frame.height)
        assertEquals(10_000, frame.sampledAtElapsedRealtimeMs)
        assertEquals(1_700_000_000_000, frame.wallClockTimestampMs)
        assertEquals(12, frame.processingLatencyMs)
        assertEquals(FrameRotation.ROTATION_90, frame.displayRotation)

        val state = CaptureStateHolder.state.value
        assertEquals(1L, state.frameCount)
        assertEquals(1_700_000_000_000, state.lastFrameWallClockMs)
        assertEquals(12L, state.lastFrameProcessingLatencyMs)
        assertEquals(2, state.displayWidth)
        assertEquals(2, state.displayHeight)
    }

    @Test fun `termination with error preserves message and clears retained frame`() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        CaptureStateHolder.recordFrame(testCapturedFrame())

        CaptureStateHolder.terminateSession("consent revoked")

        val state = CaptureStateHolder.state.value
        assertNull(CaptureStateHolder.latestFrame.value)
        assertEquals(CaptureStatus.ERROR, state.status)
        assertEquals("consent revoked", state.lastError)
        assertFalse(state.hasPermission)
        assertEquals(0L, state.frameCount)
        assertEquals(0, state.displayWidth)
        assertNull(state.lastFrameWallClockMs)
    }

    @Test fun `termination keeps pre-existing error`() {
        CaptureStateHolder.setStatus(CaptureStatus.ERROR, "original failure")
        CaptureStateHolder.terminateSession("system stopped")

        val state = CaptureStateHolder.state.value
        assertEquals(CaptureStatus.ERROR, state.status)
        assertEquals("original failure", state.lastError)
    }

    @Test fun `explicit stop forces idle even after error`() {
        CaptureStateHolder.setStatus(CaptureStatus.ERROR, "original failure")
        CaptureStateHolder.terminateSession(forceIdle = true)

        val state = CaptureStateHolder.state.value
        assertEquals(CaptureStatus.IDLE, state.status)
        assertNull(state.lastError)
        assertFalse(state.hasPermission)
    }

    @Test fun `revoked capture clears retained frame and preserves error`() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        val frame = testCapturedFrame()
        CaptureStateHolder.recordFrame(frame)
        assertSame(frame, CaptureStateHolder.latestFrame.value)

        CaptureStateHolder.terminateSession("consent revoked")

        assertNull(CaptureStateHolder.latestFrame.value)
        assertEquals(CaptureStatus.ERROR, CaptureStateHolder.state.value.status)
        assertEquals("consent revoked", CaptureStateHolder.state.value.lastError)
    }

    @Test fun `repeated cleanup is idempotent and preserves error`() {
        CaptureStateHolder.setStatus(CaptureStatus.ERROR, "cleanup proof")
        CaptureStateHolder.terminateSession("cleanup stop")
        CaptureStateHolder.terminateSession("cleanup stop again")

        val state = CaptureStateHolder.state.value
        assertEquals(CaptureStatus.ERROR, state.status)
        assertEquals("cleanup proof", state.lastError)
    }

    @Test fun `fresh session after termination starts from fresh state`() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        CaptureStateHolder.recordFrame(testCapturedFrame())

        CaptureStateHolder.terminateSession()

        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)

        val state = CaptureStateHolder.state.value
        assertEquals(CaptureStatus.CAPTURING, state.status)
        assertEquals(0L, state.frameCount)
        assertNull(state.lastFrameWallClockMs)
        assertNull(CaptureStateHolder.latestFrame.value)
        assertTrue(state.hasPermission)
    }

    @Test fun `clearing frame does not reset metrics or state`() {
        CaptureStateHolder.setStatus(CaptureStatus.ERROR, "startup failed")
        CaptureStateHolder.clearLatestFrame()

        assertEquals(CaptureStatus.ERROR, CaptureStateHolder.state.value.status)
        assertEquals("startup failed", CaptureStateHolder.state.value.lastError)
    }

    @Test fun `resetSessionMetrics clears only counters and timing`() {
        CaptureStateHolder.setStatus(CaptureStatus.ERROR, "some error")
        CaptureStateHolder.resetSessionMetrics()

        val state = CaptureStateHolder.state.value
        assertEquals(CaptureStatus.ERROR, state.status)
        assertEquals("some error", state.lastError)
        assertEquals(0L, state.frameCount)
        assertNull(state.lastFrameWallClockMs)
        assertNull(state.lastFrameProcessingLatencyMs)
    }

    @Test fun `normal stop and subsequent fresh session start from idle`() {
        CaptureStateHolder.reset()
        assertEquals(CaptureStatus.IDLE, CaptureStateHolder.state.value.status)
        assertNull(CaptureStateHolder.latestFrame.value)

        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)

        val state = CaptureStateHolder.state.value
        assertEquals(CaptureStatus.CAPTURING, state.status)
        assertEquals(0L, state.frameCount)
        assertNull(state.lastFrameWallClockMs)
    }

    @Test fun `reset returns holder to idle`() {
        CaptureStateHolder.reset()
        assertEquals(CaptureStatus.IDLE, CaptureStateHolder.state.value.status)
        assertNull(CaptureStateHolder.latestFrame.value)
    }

    @Test fun `requested to granted flow reaches capturing`() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        val state = CaptureStateHolder.state.value
        assertEquals(CaptureStatus.CAPTURING, state.status)
        assertTrue(state.hasPermission)
        assertEquals(1080, state.displayWidth)
        assertEquals(440, state.densityDpi)
    }

    @Test fun `pause and resume toggle holder state`() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        CaptureStateHolder.setPaused(true)
        assertEquals(CaptureStatus.PAUSED, CaptureStateHolder.state.value.status)
        assertTrue(CaptureStateHolder.state.value.isPaused)
        CaptureStateHolder.setPaused(false)
        assertEquals(CaptureStatus.CAPTURING, CaptureStateHolder.state.value.status)
        assertFalse(CaptureStateHolder.state.value.isPaused)
    }

    @Test fun `reset clears capture metrics so a fresh session can start`() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        CaptureStateHolder.reset()

        val state = CaptureStateHolder.state.value
        assertEquals(CaptureStatus.IDLE, state.status)
        assertEquals(0L, state.frameCount)
        assertNull(state.lastFrameWallClockMs)
        assertNull(CaptureStateHolder.latestFrame.value)
    }
}
