package com.draftlens.capture

/**
 * Rate limiter for bounded capture diagnostics: the first few occurrences of each event
 * and then one line every [every] occurrences. No pixel data is ever logged.
 *
 * Keyed by event name, so "gate-reject:contentHidden" is counted separately from
 * "gate-reject:staleGeneration" — a log line can then answer whether ImageReader received
 * images and which gate rejected them.
 */
class CaptureDiagnostics(
    private val firstEvents: Int = 4,
    private val every: Int = 20,
) {
    private val counts = LinkedHashMap<String, Int>()

    /** Increments the event's counter and returns the new count. */
    fun sample(event: String): Int {
        val next = (counts[event] ?: 0) + 1
        counts[event] = next
        return next
    }

    fun shouldLog(count: Int): Boolean = count <= firstEvents || count % every == 0

    fun total(event: String): Int = counts[event] ?: 0

    fun reset() {
        counts.clear()
    }
}
