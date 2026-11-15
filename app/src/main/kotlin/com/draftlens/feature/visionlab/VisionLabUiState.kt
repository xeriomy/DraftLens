package com.draftlens.feature.visionlab

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotIssue
import com.draftlens.vision.calibration.CalibrationLoadResult
import com.draftlens.vision.heroes.HeroCandidate
import com.draftlens.vision.heroes.HeroCatalogState
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.ProcessedFrame
import com.draftlens.vision.model.VisionSourceState

/** Progress of the single explicit processing job. */
enum class ProcessStatus { IDLE, RUNNING, SUCCESS, FAILED }

/**
 * Everything the Vision Lab renders. Recognition runs only on the user's explicit
 * Identify action; nothing here is a continuous detection.
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
    /** The slot whose contextual property editor is open, if any. */
    val menuSlotId: String? = null,
    /** The application's own slot clipboard; null when nothing was copied. */
    val clipboardSlot: DraftSlot? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** True while the fullscreen annotation editor is open. */
    val fullscreenEditor: Boolean = false,
    /** The hero catalog state as the UI shows it. */
    val heroCatalog: HeroCatalogState = HeroCatalogState.NotSynced,
    /** Recognition state per slot id. Only slots the user ran Identify on appear here. */
    val heroRecognition: Map<String, HeroSlotUiState> = emptyMap(),
    /** The extracted crop for preview; Android only, null for synthetic payloads. */
    val heroPreview: android.graphics.Bitmap? = null,
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

/** Phase of a slot's hero recognition flow. */
enum class HeroSlotPhase { IDLE, PROCESSING, CANDIDATES, NO_MATCH, CONFIRMED, FAILED, INSUFFICIENT_INFO }

/** Recognition state of one slot. */
data class HeroSlotUiState(
    val phase: HeroSlotPhase,
    /** Ranked candidates; meaningful only in [HeroSlotPhase.CANDIDATES]. */
    val candidates: List<HeroCandidate> = emptyList(),
    /** Best similarity seen in [HeroSlotPhase.NO_MATCH]; null elsewhere. */
    val bestSimilarity: Float? = null,
    /** User-facing message for [HeroSlotPhase.FAILED] and [HeroSlotPhase.INSUFFICIENT_INFO]; also used for the confirmed hero's name in [HeroSlotPhase.CONFIRMED]. */
    val message: String? = null,
    /** The confirmed hero id in [HeroSlotPhase.CONFIRMED]. */
    val confirmedHeroId: String? = null,
    /** Wall-clock confirmation time in [HeroSlotPhase.CONFIRMED]. */
    val confirmedAtMs: Long? = null,
)
