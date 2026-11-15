package com.draftlens.capture

import com.draftlens.vision.model.FrameRotation
import org.junit.Assert.assertEquals
import org.junit.Test

class DisplayChangePolicyTest {

    @Test fun `identical dimensions need only a rotation update`() {
        assertEquals(
            DisplayChange.RotationOnly,
            DisplayChangePolicy.classify(1080, 1920, 1080, 1920),
        )
    }

    @Test fun `changed dimensions need a resize`() {
        assertEquals(
            DisplayChange.Resize(1920, 1080),
            DisplayChangePolicy.classify(1080, 1920, 1920, 1080),
        )
    }

    @Test fun `a changed width alone needs a resize`() {
        assertEquals(
            DisplayChange.Resize(1200, 1920),
            DisplayChangePolicy.classify(1080, 1920, 1200, 1920),
        )
    }

    @Test fun `a portrait-to-landscape rotation always swaps dimensions and needs a resize`() {
        assertEquals(
            DisplayChange.Resize(2400, 1080),
            DisplayChangePolicy.classify(1080, 2400, 2400, 1080),
        )
    }

    @Test fun `a landscape-to-portrait rotation always swaps dimensions and needs a resize`() {
        assertEquals(
            DisplayChange.Resize(1080, 2400),
            DisplayChangePolicy.classify(2400, 1080, 1080, 2400),
        )
    }

    @Test fun `repeated identical resize callbacks are a rotation-only update`() {
        // The same callback can fire several times for one display change; the classification
        // must be idempotent so the pipeline is not needlessly recreated.
        assertEquals(
            DisplayChange.RotationOnly,
            DisplayChangePolicy.classify(2400, 1080, 2400, 1080),
        )
    }
}

class FramePipelineRotationTest {

    @Test fun `the pipeline records a display rotation and updates it without resizing`() {
        val pipeline = FramePipeline(FrameSampler(FrameSampler.DEFAULT_INTERVAL_MS))

        assertEquals(FrameRotation.ROTATION_0, pipeline.displayRotation)

        pipeline.updateDisplayRotation(FrameRotation.ROTATION_90)
        assertEquals(FrameRotation.ROTATION_90, pipeline.displayRotation)

        pipeline.updateDisplayRotation(FrameRotation.ROTATION_270)
        assertEquals(FrameRotation.ROTATION_270, pipeline.displayRotation)
    }
}
