package com.draftlens.feature.visionlab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotAnnotationStore
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.calibration.CalibrationCompatibility
import com.draftlens.vision.calibration.CalibrationLoadResult
import com.draftlens.vision.calibration.CalibrationProfile
import com.draftlens.vision.calibration.CalibrationRepository
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.OrientationCategory
import com.draftlens.vision.model.VisionFrameSource
import com.draftlens.vision.preprocessing.FramePreprocessor
import com.draftlens.vision.preprocessing.ProcessFrameRequest
import com.draftlens.vision.preprocessing.ProcessFrameResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/**
 * State holder for the Vision Lab.
 *
 * Frames are consumed through [VisionFrameSource]; processing is explicit and
 * happens only when the user asks for it. There is no recognizer in this sprint:
 * the processed frame is pixels plus geometry, never a detection.
 */
class VisionLabViewModel(
    private val source: VisionFrameSource,
    private val preprocessor: FramePreprocessor,
    private val calibrationRepository: CalibrationRepository,
    private val annotationStore: SlotAnnotationStore = SlotAnnotationStore(idGenerator = { UUID.randomUUID().toString() }),
) : ViewModel() {

    private val _uiState = MutableStateFlow(VisionLabUiState())
    val uiState: StateFlow<VisionLabUiState> = _uiState.asStateFlow()

    /** Incremented per request; a result whose generation is stale is discarded. */
    private val generation = AtomicLong(0L)
    private var processingJob: Job? = null

    init {
        viewModelScope.launch {
            source.frames.collect { frame -> _uiState.update { it.copy(liveFrame = frame) } }
        }
        viewModelScope.launch {
            source.state.collect { state -> _uiState.update { it.copy(sourceState = state) } }
        }
        viewModelScope.launch {
            calibrationRepository.profiles.collect { result -> _uiState.update { it.copy(calibration = result) } }
        }
        refreshSlots()
    }

    // region frame control

    /** Pins the frame that is on screen; live frames keep arriving but are not shown. */
    fun freeze() {
        val frame = source.frames.value ?: return
        _uiState.update { it.copy(frozenFrame = frame) }
    }

    /** Releases the pinned frame and shows live frames again. */
    fun resume() {
        _uiState.update { it.copy(frozenFrame = null) }
    }

    /** Releases the pinned reference and the processed frame. */
    fun clear() {
        _uiState.update {
            it.copy(frozenFrame = null, processed = null, processStatus = ProcessStatus.IDLE, processError = null)
        }
    }

    fun setMaxProcessedDimension(value: Int) {
        if (value <= 0) return
        _uiState.update { it.copy(maxProcessedDimension = value) }
    }

    // endregion

    // region processing

    /**
     * Processes the pinned frame, or the latest live frame when nothing is pinned.
     * The frame is captured here so a newer frame cannot swap in mid-flight.
     */
    fun processFrame() {
        val state = _uiState.value
        val frame = state.frozenFrame ?: source.frames.value
        if (frame == null) {
            _uiState.update {
                it.copy(processStatus = ProcessStatus.FAILED, processError = "There is no frame to process")
            }
            return
        }
        val request = ProcessFrameRequest(maxProcessedDimension = state.maxProcessedDimension)
        val currentGeneration = generation.incrementAndGet()
        processingJob?.cancel()
        _uiState.update {
            it.copy(
                processStatus = ProcessStatus.RUNNING,
                processError = null,
                processingGeneration = currentGeneration,
            )
        }
        processingJob = viewModelScope.launch(Dispatchers.Default) {
            val result = preprocessor.process(frame, request)
            if (currentGeneration != generation.get()) return@launch
            when (result) {
                is ProcessFrameResult.Success -> _uiState.update {
                    it.copy(processed = result.frame, processStatus = ProcessStatus.SUCCESS, processError = null)
                }
                is ProcessFrameResult.Failure -> _uiState.update {
                    it.copy(processStatus = ProcessStatus.FAILED, processError = result.reason.message)
                }
            }
        }
    }

    // endregion

    // region manual slots

    fun addSlot(
        type: SlotType,
        team: SlotTeam,
        rect: NormalizedRect,
        index: Int? = null,
        label: String? = null,
    ): Result<DraftSlot> {
        val result = annotationStore.add(type, team, rect, index, label)
        refreshSlots()
        return result
    }

    fun moveSlot(id: String, deltaX: Float, deltaY: Float): Result<DraftSlot> =
        annotationStore.move(id, deltaX, deltaY).also { refreshSlots() }

    fun resizeSlot(id: String, rect: NormalizedRect): Result<DraftSlot> =
        annotationStore.resize(id, rect).also { refreshSlots() }

    fun updateSlotTeam(id: String, team: SlotTeam): Result<DraftSlot> =
        annotationStore.updateTeam(id, team).also { refreshSlots() }

    fun updateSlotType(id: String, type: SlotType): Result<DraftSlot> =
        annotationStore.updateType(id, type).also { refreshSlots() }

    fun updateSlotIndex(id: String, index: Int): Result<DraftSlot> =
        annotationStore.updateIndex(id, index).also { refreshSlots() }

    fun updateSlotLabel(id: String, label: String?): Result<DraftSlot> =
        annotationStore.updateLabel(id, label).also { refreshSlots() }

    fun selectSlot(id: String?) {
        runCatching { annotationStore.select(id) }
        refreshSlots()
    }

    fun deleteSlot(id: String) {
        annotationStore.delete(id)
        refreshSlots()
    }

    fun resetSlots() {
        annotationStore.reset()
        refreshSlots()
    }

    private fun refreshSlots() {
        _uiState.update {
            it.copy(
                slots = annotationStore.slots(),
                selectedSlotId = annotationStore.selectedSlotId(),
                slotIssues = annotationStore.validate(),
            )
        }
    }

    // endregion

    // region calibration profiles

    /** Saves the current manual slots as a new profile for the displayed frame. */
    fun saveProfile(label: String?) {
        val state = _uiState.value
        if (state.calibration !is CalibrationLoadResult.Success) {
            _uiState.update { it.copy(message = UNSUPPORTED_SCHEMA_MESSAGE) }
            return
        }
        val frame = state.displayFrame
        if (frame == null) {
            _uiState.update { it.copy(message = "Freeze a frame before saving a profile") }
            return
        }
        val (width, height) = frame.orientedDimensions()
        val now = System.currentTimeMillis()
        val profile = CalibrationProfile(
            profileId = UUID.randomUUID().toString(),
            label = label?.takeIf { it.isNotBlank() },
            targetAspectRatio = width.toDouble() / height.toDouble(),
            expectedOrientation = OrientationCategory.from(width, height),
            createdAtMs = now,
            updatedAtMs = now,
            slots = annotationStore.slots(),
        )
        viewModelScope.launch {
            val result = calibrationRepository.upsert(profile)
            _uiState.update { it.copy(message = result.messageFor("Profile saved", "Profile could not be saved")) }
        }
    }

    /** Rewrites an existing profile with the current slots and label. */
    fun updateProfile(profileId: String, label: String?) {
        val state = _uiState.value
        val profiles = (state.calibration as? CalibrationLoadResult.Success)?.profiles
        if (profiles == null) {
            _uiState.update { it.copy(message = UNSUPPORTED_SCHEMA_MESSAGE) }
            return
        }
        val existing = profiles.firstOrNull { it.profileId == profileId } ?: return
        val frame = state.displayFrame
        if (frame == null) {
            _uiState.update { it.copy(message = "Freeze a frame before updating a profile") }
            return
        }
        val (width, height) = frame.orientedDimensions()
        val updated = existing.copy(
            label = label?.takeIf { it.isNotBlank() },
            targetAspectRatio = width.toDouble() / height.toDouble(),
            expectedOrientation = OrientationCategory.from(width, height),
            updatedAtMs = System.currentTimeMillis(),
            slots = annotationStore.slots(),
        )
        viewModelScope.launch {
            val result = calibrationRepository.upsert(updated)
            _uiState.update { it.copy(message = result.messageFor("Profile updated", "Profile could not be updated")) }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            val result = calibrationRepository.delete(profileId)
            _uiState.update { it.copy(message = result.messageFor("Profile deleted", "Profile could not be deleted")) }
        }
    }

    /** The escape hatch for a document this build cannot read. */
    fun deleteAllProfiles() {
        viewModelScope.launch {
            val result = calibrationRepository.clearDocument()
            _uiState.update { it.copy(message = result.messageFor("Calibration data cleared", "Calibration data could not be cleared")) }
        }
    }

    /** Applies a profile to the manual slots after a compatibility check. */
    fun applyProfile(profileId: String) {
        val state = _uiState.value
        val profiles = (state.calibration as? CalibrationLoadResult.Success)?.profiles
        if (profiles == null) {
            _uiState.update { it.copy(message = UNSUPPORTED_SCHEMA_MESSAGE) }
            return
        }
        val profile = profiles.firstOrNull { it.profileId == profileId } ?: return
        val frame = state.displayFrame
        if (frame == null) {
            _uiState.update { it.copy(message = "Freeze a frame before applying a profile") }
            return
        }
        val (width, height) = frame.orientedDimensions()
        val compatibility = CalibrationCompatibility.check(profile, width, height)
        if (compatibility is CalibrationCompatibility.Incompatible) {
            _uiState.update { it.copy(message = compatibility.message) }
            return
        }
        val duplicateIndexes = profile.slots
            .groupBy { Triple(it.type, it.team, it.index) }
            .filterValues { it.size > 1 }
        if (duplicateIndexes.isNotEmpty()) {
            _uiState.update { it.copy(message = "The profile contains duplicate slot indexes") }
            return
        }
        // Refuse before touching the current slots, so a profile is applied whole or not at all.
        val unusable = profile.slots.firstOrNull { slot ->
            slot.rect.width < SlotAnnotationStore.MIN_SIZE ||
                slot.rect.height < SlotAnnotationStore.MIN_SIZE ||
                slot.index < 1
        }
        if (unusable != null) {
            _uiState.update {
                it.copy(message = "The profile contains a slot that is too small or has an invalid index")
            }
            return
        }
        annotationStore.reset()
        var failure: String? = null
        profile.slots.forEach { slot ->
            val result = annotationStore.add(slot.type, slot.team, slot.rect, slot.index, slot.label)
            if (result.isFailure) failure = result.exceptionOrNull()?.message
        }
        refreshSlots()
        _uiState.update {
            it.copy(message = failure ?: "Applied profile ${profile.label ?: profile.profileId}")
        }
    }

    private fun Result<Unit>.messageFor(success: String, failure: String): String =
        if (isSuccess) success else exceptionOrNull()?.message ?: failure

    // endregion

    override fun onCleared() {
        generation.incrementAndGet()
        processingJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val UNSUPPORTED_SCHEMA_MESSAGE =
            "The stored calibration data uses an unsupported schema. Delete all calibration data to continue."
    }
}

/** Dimensions after the pixel-orientation correction, before crop and scale. */
internal fun OriginalFrame.orientedDimensions(): Pair<Int, Int> =
    when (rotation) {
        FrameRotation.ROTATION_90, FrameRotation.ROTATION_270 -> sourceHeight to sourceWidth
        else -> sourceWidth to sourceHeight
    }
