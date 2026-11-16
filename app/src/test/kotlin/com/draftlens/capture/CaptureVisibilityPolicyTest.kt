package com.draftlens.capture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CaptureVisibilityPolicyTest {

    private lateinit var policy: CaptureVisibilityPolicy

    @Before fun setUp() {
        policy = CaptureVisibilityPolicy()
    }

    @Test fun `starts visible because full-display projections never report hidden`() {
        assertTrue(policy.isContentVisible())
    }

    @Test fun `visibility false stops publication and reports the transition`() {
        val transition = policy.onContentVisibilityChanged(false)

        assertTrue(transition.changed)
        assertTrue(transition.wasVisible)
        assertFalse(transition.isVisible)
        assertFalse(policy.isContentVisible())
    }

    @Test fun `duplicate hidden callback reports no change`() {
        policy.onContentVisibilityChanged(false)

        val transition = policy.onContentVisibilityChanged(false)

        assertFalse(transition.changed)
    }

    @Test fun `visibility true restores publication when not frozen`() {
        policy.onContentVisibilityChanged(false)
        policy.onContentVisibilityChanged(true)

        assertTrue(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))
    }

    @Test fun `hidden window publishes nothing even when not frozen`() {
        policy.onContentVisibilityChanged(false)

        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))
    }

    @Test fun `frozen window publishes nothing even when visible`() {
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = true))
    }

    @Test fun `stale generation or stopped session publishes nothing regardless of visibility`() {
        assertFalse(policy.shouldPublish(isActive = false, generationCurrent = true, frozen = false))
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = false, frozen = false))
    }

    @Test fun `resume requested while hidden is remembered and flows when visible again`() {
        // User pinned a frame, then tapped Resume while MLBB was backgrounded: the pin is
        // cleared (frozen = false) but publication must stay gated until the window returns.
        policy.onContentVisibilityChanged(false)

        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))

        policy.onContentVisibilityChanged(true)
        assertTrue(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))
    }

    @Test fun `freeze while hidden keeps publication blocked after the window returns`() {
        policy.onContentVisibilityChanged(false)
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = true))
        policy.onContentVisibilityChanged(true)
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = true))
    }
}
