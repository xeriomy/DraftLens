package com.draftlens.vision.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameOrientationTest {

    @Test fun `captured frames need no pixel orientation correction`() {
        for (rotation in FrameRotation.entries) {
            assertEquals(
                "display rotation $rotation must not rotate captured pixels",
                FrameRotation.ROTATION_0,
                FrameOrientation.correctionFor(rotation),
            )
        }
    }

    @Test fun `rotation degrees follow the clockwise canvas convention`() {
        assertEquals(0, FrameRotation.ROTATION_0.degrees)
        assertEquals(90, FrameRotation.ROTATION_90.degrees)
        assertEquals(180, FrameRotation.ROTATION_180.degrees)
        assertEquals(270, FrameRotation.ROTATION_270.degrees)
    }
}
