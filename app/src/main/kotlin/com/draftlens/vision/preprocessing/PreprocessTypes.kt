package com.draftlens.vision.preprocessing

import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.ProcessedFrame

/** Explicit limits and optional oriented-coordinate crop for one preprocessing request. */
data class ProcessFrameRequest(
    val maxProcessedDimension: Int = 1280,
    val maxPixels: Long = 8_000_000,
    val crop: NormalizedRect? = null,
)

/** Honest reason a frame was not processed. */
enum class PreprocessFailureCode {
    INVALID_DIMENSIONS,
    PAYLOAD_MISMATCH,
    TOO_MANY_PIXELS,
    INVALID_CROP,
    OUTPUT_TOO_LARGE,
    UNSUPPORTED_FORMAT,
    RENDER_FAILED,
}

data class PreprocessFailure(
    val code: PreprocessFailureCode,
    val message: String,
)

sealed interface ProcessFrameResult {
    data class Success(val frame: ProcessedFrame) : ProcessFrameResult
    data class Failure(val reason: PreprocessFailure) : ProcessFrameResult
}

/** A processor that validates one frame and returns either a processed frame or a failure. */
fun interface FramePreprocessor {
    fun process(input: OriginalFrame, request: ProcessFrameRequest): ProcessFrameResult
}
