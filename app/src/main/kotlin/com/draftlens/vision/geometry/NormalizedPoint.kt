package com.draftlens.vision.geometry

/**
 * Normalized coordinate in source-image axes: both values in `[0, 1]`,
 * x left-to-right, y top-to-bottom. Never display dp or density-scaled.
 */
data class NormalizedPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "normalized coordinates must be finite" }
        require(x in 0f..1f && y in 0f..1f) { "normalized coordinates must be within [0, 1]" }
    }
}
