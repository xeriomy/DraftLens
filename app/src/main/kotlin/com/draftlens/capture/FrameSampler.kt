package com.draftlens.capture

/**
 * Throttles frame emission to at most [maxFps] and at least [minIntervalMs] apart.
 * Pure JVM logic so it is unit-testable without Android.
 *
 * Sampling uses a monotonic time source: [System.nanoTime] by default.
 * [CaptureSessionService] injects `SystemClock.elapsedRealtime()` on Android.
 * Wall-clock time is not used for sampling, though callers may record it separately.
 */
class FrameSampler(
    minIntervalMs: Long,
    val maxFps: Int = MAX_FPS,
    private val timeSource: () -> Long = { System.nanoTime() / NANOS_PER_MS }
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

    fun currentTimeMs(): Long = timeSource()

    fun shouldEmit(nowMs: Long = currentTimeMs()): Boolean {
        if (lastEmittedAtMs == Long.MIN_VALUE) return true
        val elapsed = nowMs - lastEmittedAtMs
        return elapsed >= minIntervalMs
    }

    fun markEmitted(nowMs: Long = currentTimeMs()) {
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
        private const val NANOS_PER_MS = 1_000_000L

        fun clampInterval(requestedMs: Long): Long =
            requestedMs.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS)
    }
}
