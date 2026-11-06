package com.draftlens.detection

/**
 * Sprint 0: interfaces only. Every impl below throws/returns NotImplemented.
 * Production code must never fabricate a detection result. See docs/ARCHITECTURE.md.
 */

/** Abstracts "there is a screen and frames are flowing". */
interface ScreenCaptureProvider {
    val isCapturing: Boolean
    fun start()
    fun stop()
}

/** Consumes captured frames for ML or heuristic analysis. */
interface FrameProcessor {
    /** Returns a human-readable status; Sprint 0 always NOT_IMPLEMENTED. */
    fun process(frame: FrameRef): ProcessorResult
}

data class FrameRef(val timestampMs: Long, val width: Int, val height: Int)

enum class ProcessorResult { NOT_IMPLEMENTED, OK, ERROR }

/** Detects MLBB draft phase from a frame (bans/picks/timer). */
interface DraftPhaseDetector {
    fun detect(frame: FrameRef): DetectionResult<DraftPhase>
}

/** Recognizes which hero occupies a slot. */
interface HeroRecognizer {
    fun recognize(slot: HeroSlot): DetectionResult<HeroIdentity>
}

/** Locates hero portrait slots on the draft screen. */
interface HeroSlotDetector {
    fun findSlots(frame: FrameRef): DetectionResult<List<HeroSlot>>
}

/** Cross-checks a detection against history/rules before it is trusted. */
interface DetectionValidator {
    fun validate(candidate: DetectionResult<*>): Boolean
}

// ---- Value types (no behavior) ----

enum class DraftPhase { UNKNOWN, BANNING, PICKING, LOADING, IN_GAME, NOT_IMPLEMENTED }

data class HeroSlot(val id: Int, val x: Int, val y: Int, val width: Int, val height: Int, val side: TeamSide)
enum class TeamSide { ALLY, ENEMY }

data class HeroIdentity(val heroId: Int?, val displayName: String?, val confidence: Float)

sealed interface DetectionResult<out T> {
    data class Found<T>(val value: T, val confidence: Float) : DetectionResult<T>
    data class NotImplemented<T>(val reason: String = "No recognition model in Sprint 0") : DetectionResult<T>
    data class Unavailable<T>(val reason: String) : DetectionResult<T>
    data class Failed<T>(val error: String) : DetectionResult<T>
}

/** Sprint 0 stubs: always NotImplemented. Never fake a result. */
class UnavailableDraftPhaseDetector : DraftPhaseDetector {
    override fun detect(frame: FrameRef) = DetectionResult.NotImplemented<DraftPhase>()
}

class UnavailableHeroRecognizer : HeroRecognizer {
    override fun recognize(slot: HeroSlot) = DetectionResult.NotImplemented<HeroIdentity>()
}

class UnavailableHeroSlotDetector : HeroSlotDetector {
    override fun findSlots(frame: FrameRef) = DetectionResult.NotImplemented<List<HeroSlot>>()
}

class UnavailableFrameProcessor : FrameProcessor {
    override fun process(frame: FrameRef) = ProcessorResult.NOT_IMPLEMENTED
}

class UnavailableDetectionValidator : DetectionValidator {
    override fun validate(candidate: DetectionResult<*>): Boolean = false
}
