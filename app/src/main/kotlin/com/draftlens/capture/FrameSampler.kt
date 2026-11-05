package com.draftlens.capture

/**
 * Throttles frame emission to at most [maxFps] and at least [minIntervalMs] apart.
 * Pure JVM logic so it is unit-testable without Android.
 */
class FrameSampler(
    minIntervalMs: Long,
    val maxFps: Int = MAX_FPS
) {
    init {
        require(minIntervalMs >= MIN_INTERVAL_MS) { "interval $minIntervalMs below min $MIN_INTERVAL_MS" }
        require(minIntervalMs <= MAX_INTERVAL_MS) { "interval $minIntervalMs above max $MAX_INTERVAL_MS" }
        require(maxFps in 1..MAX_FPS) { "maxFps out of range" }
    }

    var minIntervalMs = minIntervalMs
        private set

    private var lastEmittedAtMs: Long = Long.MIN_VALUE

    fun setIntervalMs(requestedMs: Long) {
        minIntervalMs = clampInterval(requestedMs)
        reset()
    }

    fun shouldEmit(nowMs: Long): Boolean {
        if (lastEmittedAtMs == Long.MIN_VALUE) return true
        val elapsed = nowMs - lastEmittedAtMs
        return elapsed >= minIntervalMs
    }

    fun markEmitted(nowMs: Long) {
        lastEmittedAtMs = nowMs
    }

    fun reset() {
        lastEmittedAtMs = Long.MIN_VALUE
    }

    fun effectiveFps(): Double = 1000.0 / minIntervalMs

    companion object {
        const val DEFAULT_INTERVAL_MS = 500L   // 2 FPS
        const val MIN_INTERVAL_MS = 334L       // ~3 FPS cap
        const val MAX_INTERVAL_MS = 2000L      // 0.5 FPS floor
        const val MAX_FPS = 3

        fun clampInterval(requestedMs: Long): Long =
            requestedMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }
}
