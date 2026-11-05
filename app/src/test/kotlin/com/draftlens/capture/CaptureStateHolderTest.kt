package com.draftlens.capture

import com.draftlens.models.CaptureStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CaptureStateHolderTest {

    @Before fun resetHolder() {
        CaptureStateHolder.reset()
    }

    private fun capturedFrameNullBitmap() =
        com.draftlens.models.CapturedFrame(
            bitmap = null,
            sampledAtElapsedRealtimeMs = 10_000,
            wallClockTimestampMs = System.currentTimeMillis(),
            processingLatencyMs = 12,
            width = 1080,
            height = 2400
        )

    @Test fun `task removal style cleanup clears frame and session metadata while preserving Error`() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        CaptureStateHolder.recordFrame(capturedFrameNullBitmap())
        CaptureStateHolder.setStatus(CaptureStatus.ERROR, "terminated externally")

        CaptureStateHolder.clearLatestFrame()
        CaptureStateHolder.resetSessionMetrics()
        if (CaptureStateHolder.state.value.status != CaptureStatus.ERROR) {
            CaptureStateHolder.setStatus(CaptureStatus.IDLE)
        }

        val state = CaptureStateHolder.state.value
        assertNull(CaptureStateHolder.latestFrame.value)
        assertEquals(CaptureStatus.ERROR, state.status)
        assertEquals("terminated externally", state.lastError)
        assertEquals(0L, state.frameCount)
        assertEquals(0, state.displayWidth)
        assertEquals(0, state.displayHeight)
        assertEquals(0, state.densityDpi)
        assertNull(state.lastFrameWallClockMs)
        assertNull(state.startedAtMs)
    }

    @Test fun `revoked capture clears stale frame and preserves error`() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        CaptureStateHolder.recordFrame(
            com.draftlens.models.CapturedFrame(
                bitmap = null,
                sampledAtElapsedRealtimeMs = 10_000,
                wallClockTimestampMs = System.currentTimeMillis(),
                processingLatencyMs = 12,
                width = 1080,
                height = 2400
            )
        )
        assertNotNull(CaptureStateHolder.latestFrame.value)

        CaptureStateHolder.setStatus(CaptureStatus.ERROR, "consent revoked")
        CaptureStateHolder.clearLatestFrame()

        assertNull(CaptureStateHolder.latestFrame.value)
        assertEquals(CaptureStatus.ERROR, CaptureStateHolder.state.value.status)
        assertEquals("consent revoked", CaptureStateHolder.state.value.lastError)
    }

    @Test fun `repeated cleanup is idempotent and preserves error`() {
        CaptureStateHolder.setStatus(CaptureStatus.ERROR, "cleanup proof")
        CaptureStateHolder.clearLatestFrame()
        CaptureStateHolder.resetSessionMetrics()
        CaptureStateHolder.clearLatestFrame()
        CaptureStateHolder.resetSessionMetrics()

        assertNull(CaptureStateHolder.latestFrame.value)
        assertEquals(CaptureStatus.ERROR, CaptureStateHolder.state.value.status)
        assertEquals("cleanup proof", CaptureStateHolder.state.value.lastError)
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
