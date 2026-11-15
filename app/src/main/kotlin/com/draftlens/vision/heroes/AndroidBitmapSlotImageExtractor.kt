package com.draftlens.vision.heroes

import android.graphics.Bitmap
import android.os.SystemClock
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.model.android.BitmapFramePayload
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import com.draftlens.vision.model.SlotImage

/**
 * Android extractor for Bitmap payloads: axis-aligned crop, then upright rotation.
 *
 * The rotation mapping is the FrameTransform.sourceToOriented() convention applied
 * pixel-exactly (the same mapping the JVM extractor uses), never an approximating
 * matrix. The source bitmap is never mutated or recycled.
 *
 * Ownership: the input bitmap is borrowed. The cropped intermediate is recycled in
 * every path exactly once; the returned payload owns the (possibly rotated) bitmap.
 */
object AndroidBitmapSlotImageExtractor : SlotImageExtractor {

    override fun extract(frame: OriginalFrame, slot: DraftSlot): Result<SlotImage> {
        val payload = frame.payload as? BitmapFramePayload
            ?: return Result.failure(IllegalArgumentException("AndroidBitmapSlotImageExtractor requires a BitmapFramePayload"))
        val bitmap = payload.bitmap
        if (bitmap.isRecycled) {
            return Result.failure(IllegalStateException("The frame bitmap has been recycled"))
        }
        if (bitmap.width != frame.sourceWidth || bitmap.height != frame.sourceHeight) {
            return Result.failure(
                IllegalArgumentException(
                    "payload bitmap ${bitmap.width}x${bitmap.height} does not match source " +
                        "${frame.sourceWidth}x${frame.sourceHeight}",
                ),
            )
        }
        if (slot.rect.width * frame.sourceWidth < 1f || slot.rect.height * frame.sourceHeight < 1f) {
            return Result.failure(IllegalArgumentException("The slot rectangle is smaller than one pixel"))
        }
        val crop = slotCropBounds(slot.rect, frame.sourceWidth, frame.sourceHeight)
        val cropped = try {
            Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width, crop.height)
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("The slot could not be cropped: ${e.message}", e))
        }
        val upright = try {
            rotateToUpright(cropped, frame.rotation)
        } catch (e: Exception) {
            if (!cropped.isRecycled) cropped.recycle()
            return Result.failure(IllegalStateException("The slot could not be rotated: ${e.message}", e))
        }
        // Ownership transfers: cropped is not needed once upright is built.
        if (upright !== cropped) cropped.recycle()
        return Result.success(
            SlotImage(
                id = FrameId.random(),
                sourceId = frame.id,
                slotId = slot.id,
                payload = BitmapFramePayload(upright),
                sourceRect = crop,
                createdAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            ),
        )
    }

    /**
     * Pixel-exact rotation following FrameTransform.sourceToOriented():
     * ROTATION_90 maps source (sx, sy) to oriented (height - 1 - sy, sx),
     * ROTATION_270 maps source (sx, sy) to oriented (sy, width - 1 - sx).
     * [source] is not recycled here; the caller owns it. The returned bitmap is owned
     * by the caller. On failure no output bitmap leaks.
     */
    private fun rotateToUpright(source: Bitmap, rotation: FrameRotation): Bitmap {
        if (rotation == FrameRotation.ROTATION_0) return source
        val width = source.width
        val height = source.height
        val swapsAxes = rotation == FrameRotation.ROTATION_90 || rotation == FrameRotation.ROTATION_270
        val outWidth = if (swapsAxes) height else width
        val outHeight = if (swapsAxes) width else height
        val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        try {
            val src = IntArray(width * height)
            source.getPixels(src, 0, width, 0, 0, width, height)
            val dst = IntArray(outWidth * outHeight)
            when (rotation) {
                FrameRotation.ROTATION_90 ->
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            dst[x * height + (height - 1 - y)] = src[y * width + x]
                        }
                    }

                FrameRotation.ROTATION_180 ->
                    for (i in src.indices) {
                        dst[src.size - 1 - i] = src[i]
                    }

                FrameRotation.ROTATION_270 ->
                    for (y in 0 until height) {
                        for (x in 0 until width) {
                            dst[(width - 1 - x) * height + y] = src[y * width + x]
                        }
                    }
            }
            out.setPixels(dst, 0, outWidth, 0, 0, outWidth, outHeight)
            return out
        } catch (e: Exception) {
            if (!out.isRecycled) out.recycle()
            throw e
        }
    }
}

/** Picks the extractor that can read [frame]'s payload. */
object SlotImageExtractors {
    fun forFrame(frame: OriginalFrame): SlotImageExtractor = when (frame.payload) {
        is PixelArrayFramePayload -> PixelSlotImageExtractor
        else -> AndroidBitmapSlotImageExtractor
    }
}
