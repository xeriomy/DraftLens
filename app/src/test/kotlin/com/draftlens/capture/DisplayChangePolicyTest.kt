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
