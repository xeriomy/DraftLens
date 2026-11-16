package com.draftlens.capture

/**
 * Decides whether a freshly acquired captured-content image may be converted and
 * published. Single-app capture windows can disappear from the screen while the
 * projection stays valid: images acquired while hidden contain DraftLens, the shade or
 * other apps — never the game — and must not overwrite the last valid game frame.
 *
 * Pure Kotlin so the truth table is unit-testable on the JVM; the pipeline holds one
 * instance and feeds it from `MediaProjection.Callback.onCapturedContentVisibilityChanged`
 * (API 34+). Full-display captures never report hidden, so the gate stays open there.
 *
 * Thread-safe: read from the ImageReader thread, written from the callback thread.
 */
class CaptureVisibilityPolicy {

    private val lock = Any()

    @Volatile
    private var contentVisible: Boolean = true

    /** Last visibility state; starts visible because most projections are full-display. */
    fun isContentVisible(): Boolean = contentVisible

    /** Effective state for structured diagnostics. */
    fun currentState(): String = if (contentVisible) "VISIBLE" else "HIDDEN"

    /**
     * Records a visibility change. Returns what changed so callers can log and act;
     * `changed == false` means the platform repeated the current value and the caller
     * should treat it as a no-op.
     */
    fun onContentVisibilityChanged(visible: Boolean): VisibilityTransition = synchronized(lock) {
        val previous = contentVisible
        contentVisible = visible
        VisibilityTransition(wasVisible = previous, isVisible = visible)
    }

    /**
     * Publication decision, evaluated per acquired image and again at publish time.
     * [frozen] is the user's intent: an explicitly frozen session publishes nothing even
     * while the window is visible, and a visible window publishes only when not frozen.
     */
    fun shouldPublish(isActive: Boolean, generationCurrent: Boolean, frozen: Boolean): Boolean =
        isActive && generationCurrent && !frozen && contentVisible
}

/** Result of a visibility transition; [changed] false = duplicate callback. */
data class VisibilityTransition(
    val wasVisible: Boolean,
    val isVisible: Boolean,
) {
    val changed: Boolean get() = wasVisible != isVisible
}
