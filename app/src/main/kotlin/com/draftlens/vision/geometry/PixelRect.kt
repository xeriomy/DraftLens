package com.draftlens.vision.geometry

/**
 * Pixel rectangle where [right] and [bottom] are exclusive edges, so at least one pixel
 * must remain between opposite edges: `right > left` and `bottom > top`.
 */
data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init {
        require(right > left) { "right edge must be greater than left edge" }
        require(bottom > top) { "bottom edge must be greater than top edge" }
    }

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
