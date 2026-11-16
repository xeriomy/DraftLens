package com.draftlens.overlay

/**
 * The one decision the shipped code makes about the floating control: may it be attached?
 * Capture start and the UI refresh both evaluate this — the capture path calls it with a
 * live session, the screen never attaches anything itself.
 */
object OverlayEnablement {

    /** True only when the user wants it, Android allows it, and capture is running. */
    fun shouldAttachOverlay(enabledPreference: Boolean, canDrawOverlays: Boolean, captureRunning: Boolean): Boolean =
        enabledPreference && canDrawOverlays && captureRunning
}
