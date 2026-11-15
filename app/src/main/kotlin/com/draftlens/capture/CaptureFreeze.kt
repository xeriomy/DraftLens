package com.draftlens.capture

import com.draftlens.models.CapturedFrame

/**
 * Process-wide bridge for the freeze workflow. The capture service installs the live
 * pipeline here; the Vision Lab reaches the same commands through [CaptureVisionFrameSource].
 * Both triggers run the exact same code path, so a notification tap and the in-app button
 * behave identically.
 *
 * Never creates a capture session: with no active pipeline the commands are no-ops.
 */
object CaptureFreeze {

    @Volatile
    private var pipeline: FramePipeline? = null

    /** Called by the capture service once its pipeline exists. */
    fun attachPipeline(instance: FramePipeline) {
        pipeline = instance
    }

    /** Called by the capture service during teardown; a stale reference is dropped. */
    fun detachPipeline(instance: FramePipeline) {
        synchronized(this) {
            if (pipeline === instance) pipeline = null
        }
    }

    /** True when an active pipeline is installed (i.e. capture is running). */
    fun isCapturing(): Boolean = pipeline != null

    fun freezeCurrentFrame(): FreezeOutcome {
        val current = CaptureStateHolder.frozenFrame.value
        if (current != null) return FreezeOutcome.AlreadyFrozen
        val active = pipeline ?: return FreezeOutcome.NoPipeline
        return when (val result = active.freezeAndSnapshot()) {
            is FramePipeline.FreezeResult.Frozen -> {
                CaptureStateHolder.setFrozenFrame(result.frame)
                FreezeOutcome.Frozen(result.frame)
            }

            FramePipeline.FreezeResult.AlreadyFrozen -> FreezeOutcome.AlreadyFrozen
            FramePipeline.FreezeResult.NoFrame -> FreezeOutcome.NoFrame
        }
    }

    /** Pins the in-memory frame; resume reveals live frames again. Never starts a session. */
    fun resumeLive() {
        pipeline?.resumeCapture()
        CaptureStateHolder.clearFrozenFrame()
    }
}

/** What a freeze command achieved. */
sealed interface FreezeOutcome {
    /** A newly pinned frame, independently owned by the holder. */
    data class Frozen(val frame: CapturedFrame) : FreezeOutcome

    /** A frame was already pinned; the existing one is kept. */
    data object AlreadyFrozen : FreezeOutcome

    /** Capture is running but no frame has been captured yet. */
    data object NoFrame : FreezeOutcome

    /** No active capture (e.g. a stale notification action after a stop). */
    data object NoPipeline : FreezeOutcome
}
