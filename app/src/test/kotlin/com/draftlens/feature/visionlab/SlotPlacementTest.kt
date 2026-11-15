package com.draftlens.feature.visionlab

import com.draftlens.vision.geometry.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotPlacementTest {

    @Test fun `an offset slot stays inside the image`() {
        val rect = NormalizedRect(0.9f, 0.9f, 0.98f, 0.98f)
        val placed = SlotPlacement.offsetRect(rect)

        assertTrue(placed.right <= 1f)
        assertTrue(placed.bottom <= 1f)
        assertEquals(rect.width, placed.width, 0.001f)
        assertEquals(rect.height, placed.height, 0.001f)
    }

    @Test fun `an offset slot is moved away from its source`() {
        val rect = NormalizedRect(0.2f, 0.2f, 0.4f, 0.4f)
        val placed = SlotPlacement.offsetRect(rect)

        assertTrue(placed.left > rect.left)
        assertTrue(placed.top > rect.top)
    }
}
