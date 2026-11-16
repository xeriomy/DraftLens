package com.draftlens.overlay

/**
 * Decides what the capture screen should do about the floating control, in one pure
 * function so the denial path is unit-testable. Denial never implies enablement: capture
 * and the in-app Freeze/Resume work regardless.
 */
object OverlayEnablement {

    sealed interface OverlayRequestOutcome {
        /** Preference on and the permission is granted: attach the overlay. */
        data object ShowControl : OverlayRequestOutcome

        /** Preference on but the permission is missing: open the overlay settings screen. */
        data object RequestPermission : OverlayRequestOutcome

        /** Preference off: show nothing; capture and the in-app Freeze/Resume still work. */
        data object KeepHidden : OverlayRequestOutcome
    }

    fun decideOverlayRequest(canDrawOverlays: Boolean, enabledPreference: Boolean): OverlayRequestOutcome =
        when {
            !enabledPreference -> OverlayRequestOutcome.KeepHidden
            canDrawOverlays -> OverlayRequestOutcome.ShowControl
            else -> OverlayRequestOutcome.RequestPermission
        }

    /** True only when the capture session is running, the user wants it, and it is allowed. */
    fun shouldAttachOverlay(enabledPreference: Boolean, canDrawOverlays: Boolean, captureRunning: Boolean): Boolean =
        enabledPreference && canDrawOverlays && captureRunning
}
