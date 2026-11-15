package com.draftlens.feature.visionlab

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.draftlens.vision.annotation.SlotValidator
import com.draftlens.vision.geometry.NormalizedPoint
import com.draftlens.vision.geometry.NormalizedRect
import kotlin.math.max
import kotlin.math.min

/**
 * The geometry of the annotation gestures, kept pure so the create and resize rules are
 * unit-testable without Compose.
 */
object AnnotationGeometry {

    /**
     * The rectangle spanned by two drag points, or null when it is smaller than the
     * minimum slot size and therefore creates nothing.
     */
    fun rectBetween(start: NormalizedPoint, end: NormalizedPoint): NormalizedRect? {
        val left = min(start.x, end.x).coerceIn(0f, 1f)
        val top = min(start.y, end.y).coerceIn(0f, 1f)
        val right = max(start.x, end.x).coerceIn(0f, 1f)
        val bottom = max(start.y, end.y).coerceIn(0f, 1f)
        if (right - left < SlotValidator.MIN_SIZE || bottom - top < SlotValidator.MIN_SIZE) return null
        return NormalizedRect(left, top, right, bottom)
    }

    /**
     * The rectangle after dragging [corner] of [start] to [point], or null when the result
     * would be too small to store.
     */
    fun resizedRect(start: NormalizedRect, corner: Corner, point: NormalizedPoint): NormalizedRect? {
        val x = point.x.coerceIn(0f, 1f)
        val y = point.y.coerceIn(0f, 1f)
        val left: Float
        val top: Float
        val right: Float
        val bottom: Float
        when (corner) {
            Corner.TOP_LEFT -> {
                left = x; top = y; right = start.right; bottom = start.bottom
            }
            Corner.TOP_RIGHT -> {
                left = start.left; top = y; right = x; bottom = start.bottom
            }
            Corner.BOTTOM_LEFT -> {
                left = x; top = start.top; right = start.right; bottom = y
            }
            Corner.BOTTOM_RIGHT -> {
                left = start.left; top = start.top; right = x; bottom = y
            }
        }
        if (right - left < SlotValidator.MIN_SIZE || bottom - top < SlotValidator.MIN_SIZE) return null
        return NormalizedRect(left, top, right, bottom)
    }
}

/**
 * The size of the annotation workspace.
 */
object AnnotationWorkspace {

    /** Smallest workspace height, so a landscape frame still has a usable touch area. */
    val MIN_HEIGHT: Dp = 240.dp

    /**
     * The height a workspace of [width] needs to show a frame of aspect [ratio] without
     * distortion, never below [minimum].
     */
    fun heightFor(width: Dp, ratio: Float, minimum: Dp = MIN_HEIGHT): Dp {
        if (ratio <= 0f) return minimum
        val exact = width / ratio
        return if (exact < minimum) minimum else exact
    }
}
