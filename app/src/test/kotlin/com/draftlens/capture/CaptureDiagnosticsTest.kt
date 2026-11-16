package com.draftlens.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureDiagnosticsTest {

    @Test fun `first events log then only periodic ones`() {
        val diagnostics = CaptureDiagnostics(firstEvents = 3, every = 5)

        val logged = (1..20).map { diagnostics.sample("acquired") to diagnostics.shouldLog(it) }
            .filter { it.second }
            .map { it.first }

        assertEquals(listOf(1, 2, 3, 5, 10, 15, 20), logged)
    }

    @Test fun `events are counted independently`() {
        val diagnostics = CaptureDiagnostics(firstEvents = 1, every = 100)

        assertEquals(1, diagnostics.sample("gate-reject:contentHidden"))
        assertEquals(1, diagnostics.sample("gate-reject:isActive"))
        assertEquals(1, diagnostics.total("gate-reject:contentHidden"))
        assertEquals(1, diagnostics.total("gate-reject:isActive"))
        assertEquals(0, diagnostics.total("published"))
    }

    @Test fun `reset clears all counters for a new session`() {
        val diagnostics = CaptureDiagnostics()
        diagnostics.sample("acquired")
        diagnostics.reset()

        assertEquals(1, diagnostics.sample("acquired"))
        assertEquals(0, diagnostics.total("published"))
    }
}
