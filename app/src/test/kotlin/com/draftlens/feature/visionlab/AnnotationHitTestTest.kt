package com.draftlens.feature.visionlab

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.NormalizedPoint
import com.draftlens.vision.geometry.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationHitTestTest {

    private fun slot(id: String, left: Float = 0.1f, top: Float = 0.1f) = DraftSlot(
        id = id,
        type = SlotType.BAN,
        team = SlotTeam.ALLY,
        index = 1,
        rect = NormalizedRect(left, top, left + 0.2f, top + 0.2f),
    )

    @Test fun `no slot is hit outside every rectangle`() {
        assertNull(AnnotationHitTest.slotAt(NormalizedPoint(0.9f, 0.9f), listOf(slot("a"))))
    }

    @Test fun `a slot is hit inside its rectangle`() {
        assertEquals("a", AnnotationHitTest.slotAt(NormalizedPoint(0.15f, 0.15f), listOf(slot("a")))?.id)
    }

    @Test fun `the topmost slot wins when rectangles overlap`() {
        val slots = listOf(slot("a"), slot("b", left = 0.15f))
        assertEquals("b", AnnotationHitTest.slotAt(NormalizedPoint(0.2f, 0.2f), slots)?.id)
    }
}
