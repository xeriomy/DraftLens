package com.draftlens.capture

/**
 * Visibility of the captured single-app window, with an explicit third state.
 *
 * UNKNOWN is deliberate: no platform callback has been applied yet for this session, or a
 * `false` arrived before any frame existed (the captured window was hidden while the user
 * was still in DraftLens choosing the app). UNKNOWN **allows** publication — full-display
 * projections never call back at all, and an early single-app `false` must not block the
 * first frame forever. The first successfully published frame promotes UNKNOWN → VISIBLE,
 * so every later `false` is authoritative and protects the valid frame already held.
 *
 * HIDDEN blocks publication only; it never touches the overlay, projection or VirtualDisplay.
 * Callbacks are kept even when they arrive before the pipeline exists: the policy is owned
 * by the service, so ordering can no longer lose or overwrite a real callback.
 *
 * Thread-safe: read from the ImageReader thread, written from the callback thread.
 */
enum class CapturedContentVisibility { UNKNOWN, VISIBLE, HIDDEN }

class CaptureVisibilityPolicy {

    private val lock = Any()

    @Volatile
    private var state: CapturedContentVisibility = CapturedContentVisibility.UNKNOWN

    @Volatile
    private var sessionId: Int = 0

    @Volatile
    private var sequence: Long = 0L

    fun currentState(): CapturedContentVisibility = state

    fun sequence(): Long = sequence

    fun boundSessionId(): Int = sessionId

    /** Called once per capture session, before consent/projection resources exist. */
    fun resetForNewSession(id: Int) = synchronized(lock) {
        state = CapturedContentVisibility.UNKNOWN
        sessionId = id
        sequence = 0L
        Unit
    }

    /**
     * Stores a platform callback, including those arriving before the pipeline is ready.
     * Callbacks for a previous session are ignored (stale projection token).
     */
    fun onContentVisibilityChanged(visible: Boolean, id: Int): VisibilityTransition = synchronized(lock) {
        if (id != sessionId) {
            VisibilityTransition(state, state, sequence, accepted = false)
        } else {
            sequence += 1
            val previous = state
            state = when {
                visible -> CapturedContentVisibility.VISIBLE
                state == CapturedContentVisibility.VISIBLE -> CapturedContentVisibility.HIDDEN
                state == CapturedContentVisibility.HIDDEN -> CapturedContentVisibility.HIDDEN
                else -> state // UNKNOWN + early false: nothing valid to protect yet
            }
            VisibilityTransition(previous, state, sequence, accepted = true)
        }
    }

    /** A real frame makes a following `false` authoritative (UNKNOWN → VISIBLE). */
    fun onFramePublished(): Boolean = synchronized(lock) {
        if (state != CapturedContentVisibility.UNKNOWN) return false
        state = CapturedContentVisibility.VISIBLE
        true
    }

    fun shouldPublish(isActive: Boolean, generationCurrent: Boolean, frozen: Boolean): Boolean =
        isActive && generationCurrent && !frozen && state != CapturedContentVisibility.HIDDEN
}

/** Result of a stored visibility callback; [accepted] false = stale-session callback. */
data class VisibilityTransition(
    val previous: CapturedContentVisibility,
    val current: CapturedContentVisibility,
    val sequence: Long,
    val accepted: Boolean = true,
) {
    val changed: Boolean get() = previous != current
}
