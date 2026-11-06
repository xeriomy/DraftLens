package com.draftlens.detection

import org.junit.Assert.*
import org.junit.Test

class DetectionStubsTest {

    @Test fun `draft phase detector is explicitly not implemented`() {
        val r = UnavailableDraftPhaseDetector().detect(FrameRef(0, 1080, 2400))
        assertTrue(r is DetectionResult.NotImplemented)
    }

    @Test fun `hero recognizer never fabricates a hero`() {
        val r = UnavailableHeroRecognizer().recognize(HeroSlot(0, 0, 0, 100, 100, TeamSide.ALLY))
        assertTrue(r is DetectionResult.NotImplemented)
        assertNull((r as DetectionResult.NotImplemented).let { null })
    }

    @Test fun `validator rejects not-implemented results`() {
        val v = UnavailableDetectionValidator()
        assertFalse(v.validate(DetectionResult.NotImplemented<String>()))
    }

    @Test fun `frame processor reports not implemented`() {
        assertEquals(ProcessorResult.NOT_IMPLEMENTED, UnavailableFrameProcessor().process(FrameRef(0, 1, 1)))
    }
}
