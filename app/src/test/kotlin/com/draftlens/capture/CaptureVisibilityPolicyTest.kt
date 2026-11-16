package com.draftlens.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CaptureVisibilityPolicyTest {

    private lateinit var policy: CaptureVisibilityPolicy

    @Before fun setUp() {
        policy = CaptureVisibilityPolicy()
        policy.resetForNewSession(7)
    }

    @Test fun `a session starts UNKNOWN and UNKNOWN deliberately allows publication`() {
        assertEquals(CapturedContentVisibility.UNKNOWN, policy.currentState())
        assertTrue(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))
    }

    @Test fun `a true callback before the pipeline exists is preserved as VISIBLE`() {
        val transition = policy.onContentVisibilityChanged(true, sessionId = 7)

        assertTrue(transition.accepted)
        assertTrue(transition.changed)
        assertEquals(CapturedContentVisibility.VISIBLE, policy.currentState())
        assertEquals(1L, policy.sequence())
    }

    @Test fun `an early false callback is kept but does not overwrite UNKNOWN with HIDDEN`() {
        val transition = policy.onContentVisibilityChanged(false, sessionId = 7)

        assertTrue(transition.accepted)
        assertEquals(1L, policy.sequence())
        // No frame exists yet, so there is no valid game frame to protect; bootstrap stays open.
        assertEquals(CapturedContentVisibility.UNKNOWN, policy.currentState())
        assertTrue(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))
    }

    @Test fun `a published frame makes a later false callback authoritative`() {
        assertTrue(policy.onFramePublished())
        assertEquals(CapturedContentVisibility.VISIBLE, policy.currentState())

        val transition = policy.onContentVisibilityChanged(false, sessionId = 7)

        assertTrue(transition.changed)
        assertEquals(CapturedContentVisibility.HIDDEN, policy.currentState())
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = false))
    }

    @Test fun `hidden to visible resumes publication`() {
        policy.onContentVisibilityChanged(true, 7)
        policy.onContentVisibilityChanged(false, 7)
        assertFalse(policy.shouldPublish(true, true, false))

        policy.onContentVisibilityChanged(true, 7)

        assertEquals(CapturedContentVisibility.VISIBLE, policy.currentState())
        assertTrue(policy.shouldPublish(true, true, false))
    }

    @Test fun `repeated callbacks are idempotent but increase the diagnostic sequence`() {
        policy.onContentVisibilityChanged(true, 7)
        val first = policy.onContentVisibilityChanged(false, 7)
        val repeat = policy.onContentVisibilityChanged(false, 7)

        assertTrue(first.changed)
        assertFalse(repeat.changed)
        assertEquals(3L, policy.sequence())
    }

    @Test fun `callbacks from a previous session are rejected`() {
        val transition = policy.onContentVisibilityChanged(false, sessionId = 6)

        assertFalse(transition.accepted)
        assertEquals(0L, policy.sequence())
        assertEquals(CapturedContentVisibility.UNKNOWN, policy.currentState())
    }

    @Test fun `a new session resets state and sequence`() {
        policy.onContentVisibilityChanged(true, 7)
        policy.onContentVisibilityChanged(false, 7)

        policy.resetForNewSession(8)

        assertEquals(CapturedContentVisibility.UNKNOWN, policy.currentState())
        assertEquals(0L, policy.sequence())
        assertEquals(8, policy.boundSessionId())
    }

    @Test fun `frozen, paused and stale-generation conjuncts still block publication`() {
        assertFalse(policy.shouldPublish(isActive = false, generationCurrent = true, frozen = false))
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = false, frozen = false))
        assertFalse(policy.shouldPublish(isActive = true, generationCurrent = true, frozen = true))
    }

    @Test fun `a resume requested while hidden opens publication on the next true callback`() {
        policy.onContentVisibilityChanged(true, 7)
        policy.onContentVisibilityChanged(false, 7)
        // User intent switches to LIVE (pin cleared/pause cleared) while the window is hidden.
        assertFalse(policy.shouldPublish(true, true, frozen = false))

        policy.onContentVisibilityChanged(true, 7)
        assertTrue(policy.shouldPublish(true, true, frozen = false))
    }
}
