package com.draftlens.vision.model.android

import android.graphics.Bitmap
import com.draftlens.vision.model.FramePayload
import com.draftlens.vision.model.PixelFormat

/**
 * Ownership marker only, not a synchronized lifetime system.
 * `SHARED_READ_ONLY` consumers may inspect and draw the bitmap but must not mutate or recycle it.
 */
enum class BitmapOwnership { SHARED_READ_ONLY }

/**
 * Frame payload backed by a non-null [Bitmap] owned by the capture pipeline.
 * Callers never recycle it; references are dropped instead so the owner stays in control.
 */
class BitmapFramePayload(
    val bitmap: Bitmap,
    val ownership: BitmapOwnership = BitmapOwnership.SHARED_READ_ONLY,
) : FramePayload {
    override val width: Int
        get() = if (bitmap.isRecycled) 0 else bitmap.width

    override val height: Int
        get() = if (bitmap.isRecycled) 0 else bitmap.height

    override val format: PixelFormat
        get() = PixelFormat.ARGB_8888

    override val byteCountEstimate: Long
        get() = if (bitmap.isRecycled) 0L else bitmap.allocationByteCount.toLong()
}
