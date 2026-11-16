package com.draftlens.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayEnablementTest {

    @Test fun `enabled with permission shows the control`() {
        assertEquals(
            OverlayEnablement.OverlayRequestOutcome.ShowControl,
            OverlayEnablement.decideOverlayRequest(canDrawOverlays = true, enabledPreference = true),
        )
    }

    @Test fun `enabled without permission requests it and never shows the control`() {
        assertEquals(
            OverlayEnablement.OverlayRequestOutcome.RequestPermission,
            OverlayEnablement.decideOverlayRequest(canDrawOverlays = false, enabledPreference = true),
        )
    }

    @Test fun `disabled stays hidden even with permission granted`() {
        assertEquals(
            OverlayEnablement.OverlayRequestOutcome.KeepHidden,
            OverlayEnablement.decideOverlayRequest(canDrawOverlays = true, enabledPreference = false),
        )
        assertEquals(
            OverlayEnablement.OverlayRequestOutcome.KeepHidden,
            OverlayEnablement.decideOverlayRequest(canDrawOverlays = false, enabledPreference = false),
        )
    }

    @Test fun `attach requires preference, permission and a running session`() {
        assertTrue(OverlayEnablement.shouldAttachOverlay(true, true, true))
        assertFalse(OverlayEnablement.shouldAttachOverlay(false, true, true))
        assertFalse(OverlayEnablement.shouldAttachOverlay(true, false, true))
        assertFalse(OverlayEnablement.shouldAttachOverlay(true, true, false))
    }
}
