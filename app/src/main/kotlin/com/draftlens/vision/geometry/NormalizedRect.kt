package com.draftlens.vision.geometry

import kotlinx.serialization.Serializable

/**
 * Validated rectangle in normalized source coordinates: finite values within
 * `[0, 1]`, strict `left < right` and `top < bottom` (empty is not valid).
 */
@Serializable
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "normalized coordinates must be finite"
        }
        require(left < right) { "left must be smaller than right" }
        require(top < bottom) { "top must be smaller than bottom" }
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f) {
            "normalized coordinates must be within [0, 1]"
        }
    }

    val width: Float get() = right - left
    val height: Float get() = bottom - top
}
