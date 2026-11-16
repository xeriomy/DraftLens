package com.draftlens.models

import android.graphics.Bitmap
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import com.draftlens.vision.model.android.BitmapFramePayload

/**
 * Creates independently owned copies of frames for the freeze workflow.
 *
 * The copy shares no payload buffer with the source: PixelArray payloads get a fresh
 * IntArray, Bitmap payloads a fresh Bitmap. All acquisition metadata (dimensions,
 * rotation, display rotation, timestamps, latency) is preserved verbatim; geometry and
 * pixel contents are untouched. Copies are not recycled here; consumers may not recycle
 * them either. The live pipeline never sees or mutates these copies.
 *
 * Returns null when the payload cannot be copied (for example a recycled bitmap), so a
 * freeze can fail cleanly without touching the source.
 */
object FrameSnapshots {

    fun copyOf(frame: OriginalFrame): OriginalFrame? = when (val payload = frame.payload) {
        is PixelArrayFramePayload -> OriginalFrame(
            id = frame.id,
            payload = PixelArrayFramePayload(payload.width, payload.height, payload.pixels()),
            sourceWidth = frame.sourceWidth,
            sourceHeight = frame.sourceHeight,
            displayRotation = frame.displayRotation,
            rotation = frame.rotation,
            capturedAtElapsedRealtimeMs = frame.capturedAtElapsedRealtimeMs,
            wallClockTimestampMs = frame.wallClockTimestampMs,
            captureLatencyMs = frame.captureLatencyMs,
        )

        is BitmapFramePayload -> {
            val source = payload.bitmap
            if (source.isRecycled) {
                null
            } else {
                val copy = try {
                    source.copy(Bitmap.Config.ARGB_8888, false)
                } catch (e: Exception) {
                    null
                }
                if (copy == null) {
                    null
                } else {
                    OriginalFrame(
                        id = frame.id,
                        payload = BitmapFramePayload(copy),
                        sourceWidth = frame.sourceWidth,
                        sourceHeight = frame.sourceHeight,
                        displayRotation = frame.displayRotation,
                        rotation = frame.rotation,
                        capturedAtElapsedRealtimeMs = frame.capturedAtElapsedRealtimeMs,
                        wallClockTimestampMs = frame.wallClockTimestampMs,
                        captureLatencyMs = frame.captureLatencyMs,
                    )
                }
            }
        }

        else -> null
    }

    fun copyOf(captured: CapturedFrame): CapturedFrame? =
        copyOf(captured.frame)?.let { CapturedFrame(it, captured.processingLatencyMs) }
}
