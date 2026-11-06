package com.draftlens.vision.geometry

import com.draftlens.vision.model.FrameRotation

/**
 * Immutable record of one preprocessing transform: source dims, pixel-orientation correction,
 * oriented crop rect, oriented dims, crop pixel rect, scale, and processed dims.
 *
 * Behavior lives with this type in Sprint 1A Task 2 (create() and coordinate round trips);
 * Task 1 only defines the stored shape so ProcessedFrame carries the spec fields.
 */
data class FrameTransform(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotation: FrameRotation,
    val crop: NormalizedRect?,
    val orientedWidth: Int,
    val orientedHeight: Int,
    val cropPixelRect: PixelRect?,
    val scale: Float,
    val processedWidth: Int,
    val processedHeight: Int,
)
