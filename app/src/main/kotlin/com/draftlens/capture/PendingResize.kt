package com.draftlens.capture

/**
 * Stores the latest captured-content dimensions reported during startup, when the
 * VirtualDisplay/pipeline/session state are not ready for a resize. Later callbacks replace
 * earlier ones; invalid dimensions are ignored so a bogus early value cannot size the reader.
 */
class PendingResize {

    private val lock = Any()
    private var width: Int = 0
    private var height: Int = 0
    private var sequence: Long = 0L

    fun offer(newWidth: Int, newHeight: Int): Long = synchronized(lock) {
        if (newWidth > 0 && newHeight > 0) {
            width = newWidth
            height = newHeight
            sequence += 1
        }
        sequence
    }

    fun peek(): Pair<Int, Int>? = synchronized(lock) {
        if (width > 0 && height > 0) width to height else null
    }

    /** Returns and clears the latest valid dimensions (call exactly when resources are ready). */
    fun consume(): Pair<Int, Int>? = synchronized(lock) {
        val pending = if (width > 0 && height > 0) width to height else null
        width = 0
        height = 0
        pending
    }

    fun sequence(): Long = synchronized(lock) { sequence }

    fun clear() = synchronized(lock) {
        width = 0
        height = 0
        Unit
    }
}
