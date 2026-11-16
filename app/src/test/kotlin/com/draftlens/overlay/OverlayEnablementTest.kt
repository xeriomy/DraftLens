package com.draftlens.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the gate both production callers (capture start, UI refresh) evaluate. */
class OverlayEnablementTest {

    @Test fun `attach requires preference, permission and a running session`() {
        assertTrue(OverlayEnablement.shouldAttachOverlay(true, true, true))
        assertFalse(OverlayEnablement.shouldAttachOverlay(false, true, true))
        assertFalse(OverlayEnablement.shouldAttachOverlay(true, false, true))
        assertFalse(OverlayEnablement.shouldAttachOverlay(true, true, false))
    }

    @Test fun `permission denial never attaches, whatever the preference says`() {
        assertFalse(OverlayEnablement.shouldAttachOverlay(enabledPreference = true, canDrawOverlays = false, captureRunning = true))
        assertFalse(OverlayEnablement.shouldAttachOverlay(enabledPreference = true, canDrawOverlays = false, captureRunning = false))
        assertFalse(OverlayEnablement.shouldAttachOverlay(enabledPreference = false, canDrawOverlays = false, captureRunning = true))
    }

    @Test fun `a missing preference never attaches, even with the permission granted`() {
        assertFalse(OverlayEnablement.shouldAttachOverlay(enabledPreference = false, canDrawOverlays = true, captureRunning = true))
    }

    @Test fun `a stopped capture never attaches, even when everything else is ready`() {
        assertFalse(OverlayEnablement.shouldAttachOverlay(enabledPreference = true, canDrawOverlays = true, captureRunning = false))
    }
}
