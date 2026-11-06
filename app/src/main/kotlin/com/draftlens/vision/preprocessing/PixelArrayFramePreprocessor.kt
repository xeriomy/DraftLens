package com.draftlens.vision.preprocessing

import com.draftlens.vision.geometry.FloatPoint
import com.draftlens.vision.model.FrameId
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.PixelArrayFramePayload
import com.draftlens.vision.model.ProcessedFrame
import kotlin.math.floor

/**
 * Pure Kotlin renderer for [PixelArrayFramePayload] frames. Applies rotation, crop, and
 * nearest-neighbor downscale in one pass without allocating an oriented intermediate.
 */
object PixelArrayFramePreprocessor : FramePreprocessor {

    override fun process(input: OriginalFrame, request: ProcessFrameRequest): ProcessFrameResult {
        val startedAtNanos = System.nanoTime()

        val plan = when (val result = PreprocessingMath.plan(input, request)) {
            is PreprocessPlanResult.Failure -> return ProcessFrameResult.Failure(result.reason)
            is PreprocessPlanResult.Success -> result.plan
        }
        val payload = input.payload
        if (payload !is PixelArrayFramePayload) {
            return ProcessFrameResult.Failure(
                PreprocessFailure(
                    PreprocessFailureCode.UNSUPPORTED_FORMAT,
                    "PixelArrayFramePreprocessor requires a PixelArrayFramePayload",
                ),
            )
        }

        val reusesPayload = request.crop == null &&
            plan.rotation == FrameRotation.ROTATION_0 &&
            plan.outputWidth == plan.sourceWidth &&
            plan.outputHeight == plan.sourceHeight
        val outputPayload = if (reusesPayload) {
            payload
        } else {
            runCatching { render(payload, plan) }.getOrElse { throwable ->
                return ProcessFrameResult.Failure(
                    PreprocessFailure(
                        PreprocessFailureCode.RENDER_FAILED,
                        "pixel rendering failed: " +
                            (throwable.message ?: throwable.javaClass.simpleName),
                    ),
                )
            }
        }

        return ProcessFrameResult.Success(
            ProcessedFrame(
                id = FrameId.random(),
                sourceId = input.id,
                payload = outputPayload,
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
        payload: PixelArrayFramePayload,
        plan: PreprocessPlan,
    ): PixelArrayFramePayload {
        val transform = plan.transform
        val source = payload.pixels()
        val output = IntArray(plan.outputWidth * plan.outputHeight)

        for (y in 0 until plan.outputHeight) {
            val rowStart = y * plan.outputWidth
            for (x in 0 until plan.outputWidth) {
                val sourcePoint = transform.processedToSource(FloatPoint(x + 0.5f, y + 0.5f))
                val sourceX = floor(sourcePoint.x).toInt().coerceIn(0, plan.sourceWidth - 1)
                val sourceY = floor(sourcePoint.y).toInt().coerceIn(0, plan.sourceHeight - 1)
                output[rowStart + x] = source[sourceY * plan.sourceWidth + sourceX]
            }
        }

        return PixelArrayFramePayload(plan.outputWidth, plan.outputHeight, output)
    }

    private const val NANOS_PER_MILLISECOND = 1_000_000L
}
