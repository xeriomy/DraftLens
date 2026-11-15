package com.draftlens.vision.annotation

/**
 * Shared validation for manual slot annotations.
 *
 * Both the annotation codec and the calibration codec use it, so a slot the editor would
 * reject cannot be persisted as calibration data.
 */
object SlotValidator {

    /** Minimum normalized size of a slot rectangle, in both axes. */
    const val MIN_SIZE = 0.01f

    /**
     * Returns a human-readable description of the first problem with [slots], or null when
     * they are valid.
     *
     * A slot is valid when its rectangle is at least [MIN_SIZE] in both axes, its index is
     * at least 1, no two slots share an id, and no two slots share a `(type, team, index)`.
     */
    fun findProblem(slots: List<DraftSlot>, context: String = "annotation data"): String? {
        for (slot in slots) {
            if (slot.rect.width < MIN_SIZE || slot.rect.height < MIN_SIZE) {
                return "Slot '${slot.id}' rectangle is below the minimum size of $MIN_SIZE in one axis."
            }
            if (slot.index < 1) {
                return "Slot '${slot.id}' index must be at least 1, was ${slot.index}."
            }
        }
        slots.groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys
            .firstOrNull()
            ?.let { id -> return "Duplicate slot id '$id' in $context." }
        slots.groupBy { Triple(it.type, it.team, it.index) }
            .filterValues { it.size > 1 }
            .keys
            .firstOrNull()
            ?.let { (type, team, index) -> return "Duplicate index $index for $type/$team in $context." }
        return null
    }
}
