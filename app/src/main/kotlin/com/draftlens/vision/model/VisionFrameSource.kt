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
 * service or the capture state holder.
 */
interface VisionFrameSource {
    val frames: StateFlow<OriginalFrame?>
    val state: StateFlow<VisionSourceState>
}
