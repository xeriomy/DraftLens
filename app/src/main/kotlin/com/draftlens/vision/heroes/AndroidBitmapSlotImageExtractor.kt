package com.draftlens.vision.heroes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.os.SystemClock
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.model.android.BitmapFramePayload
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import com.draftlens.vision.model.SlotImage

/** Android extractor for Bitmap payloads: axis-aligned crop, then upright rotation. */
object AndroidBitmapSlotImageExtractor : SlotImageExtractor {

    override fun extract(frame: OriginalFrame, slot: DraftSlot): Result<SlotImage> {
        val payload = frame.payload as? BitmapFramePayload
            ?: return Result.failure(IllegalArgumentException("AndroidBitmapSlotImageExtractor requires a BitmapFramePayload"))
        val bitmap = payload.bitmap
        if (bitmap.isRecycled) {
            return Result.failure(IllegalStateException("The frame bitmap has been recycled"))
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
        val upright = rotateToUpright(cropped, frame.rotation)
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

    private fun rotateToUpright(source: Bitmap, rotation: FrameRotation): Bitmap {
        if (rotation == FrameRotation.ROTATION_0) return source
        val swapsAxes = rotation == FrameRotation.ROTATION_90 || rotation == FrameRotation.ROTATION_270
        val outWidth = if (swapsAxes) source.height else source.width
        val outHeight = if (swapsAxes) source.width else source.height
        val out = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val matrix = Matrix().apply {
            postTranslate(-source.width / 2f, -source.height / 2f)
            postRotate(
                when (rotation) {
                    FrameRotation.ROTATION_90 -> 90f
                    FrameRotation.ROTATION_180 -> 180f
                    FrameRotation.ROTATION_270 -> 270f
                    else -> 0f
                },
            )
            postTranslate(outWidth / 2f, outHeight / 2f)
        }
        Canvas(out).drawBitmap(source, matrix, null)
        return out
    }
}

/** Picks the extractor that can read [frame]'s payload. */
object SlotImageExtractors {
    fun forFrame(frame: OriginalFrame): SlotImageExtractor = when (frame.payload) {
        is PixelArrayFramePayload -> PixelSlotImageExtractor
        else -> AndroidBitmapSlotImageExtractor
    }
}
