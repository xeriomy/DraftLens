package com.draftlens.capture

import com.draftlens.vision.testCapturedFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * End-to-end JVM sequence for the app-only capture workflow, at the level the JVM can
 * reach: the pure visibility gate plus the process-local holder. The ImageReader half of
 * the gate lives in FramePipeline (Android-only) and is covered on-device.
 */
class VisibilityFreezeSequenceTest {

    private lateinit var policy: CaptureVisibilityPolicy

    @Before fun setUp() {
        CaptureStateHolder.reset()
        policy = CaptureVisibilityPolicy()
        policy.resetForNewSession(3)
    }

    @Test fun `hidden freeze resume visible sequence`() {
        // Capture is running and a valid game frame is published.
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        val frame = testCapturedFrame()
        CaptureStateHolder.recordFrame(frame)

        // MLBB goes hidden (user is in DraftLens): nothing publishes, last valid frame stays.
        policy.onContentVisibilityChanged(true, 3)
        policy.onContentVisibilityChanged(false, 3)
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))
        assertTrue(CaptureStateHolder.latestFrame.value === frame)

        // User freezes from the floating button while hidden: pin kept independently, still not published.
        CaptureStateHolder.setFrozenFrame(frame)
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = true))

        // User taps Resume while hidden: the intent flips to LIVE, but publication stays
        // gated until the window returns — no black/DraftLens frames may be shown.
        CaptureStateHolder.clearFrozenFrame()
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))

        // MLBB returns: the remembered live intent takes effect, exactly once visibility flips.
        policy.onContentVisibilityChanged(true, 3)
        assertTrue(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))

        // Explicit freeze while visible blocks publication again (the round trip both ways).
        CaptureStateHolder.setFrozenFrame(frame)
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = true))
    }

    @Test fun `a paused capture publishes nothing even when the window is visible`() {
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        CaptureStateHolder.recordFrame(testCapturedFrame())
        policy.onContentVisibilityChanged(true, 3)
        CaptureStateHolder.setPaused(true)

        // isActive() is false while PAUSED; publication stays blocked while the window is visible.
        assertFalse(policy.shouldPublish(isActive = false, generationCurrent = true, frozen = false))

        CaptureStateHolder.setPaused(false)
        assertTrue(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))
    }

    @Test fun `an explicitly frozen session does not auto-resume on visibility`() {
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        val frame = testCapturedFrame()
        CaptureStateHolder.recordFrame(frame)
        policy.onContentVisibilityChanged(true, 3)
        policy.onContentVisibilityChanged(false, 3)
        CaptureStateHolder.setFrozenFrame(frame)

        policy.onContentVisibilityChanged(true, 3)

        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = true))
        // The pin is untouched; returning to the app shows it, not live frames.
        org.junit.Assert.assertEquals(frame, CaptureStateHolder.frozenFrame.value)
    }

    @Test fun `early hidden callback does not block the first frame but blocks replacement afterwards`() {
        // The user started capture from DraftLens: the first callback can be false while the
        // selected MLBB window is not yet on screen. That must not block the bootstrap frame.
        val early = policy.onContentVisibilityChanged(false, 3)
        assertEquals(CapturedContentVisibility.UNKNOWN, early.current)
        assertTrue(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))

        policy.onFramePublished()
        assertEquals(CapturedContentVisibility.VISIBLE, policy.currentState())

        policy.onContentVisibilityChanged(false, 3)
        assertFalse("after a valid frame, HIDDEN must protect it", policy.shouldPublish(true, true, false))
    }

    @Test fun `hidden then paused then visible stays paused and keeps the pinned frame`() {
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        val frame = testCapturedFrame()
        CaptureStateHolder.recordFrame(frame)
        policy.onContentVisibilityChanged(true, 3)
        policy.onContentVisibilityChanged(false, 3)
        CaptureStateHolder.setFrozenFrame(frame)
        CaptureStateHolder.setPaused(true)

        policy.onContentVisibilityChanged(true, 3)

        assertFalse(policy.shouldPublish(isActive = false, generationCurrent = true, frozen = true))
        assertEquals(frame, CaptureStateHolder.frozenFrame.value)
        assertTrue(CaptureStateHolder.state.value.isPaused)
    }
}
