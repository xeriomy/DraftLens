package com.draftlens.models

import org.junit.Assert.*
import org.junit.Test

class CaptureSessionStateTest {

    @Test fun `idle to awaiting permission is allowed`() {
        assertTrue(CaptureTransitions.canTransition(CaptureStatus.IDLE, CaptureStatus.AWAITING_PERMISSION))
    }

    @Test fun `capturing to paused is allowed`() {
        assertTrue(CaptureTransitions.canTransition(CaptureStatus.CAPTURING, CaptureStatus.PAUSED))
    }

    @Test fun `paused to capturing is allowed`() {
        assertTrue(CaptureTransitions.canTransition(CaptureStatus.PAUSED, CaptureStatus.CAPTURING))
    }

    @Test fun `idle to capturing directly is forbidden`() {
        assertFalse(CaptureTransitions.canTransition(CaptureStatus.IDLE, CaptureStatus.CAPTURING))
    }

    @Test fun `transition preserves frame count`() {
        val s = CaptureSessionState(status = CaptureStatus.CAPTURING, frameCount = 42)
        val next = CaptureTransitions.transition(s, CaptureStatus.PAUSED)
        assertEquals(42, next.frameCount)
        assertEquals(CaptureStatus.PAUSED, next.status)
        assertTrue(next.isPaused)
    }

    @Test fun `transition to capturing clears paused`() {
        val s = CaptureSessionState(status = CaptureStatus.PAUSED, isPaused = true)
        val next = CaptureTransitions.transition(s, CaptureStatus.CAPTURING)
        assertFalse(next.isPaused)
    }

    @Test fun `error transition carries message`() {
        val s = CaptureSessionState(status = CaptureStatus.CAPTURING)
        val next = CaptureTransitions.transition(s, CaptureStatus.ERROR, "revoked")
        assertEquals("revoked", next.lastError)
    }

    @Test fun `illegal transition throws`() {
        val s = CaptureSessionState(status = CaptureStatus.IDLE)
        try {
            CaptureTransitions.transition(s, CaptureStatus.PAUSED)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun `isCapturing true in capturing and paused`() {
        assertTrue(CaptureSessionState(status = CaptureStatus.CAPTURING).isCapturing)
        assertTrue(CaptureSessionState(status = CaptureStatus.PAUSED).isCapturing)
        assertFalse(CaptureSessionState(status = CaptureStatus.IDLE).isCapturing)
        assertFalse(CaptureSessionState(status = CaptureStatus.ERROR).isCapturing)
    }

    @Test fun `start is blocking while capture has not cleanly ended`() {
        assertTrue(CaptureSessionState(status = CaptureStatus.AWAITING_PERMISSION).isStartBlocking)
        assertTrue(CaptureSessionState(status = CaptureStatus.STARTING).isStartBlocking)
        assertTrue(CaptureSessionState(status = CaptureStatus.CAPTURING).isStartBlocking)
        assertTrue(CaptureSessionState(status = CaptureStatus.PAUSED).isStartBlocking)
        assertTrue(CaptureSessionState(status = CaptureStatus.STOPPING).isStartBlocking)
        assertFalse(CaptureSessionState(status = CaptureStatus.IDLE).isStartBlocking)
        assertFalse(CaptureSessionState(status = CaptureStatus.ERROR).isStartBlocking)
    }
}
