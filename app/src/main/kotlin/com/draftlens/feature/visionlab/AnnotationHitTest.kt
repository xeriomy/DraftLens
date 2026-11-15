package com.draftlens.feature.visionlab

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.geometry.NormalizedPoint
import com.draftlens.vision.geometry.NormalizedRect

/**
 * Hit testing for the annotation canvas.
 *
 * Pure functions, so the gesture rules can be tested without a Compose runtime.
 */
object AnnotationHitTest {

    /**
     * The topmost slot containing [point]. Later slots are drawn on top of earlier ones,
     * so the last match wins.
     */
    fun slotAt(point: NormalizedPoint, slots: List<DraftSlot>): DraftSlot? =
        slots.lastOrNull { it.rect.containsPoint(point) }
}

/** True when the normalized point lies inside the rectangle. */
fun NormalizedRect.containsPoint(point: NormalizedPoint): Boolean =
    point.x in left..right && point.y in top..bottom
