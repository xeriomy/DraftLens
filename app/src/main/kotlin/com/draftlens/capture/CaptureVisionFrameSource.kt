package com.draftlens.capture

import com.draftlens.models.CapturedFrame
import com.draftlens.models.CaptureSessionState
import com.draftlens.models.CaptureStatus
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.VisionFrameSource
import com.draftlens.vision.model.VisionSourceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The only code that knows about both capture and Vision.
 *
 * Flows are injected so tests can drive the adapter without the capture service.
 * The current values are mapped eagerly in the constructor so a caller that reads
 * [frames] or [state] right after construction sees the source's present value.
 */
class CaptureVisionFrameSource(
    captureFrames: StateFlow<CapturedFrame?>,
    captureState: StateFlow<CaptureSessionState>,
    scope: CoroutineScope,
) : VisionFrameSource {

    private val _frames = MutableStateFlow(captureFrames.value?.toVisionFrame())
    override val frames: StateFlow<OriginalFrame?> = _frames.asStateFlow()

    private val _state = MutableStateFlow(captureState.value.status.toVisionState())
    override val state: StateFlow<VisionSourceState> = _state.asStateFlow()

    init {
        scope.launch {
            captureFrames.collect { _frames.value = it?.toVisionFrame() }
        }
        scope.launch {
            captureState.collect { _state.value = it.status.toVisionState() }
        }
    }

    companion object {
        /** Production adapter over the process-local capture holder. */
        fun fromHolder(scope: CoroutineScope): CaptureVisionFrameSource =
            CaptureVisionFrameSource(CaptureStateHolder.latestFrame, CaptureStateHolder.state, scope)
    }
}

private fun CaptureStatus.toVisionState(): VisionSourceState = when (this) {
    CaptureStatus.CAPTURING -> VisionSourceState.CAPTURING
    CaptureStatus.PAUSED -> VisionSourceState.PAUSED
    CaptureStatus.IDLE -> VisionSourceState.IDLE
    CaptureStatus.STOPPING -> VisionSourceState.STOPPED
    CaptureStatus.ERROR -> VisionSourceState.ERROR
    CaptureStatus.AWAITING_PERMISSION, CaptureStatus.STARTING -> VisionSourceState.UNKNOWN
}
