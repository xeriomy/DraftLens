package com.draftlens.models

import android.graphics.Bitmap

/**
 * One acquired frame. Bitmap is owned by the holder; pipeline recycles previous
 * frame before publishing a new one so we never hold more than one in memory.
 */
data class CapturedFrame(
    val bitmap: Bitmap,
    val timestampMs: Long,
    val latencyMs: Long,
    val width: Int,
    val height: Int
)
