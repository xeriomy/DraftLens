package com.draftlens.capture

import com.draftlens.models.CapturedFrame
import com.draftlens.models.CaptureSessionState
import com.draftlens.models.CaptureStatus
import com.draftlens.models.CaptureTransitions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-process bridge between [CaptureSessionService] and UI.
 * Static process memory is intentional: no cross-process persistence, and process death clears it.
 *
 * State cleanup APIs are intentionally separate:
 * - [clearLatestFrame]: removes the latest-frame reference only.
 * - [resetSessionMetrics]: clears counters, timing, dimensions and permission metadata only.
 * - [reset]: replaces the whole session state with a fresh idle state.
 * - [terminateSession]: shared terminal path; preserves ERROR unless the user explicitly requests a clean stop.
 */
object CaptureStateHolder {
    private val _state = MutableStateFlow(CaptureSessionState())
    val state = _state.asStateFlow()

    private val _latestFrame = MutableStateFlow<CapturedFrame?>(null)
    val latestFrame = _latestFrame.asStateFlow()

    /**
     * The frame pinned by a freeze command, owned independently of the live pipeline.
     * At most one instance is kept. Survives MediaProjection stopping (release of the
     * VirtualDisplay/ImageReader does not touch it) but not process death; cleared only
     * by resume/clear or by starting a new capture session.
     */
    private val _frozenFrame = MutableStateFlow<CapturedFrame?>(null)
    val frozenFrame = _frozenFrame.asStateFlow()

    fun setStatus(target: CaptureStatus, error: String? = null) {
        _state.update { current ->
            if (CaptureTransitions.canTransition(current.status, target)) {
                CaptureTransitions.transition(current, target, error)
            } else {
                current.copy(status = target, lastError = error ?: current.lastError)
            }
        }
    }

    fun beginCapture(width: Int, height: Int, densityDpi: Int) {
        clearLatestFrame()
        // A new session starts with live frames; the previous session's pinned frame is dropped.
        _frozenFrame.value = null
        resetSessionMetrics()
        _state.update { current ->
            val base = if (CaptureTransitions.canTransition(current.status, CaptureStatus.CAPTURING)) {
                CaptureTransitions.transition(current, CaptureStatus.CAPTURING)
            } else {
                current.copy(status = CaptureStatus.CAPTURING)
            }
            base.copy(
                hasPermission = true,
                displayWidth = width,
                displayHeight = height,
                densityDpi = densityDpi,
                lastError = null,
                startedAtMs = System.currentTimeMillis()
            )
        }
    }

    fun updateDisplay(width: Int, height: Int, densityDpi: Int) {
        _state.update {
            it.copy(displayWidth = width, displayHeight = height, densityDpi = densityDpi)
        }
    }

    fun recordFrame(frame: CapturedFrame) {
        _latestFrame.value = frame
        _state.update {
            it.copy(
                frameCount = it.frameCount + 1,
                lastFrameWallClockMs = frame.wallClockTimestampMs,
                lastFrameProcessingLatencyMs = frame.processingLatencyMs,
                displayWidth = frame.width,
                displayHeight = frame.height
            )
        }
    }

    fun setPaused(paused: Boolean) {
        _state.update { current ->
            val target = if (paused) CaptureStatus.PAUSED else CaptureStatus.CAPTURING
            if (CaptureTransitions.canTransition(current.status, target)) {
                CaptureTransitions.transition(current, target).copy(isPaused = paused)
            } else current
        }
    }

    /**
     * Shared terminal cleanup for all capture-ending paths.
     *
     * The pinned frame is deliberately preserved: a projection stopped by Android (or the
     * user) must leave an independently owned copy available for editing while the process
     * lives. Only a new capture session, an explicit resume, or [reset] clears it.
     *
     * If [error] is supplied and the current state is not already ERROR, transition to ERROR
     * with that message. An existing ERROR and its message are preserved unless [forceIdle]
     * is requested by an explicit user Stop action. Normal termination ([error] = null and
     * [forceIdle] = false) ends in IDLE when no ERROR already exists.
     */
    fun terminateSession(error: String? = null, forceIdle: Boolean = false) {
        if (error != null && _state.value.status != CaptureStatus.ERROR) {
            setStatus(CaptureStatus.ERROR, error)
        }
        clearLatestFrame()
        resetSessionMetrics()
        if (forceIdle || _state.value.status != CaptureStatus.ERROR) {
            setStatus(CaptureStatus.IDLE)
        }
    }

    fun clearLatestFrame() {
        _latestFrame.value = null
    }

    /** Pins [frame] as the frozen frame, replacing any previous one. */
    fun setFrozenFrame(frame: CapturedFrame) {
        _frozenFrame.value = frame
    }

    /** Releases the pinned frame; live publication may resume. */
    fun clearFrozenFrame() {
        _frozenFrame.value = null
    }

    /**
     * Clears session counters, timings, dimensions and permission metadata.
     * Deliberately preserves status and lastError; use [reset] for a full idle reset.
     */
    fun resetSessionMetrics() {
        _state.update {
            it.copy(
                hasPermission = false,
                displayWidth = 0,
                displayHeight = 0,
                densityDpi = 0,
                frameCount = 0,
                lastFrameWallClockMs = null,
                lastFrameProcessingLatencyMs = null,
                startedAtMs = null,
                isPaused = false
            )
        }
    }

    /** Full state reset to a fresh idle state. */
    fun reset() {
        clearLatestFrame()
        _frozenFrame.value = null
        _state.value = CaptureSessionState()
    }
}
