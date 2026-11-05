package com.draftlens.models

/**
 * Immutable snapshot of a capture session. Single source of truth for UI + service.
 * States mirror ANR-safe lifecycle: never throws, always has a terminal representation.
 */
data class CaptureSessionState(
    val status: CaptureStatus = CaptureStatus.IDLE,
    val hasPermission: Boolean = false,
    val displayWidth: Int = 0,
    val displayHeight: Int = 0,
    val densityDpi: Int = 0,
    val frameCount: Long = 0,
    val lastFrameTimestampMs: Long? = null,
    val lastLatencyMs: Long? = null,
    val isPaused: Boolean = false,
    val lastError: String? = null,
    val startedAtMs: Long? = null,
    val detectionStatus: String = "Not implemented in Sprint 0"
) {
    val isCapturing: Boolean get() = status == CaptureStatus.CAPTURING || status == CaptureStatus.PAUSED
    val isStartBlocking: Boolean get() = status != CaptureStatus.IDLE && status != CaptureStatus.ERROR
}

enum class CaptureStatus {
    IDLE,            // never started or cleanly stopped
    AWAITING_PERMISSION, // system consent dialog in flight
    STARTING,        // service foregrounding / projection created
    CAPTURING,       // frames flowing
    PAUSED,          // frames throttled by user, resources retained
    STOPPING,        // intentional teardown in progress
    ERROR            // terminal error until reset
}

/** Allowed transitions; keeps service + ViewModel honest. */
object CaptureTransitions {
    private val allowed: Map<CaptureStatus, Set<CaptureStatus>> = mapOf(
        CaptureStatus.IDLE to setOf(CaptureStatus.AWAITING_PERMISSION, CaptureStatus.STARTING, CaptureStatus.ERROR),
        CaptureStatus.AWAITING_PERMISSION to setOf(CaptureStatus.STARTING, CaptureStatus.IDLE, CaptureStatus.ERROR),
        CaptureStatus.STARTING to setOf(CaptureStatus.CAPTURING, CaptureStatus.ERROR, CaptureStatus.IDLE),
        CaptureStatus.CAPTURING to setOf(CaptureStatus.PAUSED, CaptureStatus.STOPPING, CaptureStatus.ERROR),
        CaptureStatus.PAUSED to setOf(CaptureStatus.CAPTURING, CaptureStatus.STOPPING, CaptureStatus.ERROR),
        CaptureStatus.STOPPING to setOf(CaptureStatus.IDLE, CaptureStatus.ERROR),
        CaptureStatus.ERROR to setOf(CaptureStatus.IDLE, CaptureStatus.AWAITING_PERMISSION, CaptureStatus.STARTING)
    )

    fun canTransition(from: CaptureStatus, to: CaptureStatus): Boolean =
        from == to || allowed[from]?.contains(to) == true

    fun transition(state: CaptureSessionState, to: CaptureStatus, error: String? = null): CaptureSessionState {
        require(canTransition(state.status, to)) { "Illegal transition ${state.status} -> $to" }
        return state.copy(
            status = to,
            lastError = if (to == CaptureStatus.ERROR) error ?: state.lastError else if (to != CaptureStatus.ERROR) null else state.lastError,
            startedAtMs = if (to == CaptureStatus.CAPTURING) System.currentTimeMillis() else state.startedAtMs,
            isPaused = if (to == CaptureStatus.PAUSED) true else if (to == CaptureStatus.CAPTURING) false else state.isPaused
        )
    }
}
