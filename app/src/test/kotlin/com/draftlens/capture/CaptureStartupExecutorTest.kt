package com.draftlens.capture

import org.junit.Assert.*
import org.junit.Test

class CaptureStartupExecutorTest {

    @Test fun `foreground promotion precedes projection acquisition and initialization`() {
        val calls = mutableListOf<String>()
        val projection = CaptureStartupExecutor<String>().execute(
            promoteToForeground = { calls += "promote" },
            obtainProjection = { calls += "projection"; "projection" },
            initializeProjection = { calls += "initialize" }
        )

        assertEquals(listOf("promote", "projection", "initialize"), calls)
        assertEquals("projection", projection)
    }

    @Test fun `missing or failed foreground promotion prevents projection acquisition`() {
        val calls = mutableListOf<String>()
        try {
            CaptureStartupExecutor<String>().execute(
                promoteToForeground = { calls += "promote"; throw SecurityException("missing mediaProjection FGS type") },
                obtainProjection = { calls += "projection"; "projection" },
                initializeProjection = { calls += "initialize" }
            )
            fail("expected promotion failure")
        } catch (e: SecurityException) {
            assertEquals(listOf("promote"), calls)
        }
    }

    @Test fun `missing manifest permission is surfaced as promotion failure`() {
        val calls = mutableListOf<String>()
        try {
            CaptureStartupExecutor<String>().execute(
                promoteToForeground = { calls += "promote"; throw SecurityException("missing manifest permission") },
                obtainProjection = { calls += "projection"; "projection" },
                initializeProjection = { calls += "initialize" }
            )
            fail("expected manifest permission failure")
        } catch (e: SecurityException) {
            assertEquals("missing manifest permission", e.message)
            assertEquals(listOf("promote"), calls)
        }
    }

    @Test fun `startup failure propagates without initializing a projection`() {
        val calls = mutableListOf<String>()
        try {
            CaptureStartupExecutor<String>().execute(
                promoteToForeground = { calls += "promote" },
                obtainProjection = { calls += "projection"; throw SecurityException("consent revoked") },
                initializeProjection = { calls += "initialize" }
            )
            fail("expected startup failure")
        } catch (e: SecurityException) {
            assertEquals(listOf("promote", "projection"), calls)
        }
    }
}
