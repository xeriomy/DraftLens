package com.draftlens.feature.visionlab

import androidx.compose.ui.geometry.Offset

/**
 * The zoom and pan applied to the annotation canvas.
 *
 * The image and the overlay are drawn under the same transform, and pointer positions are
 * mapped back through its inverse, so the overlay stays aligned with the pixels while the
 * user zooms and pans.
 */
data class AnnotationViewTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
) {
    val isIdentity: Boolean get() = scale == 1f && offset == Offset.Zero

    /** Maps a canvas point to the screen. */
    fun forward(point: Offset, pivot: Offset): Offset =
        pivot + (point - pivot) * scale + offset

    /** Maps a screen point back to the canvas. */
    fun inverse(point: Offset, pivot: Offset): Offset =
        pivot + (point - offset - pivot) / scale

    /** The same transform with the scale kept inside [minScale] and [maxScale]. */
    fun clamped(minScale: Float, maxScale: Float): AnnotationViewTransform =
        copy(scale = scale.coerceIn(minScale, maxScale))

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 6f
    }
}
