package com.draftlens.models

import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame

/**
 * One acquired frame with a non-null Vision payload.
 *
 * Ownership:
 * - [CaptureStateHolder] keeps only the latest instance; older instances become unreferenced and GC-eligible.
 * - Callers must not recycle a bitmap payload while Compose may still be drawing it.
 * - Stopping/resetting clears the reference; native memory is released by GC.
 *
 * [width], [height], [displayRotation], [wallClockTimestampMs] and [sampledAtElapsedRealtimeMs]
 * forward acquisition metadata; [processingLatencyMs] is the capture-side processing cost.
 */
data class CapturedFrame(
    val frame: OriginalFrame,
    /** Time from image acquisition to payload creation. Not detection latency. */
    val processingLatencyMs: Long,
) {
    val width: Int get() = frame.sourceWidth
    val height: Int get() = frame.sourceHeight
    val displayRotation: FrameRotation get() = frame.displayRotation
    val wallClockTimestampMs: Long get() = frame.wallClockTimestampMs
    val sampledAtElapsedRealtimeMs: Long get() = frame.capturedAtElapsedRealtimeMs

    fun toVisionFrame(): OriginalFrame = frame
}
