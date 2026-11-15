package com.draftlens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.draftlens.capture.FramePipeline
import com.draftlens.capture.FrameSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lifecycle invariants for the capture pipeline on a device or emulator: generation
 * bumps, replacement surfaces and the frozen snapshot. No pixels are pushed, so this
 * needs no MediaProjection consent. Compiled in CI; execution requires a device/emulator
 * (see docs/TESTING_PHYSICAL_DEVICE.md).
 */
@RunWith(AndroidJUnit4::class)
class FramePipelineInstrumentedTest {

    @Test fun `starting a pipeline creates a surface sized to the request`() {
        val pipeline = FramePipeline(FrameSampler(FrameSampler.DEFAULT_INTERVAL_MS))
        try {
            pipeline.start(1080, 2400, 440)

            assertNotNull(pipeline.surface())
            assertEquals(1080, pipeline.displayWidth)
            assertEquals(2400, pipeline.displayHeight)
        } finally {
            pipeline.stop()
        }
    }

    @Test fun `a resize replaces the surface and bumps the generation`() {
        val pipeline = FramePipeline(FrameSampler(FrameSampler.DEFAULT_INTERVAL_MS))
        try {
            pipeline.start(1080, 2400, 440)
            val before = pipeline.surface()
            val generationBefore = pipeline.peekGeneration()

            val replacement = pipeline.prepareResize(2400, 1080, 440,
                com.draftlens.vision.model.FrameRotation.ROTATION_0)

            assertNotNull(replacement)
            replacement!!.closePrevious()
            assertEquals(2400, pipeline.displayWidth)
            assertEquals(1080, pipeline.displayHeight)
            assertNotSame("resize must install a replacement surface", before, pipeline.surface())
            assertTrue("resize must advance the generation", pipeline.peekGeneration() > generationBefore)
        } finally {
            pipeline.stop()
        }
    }

    @Test fun `freezing with no frames published reports NoFrame and keeps the pipeline alive`() {
        val pipeline = FramePipeline(FrameSampler(FrameSampler.DEFAULT_INTERVAL_MS))
        try {
            pipeline.start(2400, 1080, 440)

            val outcome = pipeline.freezeAndSnapshot()

            assertEquals(FramePipeline.FreezeResult.NoFrame, outcome)
            assertNotNull("the reader must survive a freeze request with no frames", pipeline.surface())
        } finally {
            pipeline.stop()
        }
    }

    @Test fun `stopping the pipeline releases its surface`() {
        val pipeline = FramePipeline(FrameSampler(FrameSampler.DEFAULT_INTERVAL_MS))
        pipeline.start(1080, 2400, 440)

        pipeline.stop()

        assertNull(pipeline.surface())
        assertEquals(0, pipeline.displayWidth)
        assertEquals(0, pipeline.displayHeight)
    }
}
