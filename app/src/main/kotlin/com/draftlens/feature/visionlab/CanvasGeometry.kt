package com.draftlens.feature.visionlab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.draftlens.vision.geometry.FloatPoint
import com.draftlens.vision.geometry.FrameTransform
import com.draftlens.vision.geometry.NormalizedPoint
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Frame geometry for the annotation canvas: the transform between source pixels and the
 * oriented image the user annotates.
 *
 * Pure geometry, so the mapping and the letterbox rules are testable without Compose.
 */
data class CanvasGeometry(
    val transform: FrameTransform,
    val rotation: FrameRotation,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val orientedWidth: Int,
    val orientedHeight: Int,
) {
    companion object {
        fun of(frame: OriginalFrame): CanvasGeometry {
            val swapsAxes =
                frame.rotation == FrameRotation.ROTATION_90 || frame.rotation == FrameRotation.ROTATION_270
            val orientedWidth = if (swapsAxes) frame.sourceHeight else frame.sourceWidth
            val orientedHeight = if (swapsAxes) frame.sourceWidth else frame.sourceHeight
            return CanvasGeometry(
                transform = FrameTransform.create(
                    sourceWidth = frame.sourceWidth,
                    sourceHeight = frame.sourceHeight,
                    rotation = frame.rotation,
                    crop = null,
                    processedWidth = orientedWidth,
                    processedHeight = orientedHeight,
                ),
                rotation = frame.rotation,
                sourceWidth = frame.sourceWidth,
                sourceHeight = frame.sourceHeight,
                orientedWidth = orientedWidth,
                orientedHeight = orientedHeight,
            )
        }
    }

    fun normalizedToCanvas(rect: NormalizedRect, fitted: Rect): Rect =
        normalizedToCanvas(rect.left, rect.top, rect.right, rect.bottom, fitted)

    fun normalizedToCanvas(left: Float, top: Float, right: Float, bottom: Float, fitted: Rect): Rect {
        val topLeft = transform.sourceToOriented(FloatPoint(left * sourceWidth, top * sourceHeight))
        val bottomRight = transform.sourceToOriented(FloatPoint(right * sourceWidth, bottom * sourceHeight))
        val orientedLeft = min(topLeft.x, bottomRight.x)
        val orientedTop = min(topLeft.y, bottomRight.y)
        val orientedRight = max(topLeft.x, bottomRight.x)
        val orientedBottom = max(topLeft.y, bottomRight.y)
        return Rect(
            left = fitted.left + orientedLeft / orientedWidth * fitted.width,
            top = fitted.top + orientedTop / orientedHeight * fitted.height,
            right = fitted.left + orientedRight / orientedWidth * fitted.width,
            bottom = fitted.top + orientedBottom / orientedHeight * fitted.height,
        )
    }

    /**
     * Maps a canvas point to normalized source coordinates.
     *
     * Returns null for a point outside the fitted image rectangle, so a touch on a
     * letterbox band is not clamped onto the image edge.
     */
    fun canvasToNormalized(offset: Offset, fitted: Rect): NormalizedPoint? {
        if (fitted.width <= 0f || fitted.height <= 0f) return null
        if (offset.x < fitted.left || offset.x > fitted.right) return null
        if (offset.y < fitted.top || offset.y > fitted.bottom) return null
        val oriented = FloatPoint(
            (offset.x - fitted.left) / fitted.width * orientedWidth,
            (offset.y - fitted.top) / fitted.height * orientedHeight,
        )
        val source = transform.orientedToSource(oriented)
        return NormalizedPoint(
            x = (source.x / sourceWidth).coerceIn(0f, 1f),
            y = (source.y / sourceHeight).coerceIn(0f, 1f),
        )
    }

    /**
     * Like [canvasToNormalized] but clamps to the fitted image.
     *
     * Used only to keep an in-progress drag alive once the pointer leaves the image; a
     * gesture may still *start* only on the image.
     */
    fun canvasToNormalizedClamped(offset: Offset, fitted: Rect): NormalizedPoint? {
        if (fitted.width <= 0f || fitted.height <= 0f) return null
        val x = offset.x.coerceIn(fitted.left, fitted.right)
        val y = offset.y.coerceIn(fitted.top, fitted.bottom)
        val oriented = FloatPoint(
            (x - fitted.left) / fitted.width * orientedWidth,
            (y - fitted.top) / fitted.height * orientedHeight,
        )
        val source = transform.orientedToSource(oriented)
        return NormalizedPoint(
            x = (source.x / sourceWidth).coerceIn(0f, 1f),
            y = (source.y / sourceHeight).coerceIn(0f, 1f),
        )
    }

    /** Corner handle under the pointer, if any. */
    fun handleAt(rect: NormalizedRect, offset: Offset, fitted: Rect, radius: Float): Corner? {
        val bounds = normalizedToCanvas(rect, fitted)
        val touch = radius * 2f
        val corners = listOf(
            Corner.TOP_LEFT to bounds.topLeft,
            Corner.TOP_RIGHT to Offset(bounds.right, bounds.top),
            Corner.BOTTOM_LEFT to Offset(bounds.left, bounds.bottom),
            Corner.BOTTOM_RIGHT to bounds.bottomRight,
        )
        return corners.firstOrNull { (_, corner) ->
            abs(corner.x - offset.x) <= touch && abs(corner.y - offset.y) <= touch
        }?.first
    }
}

/** A resize handle at one corner of a rectangle. */
enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
