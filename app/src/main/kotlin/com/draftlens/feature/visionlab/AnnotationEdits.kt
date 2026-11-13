package com.draftlens.feature.visionlab

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.geometry.NormalizedRect

/**
 * One undoable annotation change.
 */
sealed interface AnnotationEdit {

    /** A slot was deleted; undo puts it back with its own id. */
    data class Deleted(val slot: DraftSlot) : AnnotationEdit

    /** A slot was created; undo removes it again. */
    data class Created(val slot: DraftSlot) : AnnotationEdit

    /** A slot was moved or resized; undo restores the rectangle it had before. */
    data class GeometryChanged(
        val slotId: String,
        val before: NormalizedRect,
        val after: NormalizedRect,
    ) : AnnotationEdit
}

/**
 * The geometry of a copied or duplicated slot.
 */
object SlotPlacement {

    /** How far a pasted or duplicated slot is offset from its source. */
    const val PASTE_OFFSET = 0.02f

    /**
     * The rectangle of a pasted or duplicated slot: the same size, moved by a small
     * offset and kept inside the image.
     */
    fun offsetRect(rect: NormalizedRect, offset: Float = PASTE_OFFSET): NormalizedRect {
        val width = rect.width
        val height = rect.height
        val left = (rect.left + offset).coerceAtMost(1f - width)
        val top = (rect.top + offset).coerceAtMost(1f - height)
        return NormalizedRect(left, top, left + width, top + height)
    }
}
