package com.draftlens.capture

import java.util.concurrent.atomic.AtomicLong

/**
 * Monotonic generation marker for capture sessions and reader generations.
 *
 * Every time the pipeline builds a new ImageReader generation it calls [bump]; callbacks
 * and publish operations captured with an older generation are rejected by [isCurrent].
 * This is what keeps late images from a released reader from overwriting frames from the
 * replacement reader.
 */
internal class GenerationCounter {
    private val generation = AtomicLong(0)

    fun bump(): Long = generation.incrementAndGet()

    fun current(): Long = generation.get()

    fun isCurrent(captured: Long): Boolean = captured == generation.get()
}
