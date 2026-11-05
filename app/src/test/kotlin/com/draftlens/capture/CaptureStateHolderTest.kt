package com.draftlens.capture

import com.draftlens.models.CaptureStatus
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CaptureStateHolderTest {

    @Before fun resetHolder() {
        CaptureStateHolder.reset()
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
        assertNull(state.lastFrameTimestampMs)
        assertNull(CaptureStateHolder.latestFrame.value)
    }
}
