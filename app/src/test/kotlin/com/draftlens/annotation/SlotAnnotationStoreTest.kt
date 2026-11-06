package com.draftlens.annotation

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotAnnotationStore
import com.draftlens.vision.annotation.SlotIssueCode
import com.draftlens.vision.annotation.SlotIssueSeverity
import com.draftlens.vision.annotation.SlotIssueException
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotAnnotationStoreTest {

    private fun rect(left: Float, top: Float = 0.1f, size: Float = 0.2f) =
        NormalizedRect(left, top, left + size, top + size)

    private fun Result<DraftSlot>.issueCode(): SlotIssueCode? =
        (exceptionOrNull() as? SlotIssueException)?.issueCode

    private fun store(ids: Iterator<String> = sequenceOf("a", "b", "c", "d", "e", "f", "g", "h").iterator()) =
        SlotAnnotationStore(idGenerator = ids::next)

    @Test fun `duplicate index is rejected within type team scope`() {
        val store = SlotAnnotationStore(idGenerator = sequenceOf("a", "b").iterator()::next)
        assertTrue(store.add(SlotType.BAN, SlotTeam.ALLY, rect(0.1f), index = 1).isSuccess)
        val duplicate = store.add(SlotType.BAN, SlotTeam.ALLY, rect(0.5f), index = 1)
        assertEquals(SlotIssueCode.DUPLICATE_INDEX, duplicate.issueCode())
    }

    @Test fun `same index is allowed across different scopes`() {
        val store = store()
        assertTrue(store.add(SlotType.BAN, SlotTeam.ALLY, rect(0.1f), index = 1).isSuccess)
        assertTrue(store.add(SlotType.BAN, SlotTeam.ENEMY, rect(0.4f), index = 1).isSuccess)
        assertTrue(store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.7f), index = 1).isSuccess)
    }

    @Test fun `move clamps rectangle to normalized bounds`() {
        val store = SlotAnnotationStore(idGenerator = { "slot" })
        val slot = store.add(SlotType.PICK, SlotTeam.ENEMY, NormalizedRect(0.8f, 0.1f, 0.95f, 0.4f)).getOrThrow()
        store.move(slot.id, deltaX = 0.5f, deltaY = 0f)
        assertEquals(1.0f, store.slots().single().rect.right, 0.0001f)
        assertEquals(0.85f, store.slots().single().rect.left, 0.0001f)
    }

    @Test fun `move clamps to top and left bounds`() {
        val store = SlotAnnotationStore(idGenerator = { "slot" })
        val slot = store.add(SlotType.PICK, SlotTeam.ENEMY, NormalizedRect(0.1f, 0.1f, 0.3f, 0.4f)).getOrThrow()
        store.move(slot.id, deltaX = -1f, deltaY = -1f)
        val moved = store.slots().single().rect
        assertEquals(0.0f, moved.left, 0.0001f)
        assertEquals(0.0f, moved.top, 0.0001f)
    }

    @Test fun `move rejects unknown id`() {
        val store = store()
        assertTrue(store.move("nope", 0.1f, 0.0f).isFailure)
    }

    @Test fun `add assigns next free index in scope when omitted`() {
        val store = store()
        val first = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.0f)).getOrThrow()
        val second = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.3f)).getOrThrow()
        assertEquals(1, first.index)
        assertEquals(2, second.index)
        val otherScope = store.add(SlotType.PICK, SlotTeam.ENEMY, rect(0.6f)).getOrThrow()
        assertEquals(1, otherScope.index)
    }

    @Test fun `add rejects rectangle below minimum size`() {
        val store = store()
        val tiny = NormalizedRect(0.1f, 0.1f, 0.105f, 0.2f)
        val result = store.add(SlotType.PICK, SlotTeam.ALLY, tiny)
        assertEquals(SlotIssueCode.INVALID_RECT, result.issueCode())
    }

    @Test fun `add rejects index below one`() {
        val store = store()
        val result = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.0f), index = 0)
        assertEquals(SlotIssueCode.INVALID_INDEX, result.issueCode())
    }

    @Test fun `ids are never reused after delete`() {
        val ids = sequenceOf("a", "a").iterator()
        val store = SlotAnnotationStore(idGenerator = ids::next)
        val first = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.0f)).getOrThrow()
        assertTrue(store.delete(first.id))
        val second = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.4f))
        assertTrue(second.isFailure)
        assertEquals(SlotIssueCode.DUPLICATE_ID, second.issueCode())
    }

    @Test fun `slots snapshot is immutable and stable`() {
        val store = store()
        store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.0f))
        val snapshot = store.slots()
        store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.4f))
        assertEquals(1, snapshot.size)
        assertEquals(2, store.slots().size)
    }

    @Test fun `selected slot id survives reads and clears on delete`() {
        val store = store()
        val slot = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.0f)).getOrThrow()
        assertNull(store.selectedSlotId())
        store.select(slot.id)
        assertEquals(slot.id, store.selectedSlotId())
        assertTrue(store.delete(slot.id))
        assertNull(store.selectedSlotId())
    }

    @Test fun `select rejects unknown id`() {
        val store = store()
        assertThrows(IllegalArgumentException::class.java) { store.select("nope") }
    }

    @Test fun `updateTeam rejects move into scope with duplicate index`() {
        val store = store()
        store.add(SlotType.BAN, SlotTeam.ALLY, rect(0.0f), index = 1)
        val other = store.add(SlotType.BAN, SlotTeam.ENEMY, rect(0.4f), index = 1).getOrThrow()
        val result = store.updateTeam(other.id, SlotTeam.ALLY)
        assertEquals(SlotIssueCode.DUPLICATE_INDEX, result.issueCode())
        assertEquals(SlotTeam.ENEMY, store.slots().first { it.id == other.id }.team)
    }

    @Test fun `updateType rejects move into scope with duplicate index`() {
        val store = store()
        store.add(SlotType.BAN, SlotTeam.ALLY, rect(0.0f), index = 1)
        val other = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.4f), index = 1).getOrThrow()
        val result = store.updateType(other.id, SlotType.BAN)
        assertEquals(SlotIssueCode.DUPLICATE_INDEX, result.issueCode())
        assertEquals(SlotType.PICK, store.slots().first { it.id == other.id }.type)
    }

    @Test fun `updateTeam applies when scope is free`() {
        val store = store()
        val slot = store.add(SlotType.BAN, SlotTeam.ENEMY, rect(0.0f), index = 1).getOrThrow()
        val updated = store.updateTeam(slot.id, SlotTeam.ALLY).getOrThrow()
        assertEquals(SlotTeam.ALLY, updated.team)
    }

    @Test fun `updateIndex rejects duplicate in scope`() {
        val store = store()
        store.add(SlotType.BAN, SlotTeam.ALLY, rect(0.0f), index = 1)
        val second = store.add(SlotType.BAN, SlotTeam.ALLY, rect(0.4f), index = 2).getOrThrow()
        val result = store.updateIndex(second.id, 1)
        assertEquals(SlotIssueCode.DUPLICATE_INDEX, result.issueCode())
        assertEquals(2, store.slots().first { it.id == second.id }.index)
    }

    @Test fun `updateIndex applies when free`() {
        val store = store()
        val slot = store.add(SlotType.BAN, SlotTeam.ALLY, rect(0.0f), index = 1).getOrThrow()
        val updated = store.updateIndex(slot.id, 3).getOrThrow()
        assertEquals(3, updated.index)
    }

    @Test fun `updateLabel sets and clears`() {
        val store = store()
        val slot = store.add(SlotType.PICK, SlotTeam.GLOBAL, rect(0.0f), label = "A-P1").getOrThrow()
        assertEquals("A-P2", store.updateLabel(slot.id, "A-P2").getOrThrow().label)
        assertNull(store.updateLabel(slot.id, null).getOrThrow().label)
    }

    @Test fun `resize accepts valid rect and rejects below minimum size`() {
        val store = store()
        val slot = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.0f)).getOrThrow()
        val ok = store.resize(slot.id, NormalizedRect(0.1f, 0.1f, 0.5f, 0.5f)).getOrThrow()
        assertEquals(0.4f, ok.rect.width, 0.0001f)
        val tiny = store.resize(slot.id, NormalizedRect(0.1f, 0.1f, 0.105f, 0.5f))
        assertEquals(SlotIssueCode.INVALID_RECT, tiny.issueCode())
    }

    @Test fun `delete returns false for unknown id`() {
        val store = store()
        assertFalse(store.delete("nope"))
    }

    @Test fun `reset clears slots and selection`() {
        val store = store()
        val slot = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.0f)).getOrThrow()
        store.select(slot.id)
        store.reset()
        assertTrue(store.slots().isEmpty())
        assertNull(store.selectedSlotId())
    }

    @Test fun `same scope overlap is error cross scope is warning`() {
        val store = store()
        store.add(SlotType.PICK, SlotTeam.ALLY, NormalizedRect(0.1f, 0.1f, 0.4f, 0.4f))
        store.add(SlotType.PICK, SlotTeam.ALLY, NormalizedRect(0.3f, 0.3f, 0.6f, 0.6f))
        store.add(SlotType.PICK, SlotTeam.ENEMY, NormalizedRect(0.2f, 0.2f, 0.5f, 0.5f))
        val issues = store.validate().filter { it.code == SlotIssueCode.OVERLAP }
        assertTrue(issues.any { it.severity == SlotIssueSeverity.ERROR })
        assertTrue(issues.any { it.severity == SlotIssueSeverity.WARNING })
    }

    @Test fun `non overlapping rects produce no overlap issues`() {
        val store = store()
        store.add(SlotType.PICK, SlotTeam.ALLY, NormalizedRect(0.0f, 0.0f, 0.2f, 0.2f))
        store.add(SlotType.PICK, SlotTeam.ALLY, NormalizedRect(0.3f, 0.3f, 0.5f, 0.5f))
        assertTrue(store.validate().none { it.code == SlotIssueCode.OVERLAP })
    }

    @Test fun `touching edges do not count as overlap`() {
        val store = store()
        store.add(SlotType.PICK, SlotTeam.ALLY, NormalizedRect(0.0f, 0.0f, 0.2f, 0.2f))
        store.add(SlotType.PICK, SlotTeam.ALLY, NormalizedRect(0.2f, 0.0f, 0.4f, 0.2f))
        assertTrue(store.validate().none { it.code == SlotIssueCode.OVERLAP })
    }

    @Test fun `issue exception carries code`() {
        val exception = SlotIssueException(SlotIssueCode.DUPLICATE_INDEX, "dup")
        assertEquals(SlotIssueCode.DUPLICATE_INDEX, exception.issueCode)
    }

    @Test fun `update on unknown id fails`() {
        val store = store()
        assertTrue(store.updateLabel("nope", "x").isFailure)
        assertTrue(store.updateIndex("nope", 1).isFailure)
        assertTrue(store.updateType("nope", SlotType.BAN).isFailure)
        assertTrue(store.updateTeam("nope", SlotTeam.ALLY).isFailure)
        assertTrue(store.resize("nope", rect(0.0f)).isFailure)
    }

    @Test fun `default index continues after explicit delete`() {
        val store = store()
        val first = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.0f), index = 1).getOrThrow()
        val second = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.3f), index = 2).getOrThrow()
        assertTrue(store.delete(first.id))
        val third = store.add(SlotType.PICK, SlotTeam.ALLY, rect(0.6f)).getOrThrow()
        assertEquals(3, third.index)
    }
}
