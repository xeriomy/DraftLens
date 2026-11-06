package com.draftlens.vision.preprocessing

import com.draftlens.vision.geometry.FrameTransform
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.geometry.PixelRect
import com.draftlens.vision.geometry.toPixelRect
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelFormat
import kotlin.math.roundToInt

/**
 * Validated sizing decision for one frame. All dimensions and coordinates come from the
 * existing [FrameTransform] so rendering and coordinate mapping cannot disagree.
 */
data class PreprocessPlan(val transform: FrameTransform) {
    val sourceWidth: Int get() = transform.sourceWidth
    val sourceHeight: Int get() = transform.sourceHeight
    val rotation: FrameRotation get() = transform.rotation
    val crop: NormalizedRect get() = transform.crop
    val orientedWidth: Int get() = transform.orientedWidth
    val orientedHeight: Int get() = transform.orientedHeight
    val cropPixelRect: PixelRect get() = transform.cropPixelRect
    val outputWidth: Int get() = transform.processedWidth
    val outputHeight: Int get() = transform.processedHeight
    val scale: Float get() = transform.scale
}

sealed interface PreprocessPlanResult {
    val isSuccess: Boolean

    fun getOrThrow(): PreprocessPlan

    data class Success(val plan: PreprocessPlan) : PreprocessPlanResult {
        override val isSuccess: Boolean get() = true
        override fun getOrThrow(): PreprocessPlan = plan
    }

    data class Failure(val reason: PreprocessFailure) : PreprocessPlanResult {
        override val isSuccess: Boolean get() = false
        override fun getOrThrow(): PreprocessPlan =
            throw IllegalStateException(reason.message)
    }
}

object PreprocessingMath {

    /**
     * Validates [frame] against [request] and derives the oriented crop and output size.
     *
     * The output long side never exceeds [ProcessFrameRequest.maxProcessedDimension] and is
     * never upscaled. Output is also rejected above `Int.MAX_VALUE` pixels because rendering
     * allocates a single `IntArray`. Aspect ratio is preserved within one output pixel by
     * rounding the short side. Failures carry a [PreprocessFailureCode] instead of throwing.
     */
    fun plan(frame: OriginalFrame, request: ProcessFrameRequest): PreprocessPlanResult {
        if (frame.sourceWidth <= 0 || frame.sourceHeight <= 0) {
            return failure(
                PreprocessFailureCode.INVALID_DIMENSIONS,
                "source dimensions must be positive, was ${frame.sourceWidth}x${frame.sourceHeight}",
            )
        }
        if (request.maxProcessedDimension <= 0) {
            return failure(
                PreprocessFailureCode.INVALID_DIMENSIONS,
                "maxProcessedDimension must be positive, was ${request.maxProcessedDimension}",
            )
        }
        if (request.maxPixels <= 0) {
            return failure(
                PreprocessFailureCode.INVALID_DIMENSIONS,
                "maxPixels must be positive, was ${request.maxPixels}",
            )
        }
        if (frame.payload.width != frame.sourceWidth || frame.payload.height != frame.sourceHeight) {
            return failure(
                PreprocessFailureCode.PAYLOAD_MISMATCH,
                "payload ${frame.payload.width}x${frame.payload.height} does not match " +
                    "source ${frame.sourceWidth}x${frame.sourceHeight}",
            )
        }
        if (frame.payload.format != PixelFormat.ARGB_8888) {
            return failure(
                PreprocessFailureCode.UNSUPPORTED_FORMAT,
                "unsupported pixel format ${frame.payload.format}",
            )
        }

        val sourcePixels = frame.sourceWidth.toLong() * frame.sourceHeight.toLong()
        if (sourcePixels > request.maxPixels) {
            return failure(
                PreprocessFailureCode.TOO_MANY_PIXELS,
                "source has $sourcePixels pixels, limit is ${request.maxPixels}",
            )
        }

        val swapsAxes =
            frame.rotation == FrameRotation.ROTATION_90 || frame.rotation == FrameRotation.ROTATION_270
        val orientedWidth = if (swapsAxes) frame.sourceHeight else frame.sourceWidth
        val orientedHeight = if (swapsAxes) frame.sourceWidth else frame.sourceHeight

        val cropPixelRect = try {
            (request.crop ?: FULL_FRAME_CROP).toPixelRect(orientedWidth, orientedHeight)
        } catch (exception: IllegalArgumentException) {
            return failure(
                PreprocessFailureCode.INVALID_CROP,
                "crop cannot be applied to ${orientedWidth}x$orientedHeight oriented pixels: " +
                    (exception.message ?: "invalid rectangle"),
            )
        }

        val (outputWidth, outputHeight) = calculateOutputSize(
            cropWidth = cropPixelRect.width,
            cropHeight = cropPixelRect.height,
            maxProcessedDimension = request.maxProcessedDimension,
        )
        if (outputWidth <= 0 || outputHeight <= 0) {
            return failure(
                PreprocessFailureCode.OUTPUT_TOO_LARGE,
                "computed output ${outputWidth}x$outputHeight is not positive",
            )
        }
        val outputPixels = outputWidth.toLong() * outputHeight.toLong()
        // Rendering allocates an IntArray; more than Int.MAX_VALUE pixels cannot fit it.
        if (outputPixels > Int.MAX_VALUE) {
            return failure(
                PreprocessFailureCode.OUTPUT_TOO_LARGE,
                "output has $outputPixels pixels, exceeds the ${Int.MAX_VALUE} pixel limit",
            )
        }
        if (outputPixels > request.maxPixels) {
            return failure(
                PreprocessFailureCode.OUTPUT_TOO_LARGE,
                "output has $outputPixels pixels, limit is ${request.maxPixels}",
            )
        }

        val transform = try {
            FrameTransform.create(
                sourceWidth = frame.sourceWidth,
                sourceHeight = frame.sourceHeight,
                rotation = frame.rotation,
                crop = request.crop,
                processedWidth = outputWidth,
                processedHeight = outputHeight,
            )
        } catch (exception: IllegalArgumentException) {
            return failure(
                PreprocessFailureCode.INVALID_DIMENSIONS,
                "cannot build transform: ${exception.message ?: "invalid dimensions"}",
            )
        }

        return PreprocessPlanResult.Success(PreprocessPlan(transform))
    }

    private fun calculateOutputSize(
        cropWidth: Int,
        cropHeight: Int,
        maxProcessedDimension: Int,
    ): Pair<Int, Int> {
        val longSide = maxOf(cropWidth, cropHeight)
        if (longSide <= maxProcessedDimension) return cropWidth to cropHeight

        return if (cropWidth >= cropHeight) {
            val width = maxProcessedDimension
            val height = (cropHeight.toDouble() * width / cropWidth).roundToInt().coerceAtLeast(1)
            width to height
        } else {
            val height = maxProcessedDimension
            val width = (cropWidth.toDouble() * height / cropHeight).roundToInt().coerceAtLeast(1)
            width to height
        }
    }

    private fun failure(code: PreprocessFailureCode, message: String): PreprocessPlanResult.Failure =
        PreprocessPlanResult.Failure(PreprocessFailure(code, message))

    private val FULL_FRAME_CROP = NormalizedRect(0f, 0f, 1f, 1f)
}
