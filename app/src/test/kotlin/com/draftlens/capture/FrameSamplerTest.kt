package com.draftlens.capture

import org.junit.Assert.*
import org.junit.Test

class FrameSamplerTest {

    @Test fun `first frame always emitted`() {
        val s = FrameSampler(500)
        assertTrue(s.shouldEmit(0))
    }

    @Test fun `frame within interval suppressed`() {
        val s = FrameSampler(500)
        s.markEmitted(1000)
        assertFalse(s.shouldEmit(1200)) // 200ms < 500ms
    }

    @Test fun `frame after interval emitted`() {
        val s = FrameSampler(500)
        s.markEmitted(1000)
        assertTrue(s.shouldEmit(1500))
    }

    @Test fun `interval clamped to 3 fps max`() {
        assertEquals(334L, FrameSampler.clampInterval(100))
        assertEquals(2000L, FrameSampler.clampInterval(99999))
    }

    @Test fun `effective fps matches interval`() {
        assertEquals(2.0, FrameSampler(500).effectiveFps(), 0.001)
    }

    @Test fun `reset emits next frame immediately`() {
        val s = FrameSampler(500)
        s.markEmitted(1000)
        s.reset()
        assertTrue(s.shouldEmit(1001))
    }

    @Test fun `monotonic clock is independent of wall-clock jumps`() {
        var monotonic = 10_000L
        val s = FrameSampler(500, timeSource = { monotonic })
        s.markEmitted()
        monotonic += 250
        assertFalse(s.shouldEmit())
        monotonic += 250
        assertTrue(s.shouldEmit())
    }

    @Test fun `pause and resume resets sampler`() {
        var monotonic = 0L
        val s = FrameSampler(500, timeSource = { monotonic })
        s.markEmitted()
        monotonic += 100
        s.reset()
        assertTrue(s.shouldEmit())
    }

    @Test fun `setIntervalMs clamps and resets`() {
        var monotonic = 0L
        val s = FrameSampler(500, timeSource = { monotonic })
        s.markEmitted()
        s.setIntervalMs(100)
        assertEquals(FrameSampler.MIN_INTERVAL_MS, s.minIntervalMs)
        assertTrue(s.shouldEmit())
    }

    @Test fun `negative or overflowing timestamps do not emit repeatedly when monotonic is used`() {
        var monotonic = Long.MAX_VALUE - 600
        val s = FrameSampler(500, timeSource = { monotonic })
        s.markEmitted()
        monotonic = Long.MAX_VALUE
        assertTrue(s.shouldEmit())
    }

    @Test fun `constructor rejects tiny interval`() {
        try {
            FrameSampler(100)
            fail("expected require failure")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
