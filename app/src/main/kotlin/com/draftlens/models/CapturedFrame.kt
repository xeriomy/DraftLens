package com.draftlens.models

import android.graphics.Bitmap

/**
 * One acquired frame. The Bitmap is the only analysis artifact retained.
 *
 * Ownership:
 * - [CaptureStateHolder] keeps only the latest instance; older instances become unreferenced and GC-eligible.
 * - Callers must not recycle the Bitmap while Compose may still be drawing it.
 * - Stopping/resetting clears the reference; native memory is released by GC.
 */
data class CapturedFrame(
    val bitmap: Bitmap?,
    val sampledAtElapsedRealtimeMs: Long,
    val wallClockTimestampMs: Long,
    /** Time from image acquisition to Bitmap creation. Not detection latency. */
    val processingLatencyMs: Long,
    val width: Int,
    val height: Int
)
