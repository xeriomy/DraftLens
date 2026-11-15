package com.draftlens.feature.visionlab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.degrees
import kotlin.math.min

/**
 * Where a captured bitmap is drawn inside the fitted rectangle.
 *
 * The bitmap's raw pixel size is **not** a canvas size: the bitmap is scaled uniformly
 * into the fitted rectangle, so the drawn pixels and the annotation geometry share the
 * same bounds.
 */
data class OrientedDrawBounds(
    /** The destination rectangle of the bitmap before the rotation is applied. */
    val destination: Rect,
    /** The axis-aligned bounds of the bitmap after the rotation is applied. */
    val rotatedBounds: Rect,
    /** The rotation pivot, in canvas coordinates. */
    val pivot: Offset,
    /** The clockwise rotation in degrees. */
    val degrees: Float,
)

/**
 * Computes the destination of a captured bitmap for a given oriented frame and a fitted
 * rectangle. Pure geometry, so the scale and the bounds are unit-testable.
 */
object OrientedBitmapLayout {

    fun of(
        sourceWidth: Int,
        sourceHeight: Int,
        rotation: FrameRotation,
        fitted: Rect,
    ): OrientedDrawBounds {
        val swapsAxes =
            rotation == FrameRotation.ROTATION_90 || rotation == FrameRotation.ROTATION_270
        val orientedWidth = if (swapsAxes) sourceHeight else sourceWidth
        val orientedHeight = if (swapsAxes) sourceWidth else sourceHeight

        // One uniform scale for both axes: the image is never stretched.
        val scale = min(
            fitted.width / orientedWidth,
            fitted.height / orientedHeight,
        )
        val drawWidth = sourceWidth * scale
        val drawHeight = sourceHeight * scale
        val center = fitted.center

        val destination = Rect(
            left = center.x - drawWidth / 2f,
            top = center.y - drawHeight / 2f,
            right = center.x + drawWidth / 2f,
            bottom = center.y + drawHeight / 2f,
        )
        val rotatedWidth = if (swapsAxes) drawHeight else drawWidth
        val rotatedHeight = if (swapsAxes) drawWidth else drawHeight
        val rotatedBounds = Rect(
            left = center.x - rotatedWidth / 2f,
            top = center.y - rotatedHeight / 2f,
            right = center.x + rotatedWidth / 2f,
            bottom = center.y + rotatedHeight / 2f,
        )
        return OrientedDrawBounds(
            destination = destination,
            rotatedBounds = rotatedBounds,
            pivot = center,
            degrees = rotation.degrees.toFloat(),
        )
    }
}
