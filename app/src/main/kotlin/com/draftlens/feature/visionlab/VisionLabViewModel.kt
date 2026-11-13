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

    private val undoStack = ArrayDeque<AnnotationEdit>()
    private val redoStack = ArrayDeque<AnnotationEdit>()
    private var gestureBaseline: Pair<String, NormalizedRect>? = null

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

    /**
     * Releases the pinned reference and the processed frame, and cancels any processing
     * that is still in flight so a late result cannot restore itself.
     */
    fun clear() {
        generation.incrementAndGet()
        processingJob?.cancel()
        processingJob = null
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
        result.onSuccess { slot ->
            pushUndo(AnnotationEdit.Created(slot))
            annotationStore.select(slot.id)
        }
        refreshSlots()
        return result
    }

    /** Creates a slot drawn on the canvas and selects it. */
    fun createSlot(rect: NormalizedRect): Result<DraftSlot> =
        addSlot(SlotType.BAN, SlotTeam.ALLY, rect)

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
        val slot = annotationStore.slots().firstOrNull { it.id == id } ?: return
        if (annotationStore.delete(id)) {
            pushUndo(AnnotationEdit.Deleted(slot))
            refreshSlots()
        }
    }

    fun resetSlots() {
        annotationStore.reset()
        refreshSlots()
    }

    // endregion

    // region contextual property editor

    /** Opens the property editor of a slot. */
    fun openSlotMenu(id: String) {
        if (annotationStore.slots().none { it.id == id }) return
        _uiState.update { it.copy(menuSlotId = id) }
    }

    fun closeSlotMenu() {
        _uiState.update { it.copy(menuSlotId = null) }
    }

    // endregion

    // region clipboard

    /** Copies a slot into the application's own clipboard. */
    fun copySlot(id: String) {
        val slot = annotationStore.slots().firstOrNull { it.id == id } ?: return
        _uiState.update { it.copy(clipboardSlot = slot, message = "Copied ${slot.id.take(8)}") }
    }

    /**
     * Pastes the copied slot next to [targetSlotId], or next to the copied slot itself.
     * The pasted slot gets a new id and the next free index in its scope.
     */
    fun pasteSlot(targetSlotId: String? = null): Result<DraftSlot> {
        val copied = _uiState.value.clipboardSlot
            ?: return Result.failure(IllegalStateException("Nothing has been copied"))
        val source = targetSlotId?.let { id -> annotationStore.slots().firstOrNull { it.id == id } } ?: copied
        return duplicateFrom(source)
    }

    /** Duplicates a slot: a new id, a new index and a small offset. */
    fun duplicateSlot(id: String): Result<DraftSlot> {
        val source = annotationStore.slots().firstOrNull { it.id == id }
            ?: return Result.failure(IllegalArgumentException("Unknown slot id: $id"))
        return duplicateFrom(source)
    }

    private fun duplicateFrom(source: DraftSlot): Result<DraftSlot> {
        val rect = SlotPlacement.offsetRect(source.rect)
        val result = annotationStore.add(
            type = source.type,
            team = source.team,
            rect = rect,
            index = null,
            label = source.label,
        )
        result.onSuccess { slot ->
            pushUndo(AnnotationEdit.Created(slot))
            annotationStore.select(slot.id)
        }
        refreshSlots()
        return result
    }

    // endregion

    // region undo

    /** Records the rectangle of a slot before a move or resize gesture changes it. */
    fun beginSlotGesture(id: String) {
        gestureBaseline = annotationStore.slots().firstOrNull { it.id == id }?.let { id to it.rect }
    }

    /** Closes a move or resize gesture and records it as undoable when it changed anything. */
    fun endSlotGesture() {
        val baseline = gestureBaseline
        gestureBaseline = null
        if (baseline == null) return
        val current = annotationStore.slots().firstOrNull { it.id == baseline.first } ?: return
        if (current.rect == baseline.second) return
        pushUndo(AnnotationEdit.GeometryChanged(baseline.first, baseline.second, current.rect))
    }

    fun undo() {
        val edit = undoStack.removeLastOrNull() ?: return
        when (edit) {
            is AnnotationEdit.Deleted -> {
                val restored = annotationStore.restore(edit.slot)
                if (restored.isFailure) {
                    undoStack.add(edit)
                    report(restored.exceptionOrNull()?.message ?: "The deleted slot cannot be restored")
                    return
                }
                annotationStore.select(edit.slot.id)
            }

            is AnnotationEdit.Created -> annotationStore.delete(edit.slot.id)
            is AnnotationEdit.GeometryChanged -> annotationStore.resize(edit.slotId, edit.before)
        }
        redoStack.add(edit)
        refreshSlots()
    }

    fun redo() {
        val edit = redoStack.removeLastOrNull() ?: return
        when (edit) {
            is AnnotationEdit.Deleted -> annotationStore.delete(edit.slot.id)
            is AnnotationEdit.Created -> {
                val restored = annotationStore.restore(edit.slot)
                if (restored.isFailure) {
                    redoStack.add(edit)
                    report(restored.exceptionOrNull()?.message ?: "The slot cannot be restored")
                    return
                }
                annotationStore.select(edit.slot.id)
            }

            is AnnotationEdit.GeometryChanged -> annotationStore.resize(edit.slotId, edit.after)
        }
        undoStack.add(edit)
        refreshSlots()
    }

    private fun pushUndo(edit: AnnotationEdit) {
        undoStack.add(edit)
        while (undoStack.size > MAX_UNDO) undoStack.removeAt(0)
        redoStack.clear()
        _uiState.update { it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false) }
    }

    // endregion

    // region fullscreen editor

    fun openFullscreenEditor() {
        _uiState.update { it.copy(fullscreenEditor = true) }
    }

    fun closeFullscreenEditor() {
        _uiState.update { it.copy(fullscreenEditor = false) }
    }

    // endregion

    private fun report(message: String?) {
        if (message == null) return
        _uiState.update { it.copy(message = message) }
    }

    private fun refreshSlots() {
        _uiState.update {
            it.copy(
                slots = annotationStore.slots(),
                selectedSlotId = annotationStore.selectedSlotId(),
                slotIssues = annotationStore.validate(),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
    }

    // region calibration profiles

    /** Saves the current manual slots as a new profile for the displayed frame. */
    fun saveProfile(label: String?) {
        val state = _uiState.value
        if (state.calibration !is CalibrationLoadResult.Success) {
            _uiState.update { it.copy(message = UNSUPPORTED_SCHEMA_MESSAGE) }
            return
        }
        val frame = state.frozenFrame
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
        val frame = state.frozenFrame
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
        val frame = state.frozenFrame
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
        const val MAX_UNDO = 50
    }
}

/** Dimensions after the pixel-orientation correction, before crop and scale. */
internal fun OriginalFrame.orientedDimensions(): Pair<Int, Int> =
    when (rotation) {
        FrameRotation.ROTATION_90, FrameRotation.ROTATION_270 -> sourceHeight to sourceWidth
        else -> sourceWidth to sourceHeight
    }
