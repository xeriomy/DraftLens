package com.draftlens.feature.visionlab

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotIssue
import com.draftlens.vision.calibration.CalibrationLoadResult
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.ProcessedFrame
import com.draftlens.vision.model.VisionSourceState

/** Progress of the single explicit processing job. */
enum class ProcessStatus { IDLE, RUNNING, SUCCESS, FAILED }

/**
 * Everything the Vision Lab renders. No detection data: this sprint has no recognizer.
 */
data class VisionLabUiState(
    val liveFrame: OriginalFrame? = null,
    val sourceState: VisionSourceState = VisionSourceState.UNKNOWN,
    val frozenFrame: OriginalFrame? = null,
    val processed: ProcessedFrame? = null,
    val processStatus: ProcessStatus = ProcessStatus.IDLE,
    val processError: String? = null,
    val processingGeneration: Long = 0L,
    val slots: List<DraftSlot> = emptyList(),
    val selectedSlotId: String? = null,
    val slotIssues: List<SlotIssue> = emptyList(),
    val calibration: CalibrationLoadResult = CalibrationLoadResult.Success(emptyList()),
    val maxProcessedDimension: Int = DEFAULT_MAX_PROCESSED_DIMENSION,
    val message: String? = null,
) {
    /** The frame the canvas draws: the pinned one when frozen, otherwise the live one. */
    val displayFrame: OriginalFrame? get() = frozenFrame ?: liveFrame

    val isFrozen: Boolean get() = frozenFrame != null

    /** A document newer than this build supports cannot be written to. */
    val canWriteProfiles: Boolean get() = calibration is CalibrationLoadResult.Success

    companion object {
        const val DEFAULT_MAX_PROCESSED_DIMENSION = 1280
    }
}
