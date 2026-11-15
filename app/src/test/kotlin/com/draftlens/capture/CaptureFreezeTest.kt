package com.draftlens.capture

import com.draftlens.vision.testCapturedFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Command-flow coverage for the parts of the freeze path that are deterministic on the JVM.
 * The pipeline itself is Android-only, so the full flow is verified manually and on-device;
 * these tests pin the guarantees that do not need a MediaProjection session.
 */
class CaptureFreezeTest {

    @Before fun resetHolder() {
        CaptureStateHolder.reset()
    }

    @Test fun `freezing with no active capture reports NoPipeline and stores nothing`() {
        val outcome = CaptureFreeze.freezeCurrentFrame()

        assertEquals(FreezeOutcome.NoPipeline, outcome)
        assertNull(CaptureStateHolder.frozenFrame.value)
    }

    @Test fun `resume with no active capture is a safe no-op that drops the pin`() {
        CaptureStateHolder.setFrozenFrame(testCapturedFrame())

        CaptureFreeze.resumeLive()

        assertNull(CaptureStateHolder.frozenFrame.value)
    }

    @Test fun `a duplicate freeze keeps the original frame and reports AlreadyFrozen`() {
        val pinned = testCapturedFrame()
        CaptureStateHolder.setFrozenFrame(pinned)

        // The holder already pins a frame, so the command is a no-op before the pipeline
        // would even be consulted.
        val outcome = CaptureFreeze.freezeCurrentFrame()

        assertEquals(FreezeOutcome.AlreadyFrozen, outcome)
        assertEquals(pinned, CaptureStateHolder.frozenFrame.value)
    }
}
