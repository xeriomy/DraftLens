package com.draftlens.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the shared LIVE/PAUSED/FROZEN presentation both surfaces (notification, overlay)
 * derive their action from, plus the tap/toggle outcomes.
 */
class PublishStateTest {

    @Test fun `live publishing derives LIVE`() {
        assertEquals(CapturePublishState.LIVE, publishStateFor(pinInPlace = false, isPaused = false))
    }

    @Test fun `paused derives PAUSED`() {
        assertEquals(CapturePublishState.PAUSED, publishStateFor(pinInPlace = false, isPaused = true))
    }

    @Test fun `pinned frame wins over pause for presentation`() {
        assertEquals(CapturePublishState.FROZEN, publishStateFor(pinInPlace = true, isPaused = true))
        assertEquals(CapturePublishState.FROZEN, publishStateFor(pinInPlace = true, isPaused = false))
    }

    @Test fun `live state offers Pause, every other state offers Resume`() {
        assertEquals(CapturePublishAction.PAUSE, publishActionFor(CapturePublishState.LIVE))
        assertEquals(CapturePublishAction.RESUME, publishActionFor(CapturePublishState.PAUSED))
        assertEquals(CapturePublishAction.RESUME, publishActionFor(CapturePublishState.FROZEN))
    }

    @Test fun `overlay tap freezes when live and resumes otherwise`() {
        assertEquals(FloatingTapOutcome.FREEZE, floatingTapFor(CapturePublishState.LIVE))
        assertEquals(FloatingTapOutcome.RESUME, floatingTapFor(CapturePublishState.PAUSED))
        assertEquals(FloatingTapOutcome.RESUME, floatingTapFor(CapturePublishState.FROZEN))
    }

    @Test fun `publication is blocked in both paused and frozen`() {
        assertFalse(publishStateBlocksPublication(CapturePublishState.LIVE))
        assertTrue(publishStateBlocksPublication(CapturePublishState.PAUSED))
        assertTrue(publishStateBlocksPublication(CapturePublishState.FROZEN))
    }
}
