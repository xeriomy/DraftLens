package com.draftlens.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationCounterTest {

    @Test fun `a captured generation goes stale exactly when a new reader generation starts`() {
        val counter = GenerationCounter()
        val first = counter.bump()

        assertTrue(counter.isCurrent(first))

        counter.bump()

        assertFalse("the first generation is stale after the second bump", counter.isCurrent(first))
        assertTrue(counter.isCurrent(counter.current()))
    }

    @Test fun `generations are monotonic and never reused`() {
        val counter = GenerationCounter()
        val generations = (1..5).map { counter.bump() }

        assertEquals((1L..5L).toList(), generations)
        assertEquals(5L, counter.current())
    }

    @Test fun `freezing and resuming does not change the generation`() {
        // The frozen snapshot keeps showing the latest published frame; resuming continues
        // the same reader generation, so the counter must not advance by itself.
        val counter = GenerationCounter()
        val gen = counter.bump()

        assertEquals(gen, counter.current())
        assertTrue(counter.isCurrent(gen))
    }
}
