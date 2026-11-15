package com.draftlens.vision.model

import kotlinx.coroutines.flow.StateFlow

/**
 * State of the frame source as seen by Vision.
 *
 * [UNKNOWN] covers capture states that are not yet meaningful to Vision, such as
 * the system consent dialog being open.
 */
enum class VisionSourceState { IDLE, CAPTURING, PAUSED, ERROR, STOPPED, UNKNOWN }

/**
 * Narrow seam between capture and Vision.
 *
 * Vision consumes frames through this interface only; it never imports the capture
 * service or the capture state holder. The freeze commands are part of the seam:
 * they run the capture-side freeze/resume workflow, whose results come back
 * through [frozenFrame].
 */
interface VisionFrameSource {
    val frames: StateFlow<OriginalFrame?>
    val state: StateFlow<VisionSourceState>

    /**
     * The independently owned frame pinned by a freeze command; null while live.
     * The capture side publishes this; the UI renders it in place of live frames.
     */
    val frozenFrame: StateFlow<OriginalFrame?>

    /**
     * Pins the newest valid frame and pauses publication of live frames. Idempotent
     * for an already-frozen source; false is returned only when there is nothing to pin
     * (no capture running, or no frame captured yet).
     */
    fun freeze(): Boolean

    /** Releases the pinned frame and resumes live publication. */
    fun resume()
}
