package com.draftlens.vision.preprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import com.draftlens.vision.geometry.FrameTransform
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.ProcessedFrame
import com.draftlens.vision.model.android.BitmapFramePayload

/**
 * Android renderer for [BitmapFramePayload] frames. Validation uses [PreprocessingMath];
 * rendering draws the source into exactly one destination bitmap with a single Canvas
 * matrix operation, so no full-size oriented intermediate exists.
 */
object AndroidFramePreprocessor : FramePreprocessor {

    override fun process(input: OriginalFrame, request: ProcessFrameRequest): ProcessFrameResult {
        val startedAtNanos = System.nanoTime()

        val plan = when (val result = PreprocessingMath.plan(input, request)) {
            is PreprocessPlanResult.Failure -> return ProcessFrameResult.Failure(result.reason)
            is PreprocessPlanResult.Success -> result.plan
        }
        val payload = input.payload
        if (payload !is BitmapFramePayload) {
            return ProcessFrameResult.Failure(
                PreprocessFailure(
                    PreprocessFailureCode.UNSUPPORTED_FORMAT,
                    "AndroidFramePreprocessor requires a BitmapFramePayload",
                ),
            )
        }

        val rendered = runCatching { render(payload, plan) }.getOrElse { throwable ->
            return ProcessFrameResult.Failure(
                PreprocessFailure(
                    PreprocessFailureCode.RENDER_FAILED,
                    "bitmap rendering failed: " +
                        (throwable.message ?: throwable.javaClass.simpleName),
                ),
            )
        }

        return ProcessFrameResult.Success(
            ProcessedFrame(
                id = FrameId.random(),
                sourceId = input.id,
                payload = rendered,
                sourceWidth = input.sourceWidth,
                sourceHeight = input.sourceHeight,
                displayRotation = input.displayRotation,
                rotation = input.rotation,
                capturedAtElapsedRealtimeMs = input.capturedAtElapsedRealtimeMs,
                wallClockTimestampMs = input.wallClockTimestampMs,
                transform = plan.transform,
                processingDurationMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND,
            ),
        )
    }

    private fun render(
        payload: BitmapFramePayload,
        plan: PreprocessPlan,
    ): BitmapFramePayload {
        val destination = Bitmap.createBitmap(
            plan.outputWidth,
            plan.outputHeight,
            Bitmap.Config.ARGB_8888,
        )
        Canvas(destination).drawBitmap(payload.bitmap, matrixFor(plan.transform), null)
        return BitmapFramePayload(destination)
    }

    /**
     * Builds the single source-to-processed matrix: rotation correction, oriented crop
     * offset, then downscale. Each `post*` call applies after the previous operation.
     */
    private fun matrixFor(transform: FrameTransform): Matrix {
        val matrix = Matrix()
        when (transform.rotation) {
            FrameRotation.ROTATION_0 -> Unit
            FrameRotation.ROTATION_90 -> {
                matrix.setRotate(90f)
                matrix.postTranslate(transform.sourceHeight.toFloat(), 0f)
            }
            FrameRotation.ROTATION_180 -> {
                matrix.setRotate(180f)
                matrix.postTranslate(transform.sourceWidth.toFloat(), transform.sourceHeight.toFloat())
            }
            FrameRotation.ROTATION_270 -> {
                matrix.setRotate(270f)
                matrix.postTranslate(0f, transform.sourceWidth.toFloat())
            }
        }
        matrix.postTranslate(
            -transform.cropPixelRect.left.toFloat(),
            -transform.cropPixelRect.top.toFloat(),
        )
        matrix.postScale(
            transform.processedWidth.toFloat() / transform.cropPixelRect.width,
            transform.processedHeight.toFloat() / transform.cropPixelRect.height,
        )
        return matrix
    }

    private const val NANOS_PER_MILLISECOND = 1_000_000L
}
