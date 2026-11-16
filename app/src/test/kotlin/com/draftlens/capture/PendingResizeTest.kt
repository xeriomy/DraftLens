package com.draftlens.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PendingResizeTest {

    private lateinit var pending: PendingResize

    @Before fun setUp() {
        pending = PendingResize()
    }

    @Test fun `no callback means no pending dimensions`() {
        assertEquals(null, pending.peek())
        assertEquals(null, pending.consume())
    }

    @Test fun `an early resize callback is retained`() {
        assertEquals(1L, pending.offer(1080, 2400))

        assertEquals(1080 to 2400, pending.peek())
        assertEquals(1080 to 2400, pending.consume())
        assertEquals(null, pending.peek())
    }

    @Test fun `multiple early callbacks use the latest valid dimensions`() {
        pending.offer(1080, 2400)
        pending.offer(2400, 1080)
        pending.offer(1200, 2600)

        assertEquals(3L, pending.sequence())
        assertEquals(1200 to 2600, pending.consume())
    }

    @Test fun `invalid dimensions are ignored`() {
        assertEquals(0L, pending.offer(0, 2400))
        assertEquals(0L, pending.offer(1080, -1))
        assertEquals(null, pending.peek())
    }

    @Test fun `clear drops the buffered callback`() {
        pending.offer(2400, 1080)
        pending.clear()

        assertEquals(null, pending.consume())
    }
}
