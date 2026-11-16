package com.draftlens.capture

/**
 * Single source of truth for the "is live publication happening?" presentation used by
 * the capture notification, the floating overlay glyph and the toggle logic.
 *
 * Three states, never independent:
 * - LIVE: images convert and publish (no pin, not paused).
 * - PAUSED: the user (or the notification) paused publication; projection stays valid.
 * - FROZEN: a frame snapshot is pinned (in-app freeze / floating button).
 *
 * Pure Kotlin so the decision matrix is unit-testable; every UI surface derives its
 * action from here instead of keeping parallel state.
 */
enum class CapturePublishState { LIVE, PAUSED, FROZEN }

/** What the notification's one freeze/pause action shows. */
enum class CapturePublishAction { PAUSE, RESUME }

enum class FloatingTapOutcome { FREEZE, RESUME }

/** Derivation both surfaces share; a pin wins over pause for presentation. */
fun publishStateFor(pinInPlace: Boolean, isPaused: Boolean): CapturePublishState = when {
    pinInPlace -> CapturePublishState.FROZEN
    isPaused -> CapturePublishState.PAUSED
    else -> CapturePublishState.LIVE
}

fun publishActionFor(state: CapturePublishState): CapturePublishAction =
    if (state == CapturePublishState.LIVE) CapturePublishAction.PAUSE else CapturePublishAction.RESUME

/** The overlay tap toggles the shared pause/freeze gate; it never invents a new one. */
fun floatingTapFor(state: CapturePublishState): FloatingTapOutcome =
    if (state == CapturePublishState.LIVE) FloatingTapOutcome.FREEZE else FloatingTapOutcome.RESUME

fun publishStateBlocksPublication(state: CapturePublishState): Boolean = state != CapturePublishState.LIVE
