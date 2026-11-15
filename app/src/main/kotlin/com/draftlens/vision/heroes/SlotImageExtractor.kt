package com.draftlens.vision.heroes

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.geometry.PixelRect
import com.draftlens.vision.geometry.toPixelRect
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import com.draftlens.vision.model.SlotImage

/**
 * Pulls the image contained in a slot out of a captured frame, upright as the user sees it.
 * Failures are returned, never thrown; nothing is written to disk.
 */
fun interface SlotImageExtractor {
    fun extract(frame: OriginalFrame, slot: DraftSlot): Result<SlotImage>
}

/**
 * The crop bounds of a slot rectangle on the source image, in whole source pixels.
 * Same rounding rule as the canvas ([NormalizedRect.toPixelRect]): floors the origin,
 * ceils the far edge, clamps to the image, always at least one pixel per axis.
 */
internal fun slotCropBounds(rect: NormalizedRect, sourceWidth: Int, sourceHeight: Int): PixelRect =
    rect.toPixelRect(sourceWidth, sourceHeight)

/**
 * Reads an extracted slot image back into pixels for the matcher.
 * Pure logic for PixelArray payloads; Bitmap payloads are read on Android.
 */
fun pixelsOf(image: SlotImage): Triple<IntArray, Int, Int>? = when (val payload = image.payload) {
    is com.draftlens.vision.model.PixelArrayFramePayload -> {
        val pixels = payload.pixels()
        Triple(pixels, payload.width, payload.height)
    }

    is com.draftlens.vision.model.BitmapFramePayload -> {
        val bitmap = payload.bitmap
        if (bitmap.isRecycled) {
            null
        } else {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            Triple(pixels, bitmap.width, bitmap.height)
        }
    }

    else -> null
}

/** Pure Kotlin extractor for synthetic/JVM payloads. */
object PixelSlotImageExtractor : SlotImageExtractor {

    override fun extract(frame: OriginalFrame, slot: DraftSlot): Result<SlotImage> {
        val payload = frame.payload as? PixelArrayFramePayload
            ?: return Result.failure(
                IllegalArgumentException("PixelSlotImageExtractor requires a PixelArrayFramePayload"),
            )
        if (payload.width != frame.sourceWidth || payload.height != frame.sourceHeight) {
            return Result.failure(
                IllegalArgumentException(
                    "payload ${payload.width}x${payload.height} does not match source ${frame.sourceWidth}x${frame.sourceHeight}",
                ),
            )
        }
        if (slot.rect.width * frame.sourceWidth < 1f || slot.rect.height * frame.sourceHeight < 1f) {
            return Result.failure(IllegalArgumentException("The slot rectangle is smaller than one pixel"))
        }
        val crop = slotCropBounds(slot.rect, frame.sourceWidth, frame.sourceHeight)
        val source = payload.pixels() // defensive copy; the frame is never mutated or recycled
        val width = crop.width
        val height = crop.height
        val cropped = IntArray(width * height)
        for (y in 0 until height) {
            val srcRow = (crop.top + y) * frame.sourceWidth + crop.left
            System.arraycopy(source, srcRow, cropped, y * width, width)
        }
        val (pixels, outWidth, outHeight) = rotateToUpright(cropped, width, height, frame.rotation)
        val output = SlotImage(
            id = FrameId.random(),
            sourceId = frame.id,
            slotId = slot.id,
            payload = PixelArrayFramePayload(outWidth, outHeight, pixels),
            sourceRect = crop,
            createdAtElapsedRealtimeMs = System.currentTimeMillis(),
        )
        return Result.success(output)
    }

    /** Rotates the axis-aligned crop so the result matches what the canvas shows. */
    private fun rotateToUpright(
        pixels: IntArray,
        width: Int,
        height: Int,
        rotation: FrameRotation,
    ): Triple<IntArray, Int, Int> = when (rotation) {
        FrameRotation.ROTATION_0 -> Triple(pixels, width, height)
        FrameRotation.ROTATION_90 -> {
            // oriented(xx, yy) = source(col = yy, row = height - 1 - xx)
            val out = IntArray(width * height)
            for (xx in 0 until height) {
                for (yy in 0 until width) {
                    out[xx * width + yy] = pixels[(height - 1 - xx) * width + yy]
                }
            }
            Triple(out, height, width)
        }

        FrameRotation.ROTATION_180 -> {
            val out = IntArray(width * height)
            for (i in pixels.indices) out[pixels.size - 1 - i] = pixels[i]
            Triple(out, width, height)
        }

        FrameRotation.ROTATION_270 -> {
            // oriented(xx, yy) = source(col = width - 1 - yy, row = xx)
            val out = IntArray(width * height)
            for (xx in 0 until height) {
                for (yy in 0 until width) {
                    out[xx * width + yy] = pixels[xx * width + (width - 1 - yy)]
                }
            }
            Triple(out, height, width)
        }
    }
}
