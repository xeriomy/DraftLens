package com.draftlens.capture

import com.draftlens.vision.testCapturedFrame
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
    }

    @Test fun `hidden freeze resume visible sequence`() {
        // Capture is running and a valid game frame is published.
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        val frame = testCapturedFrame()
        CaptureStateHolder.recordFrame(frame)

        // MLBB goes hidden (user is in DraftLens): nothing publishes, last valid frame stays.
        policy.onContentVisibilityChanged(false)
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
        policy.onContentVisibilityChanged(true)
        assertTrue(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))

        // Explicit freeze while visible blocks publication again (the round trip both ways).
        CaptureStateHolder.setFrozenFrame(frame)
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = true))
    }

    @Test fun `a paused capture publishes nothing even when the window is visible`() {
        CaptureStateHolder.beginCapture(1080, 2400, 440)
        CaptureStateHolder.recordFrame(testCapturedFrame())
        policy.onContentVisibilityChanged(true)
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
        policy.onContentVisibilityChanged(false)
        CaptureStateHolder.setFrozenFrame(frame)

        policy.onContentVisibilityChanged(true)

        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = true))
        // The pin is untouched; returning to the app shows it, not live frames.
        org.junit.Assert.assertEquals(frame, CaptureStateHolder.frozenFrame.value)
    }
}
