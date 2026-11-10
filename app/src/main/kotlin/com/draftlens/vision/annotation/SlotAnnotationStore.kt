package com.draftlens.vision.annotation

import com.draftlens.vision.geometry.NormalizedRect

/**
 * In-memory store for manual slot annotations. Pure Kotlin, calibration data only.
 * IDs come from [idGenerator] and are never reused, even after delete or reset.
 */
class SlotAnnotationStore(
    private val idGenerator: () -> String,
) {
    private val slotList = mutableListOf<DraftSlot>()
    private val usedIds = mutableSetOf<String>()
    private var selected: String? = null

    fun slots(): List<DraftSlot> = slotList.toList()

    fun selectedSlotId(): String? = selected

    fun add(
        type: SlotType,
        team: SlotTeam,
        rect: NormalizedRect,
        index: Int? = null,
        label: String? = null,
    ): Result<DraftSlot> {
        if (!meetsMinSize(rect)) return fail(SlotIssueCode.INVALID_RECT, "Rectangle is below the minimum size of $MIN_SIZE in one axis")
        val resolvedIndex = index ?: nextIndex(type, team)
        if (resolvedIndex < 1) return fail(SlotIssueCode.INVALID_INDEX, "Index must be at least 1, was $resolvedIndex")
        if (hasDuplicateIndex(type, team, resolvedIndex, excludingId = null)) {
            return fail(SlotIssueCode.DUPLICATE_INDEX, "Index $resolvedIndex already used for $type/$team")
        }
        val id = idGenerator()
        if (id in usedIds) return fail(SlotIssueCode.DUPLICATE_ID, "Generated id is already in use and is never reused")
        val slot = DraftSlot(id = id, type = type, team = team, index = resolvedIndex, rect = rect, label = label)
        usedIds += id
        slotList += slot
        return Result.success(slot)
    }

    fun move(id: String, deltaX: Float, deltaY: Float): Result<DraftSlot> {
        val slot = findSlot(id) ?: return unknownId(id)
        if (!deltaX.isFinite() || !deltaY.isFinite()) {
            return Result.failure(IllegalArgumentException("Move deltas must be finite"))
        }
        var dx = deltaX
        var dy = deltaY
        if (slot.rect.left + dx < 0f) dx = -slot.rect.left
        if (slot.rect.right + dx > 1f) dx = 1f - slot.rect.right
        if (slot.rect.top + dy < 0f) dy = -slot.rect.top
        if (slot.rect.bottom + dy > 1f) dy = 1f - slot.rect.bottom
        return replace(slot, slot.rect.copy(left = slot.rect.left + dx, top = slot.rect.top + dy, right = slot.rect.right + dx, bottom = slot.rect.bottom + dy))
    }

    fun resize(id: String, rect: NormalizedRect): Result<DraftSlot> {
        val slot = findSlot(id) ?: return unknownId(id)
        if (!meetsMinSize(rect)) return fail(SlotIssueCode.INVALID_RECT, "Rectangle is below the minimum size of $MIN_SIZE in one axis")
        return replace(slot, rect)
    }

    fun updateTeam(id: String, team: SlotTeam): Result<DraftSlot> {
        val slot = findSlot(id) ?: return unknownId(id)
        if (hasDuplicateIndex(slot.type, team, slot.index, excludingId = slot.id)) {
            return fail(SlotIssueCode.DUPLICATE_INDEX, "Index ${slot.index} already used for ${slot.type}/$team")
        }
        return replace(slot, slot.copy(team = team))
    }

    fun updateType(id: String, type: SlotType): Result<DraftSlot> {
        val slot = findSlot(id) ?: return unknownId(id)
        if (hasDuplicateIndex(type, slot.team, slot.index, excludingId = slot.id)) {
            return fail(SlotIssueCode.DUPLICATE_INDEX, "Index ${slot.index} already used for $type/${slot.team}")
        }
        return replace(slot, slot.copy(type = type))
    }

    fun updateIndex(id: String, index: Int): Result<DraftSlot> {
        val slot = findSlot(id) ?: return unknownId(id)
        if (index < 1) return fail(SlotIssueCode.INVALID_INDEX, "Index must be at least 1, was $index")
        if (hasDuplicateIndex(slot.type, slot.team, index, excludingId = slot.id)) {
            return fail(SlotIssueCode.DUPLICATE_INDEX, "Index $index already used for ${slot.type}/${slot.team}")
        }
        return replace(slot, slot.copy(index = index))
    }

    fun updateLabel(id: String, label: String?): Result<DraftSlot> {
        val slot = findSlot(id) ?: return unknownId(id)
        return replace(slot, slot.copy(label = label))
    }

    fun select(id: String?) {
        if (id != null && slotList.none { it.id == id }) {
            throw IllegalArgumentException("Unknown slot id: $id")
        }
        selected = id
    }

    fun delete(id: String): Boolean {
        val removed = slotList.removeAll { it.id == id }
        if (removed && selected == id) selected = null
        return removed
    }

    fun reset() {
        slotList.clear()
        selected = null
    }

    fun validate(): List<SlotIssue> {
        val issues = mutableListOf<SlotIssue>()
        slotList
            .groupBy { Triple(it.type, it.team, it.index) }
            .filterValues { it.size > 1 }
            .forEach { (key, group) ->
                issues += SlotIssue(
                    code = SlotIssueCode.DUPLICATE_INDEX,
                    severity = SlotIssueSeverity.ERROR,
                    message = "Index ${key.third} used more than once for ${key.first}/${key.second}",
                    slotIds = group.map { it.id },
                )
            }
        for (i in slotList.indices) {
            for (j in i + 1 until slotList.size) {
                val a = slotList[i]
                val b = slotList[j]
                if (overlaps(a.rect, b.rect)) {
                    val sameScope = a.type == b.type && a.team == b.team
                    issues += SlotIssue(
                        code = SlotIssueCode.OVERLAP,
                        severity = if (sameScope) SlotIssueSeverity.ERROR else SlotIssueSeverity.WARNING,
                        message = if (sameScope) {
                            "Overlapping rectangles in ${a.type}/${a.team}"
                        } else {
                            "Overlapping rectangles across ${a.type}/${a.team} and ${b.type}/${b.team}"
                        },
                        slotIds = listOf(a.id, b.id),
                    )
                }
            }
        }
        return issues
    }

    private fun nextIndex(type: SlotType, team: SlotTeam): Int =
        (slotList.filter { it.type == type && it.team == team }.maxOfOrNull { it.index } ?: 0) + 1

    private fun hasDuplicateIndex(type: SlotType, team: SlotTeam, index: Int, excludingId: String?): Boolean =
        slotList.any { it.id != excludingId && it.type == type && it.team == team && it.index == index }

    private fun findSlot(id: String): DraftSlot? = slotList.firstOrNull { it.id == id }

    private fun unknownId(id: String): Result<DraftSlot> =
        Result.failure(IllegalArgumentException("Unknown slot id: $id"))

    private fun replace(slot: DraftSlot, updated: DraftSlot): Result<DraftSlot> {
        val position = slotList.indexOfFirst { it.id == slot.id }
        slotList[position] = updated
        return Result.success(updated)
    }

    private fun replace(slot: DraftSlot, rect: NormalizedRect): Result<DraftSlot> =
        replace(slot, slot.copy(rect = rect))

    private fun fail(code: SlotIssueCode, message: String): Result<DraftSlot> =
        Result.failure(SlotIssueException(code, message))

    private fun meetsMinSize(rect: NormalizedRect): Boolean =
        rect.width >= MIN_SIZE && rect.height >= MIN_SIZE

    private fun overlaps(a: NormalizedRect, b: NormalizedRect): Boolean =
        a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    companion object {
        /** Minimum normalized size of a slot rectangle, in both axes. */
        const val MIN_SIZE = 0.01f
    }
}
