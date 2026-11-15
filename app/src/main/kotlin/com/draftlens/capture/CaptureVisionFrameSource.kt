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
 *
 * [freeze]/[resume] delegate to [CaptureFreeze], exactly like the notification action.
 */
class CaptureVisionFrameSource(
    captureFrames: StateFlow<CapturedFrame?>,
    captureState: StateFlow<CaptureSessionState>,
    scope: CoroutineScope,
    captureFrozen: StateFlow<CapturedFrame?> = MutableStateFlow(null),
) : VisionFrameSource {

    private val _frames = MutableStateFlow(captureFrames.value?.toVisionFrame())
    override val frames: StateFlow<OriginalFrame?> = _frames.asStateFlow()

    private val _state = MutableStateFlow(captureState.value.status.toVisionState())
    override val state: StateFlow<VisionSourceState> = _state.asStateFlow()

    private val _frozenFrame = MutableStateFlow(captureFrozen.value?.toVisionFrame())
    override val frozenFrame: StateFlow<OriginalFrame?> = _frozenFrame.asStateFlow()

    init {
        scope.launch {
            captureFrames.collect { _frames.value = it?.toVisionFrame() }
        }
        scope.launch {
            captureState.collect { _state.value = it.status.toVisionState() }
        }
        scope.launch {
            captureFrozen.collect { _frozenFrame.value = it?.toVisionFrame() }
        }
    }

    override fun freeze(): Boolean = when (val outcome = CaptureFreeze.freezeCurrentFrame()) {
        is FreezeOutcome.Frozen, FreezeOutcome.AlreadyFrozen -> true
        FreezeOutcome.NoFrame, FreezeOutcome.NoPipeline -> false
    }

    override fun resume() = CaptureFreeze.resumeLive()

    companion object {
        /** Production adapter over the process-local capture holder. */
        fun fromHolder(scope: CoroutineScope): CaptureVisionFrameSource =
            CaptureVisionFrameSource(
                CaptureStateHolder.latestFrame,
                CaptureStateHolder.state,
                scope,
                CaptureStateHolder.frozenFrame,
            )
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
