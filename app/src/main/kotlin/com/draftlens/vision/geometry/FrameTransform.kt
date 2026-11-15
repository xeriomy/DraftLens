package com.draftlens.vision.geometry

import com.draftlens.vision.model.FrameRotation
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Continuous point in pixel coordinates, with the origin at the top-left of an image.
 *
 * The coordinates may lie outside the image bounds: rotation, crop, and scale are affine
 * maps and are evaluated beyond the visible area for round trips. Non-finite values are
 * rejected because they would silently poison every downstream coordinate.
 */
data class FloatPoint(val x: Float, val y: Float) {
    init {
        require(x.isFinite() && y.isFinite()) { "pixel coordinates must be finite" }
    }
}

/**
 * Immutable record of one preprocessing transform: source dimensions, pixel-orientation
 * correction, oriented crop rectangle, oriented dimensions, crop pixel rectangle, scale,
 * and processed dimensions.
 *
 * Coordinates flow through these spaces:
 * 1. source pixels (captured, before orientation correction),
 * 2. oriented pixels (rotation correction applied),
 * 3. crop pixels (origin at the top-left of [cropPixelRect]),
 * 4. processed pixels (cropped and scaled output).
 *
 * The pipeline order is fixed: validate, orient, crop, then resize without distortion.
 * Crop is stored in normalized oriented coordinates; [cropPixelRect] is its integer
 * counterpart. A null crop means the full oriented frame.
 *
 * [scale] is the horizontal processed-pixels-per-crop-pixel ratio. Horizontal and vertical
 * ratios can differ by rounding within one output pixel, and the mappings use each axis
 * separately so the output is never stretched by more than that rounding.
 *
 * Round trips are exact up to floating-point error for continuous mappings. Public integer
 * conversions round outward to whole pixels and therefore remain within one pixel.
 */
data class FrameTransform(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val rotation: FrameRotation,
    val crop: NormalizedRect,
    val orientedWidth: Int,
    val orientedHeight: Int,
    val cropPixelRect: PixelRect,
    val scale: Float,
    val processedWidth: Int,
    val processedHeight: Int,
) {
    init {
        require(sourceWidth > 0) { "source width must be positive" }
        require(sourceHeight > 0) { "source height must be positive" }
        require(processedWidth > 0) { "processed width must be positive" }
        require(processedHeight > 0) { "processed height must be positive" }
        require(scale.isFinite() && scale > 0f) { "scale must be positive and finite" }

        val swapsAxes = rotation == FrameRotation.ROTATION_90 || rotation == FrameRotation.ROTATION_270
        val expectedOrientedWidth = if (swapsAxes) sourceHeight else sourceWidth
        val expectedOrientedHeight = if (swapsAxes) sourceWidth else sourceHeight
        require(orientedWidth == expectedOrientedWidth) {
            "oriented width does not match rotation"
        }
        require(orientedHeight == expectedOrientedHeight) {
            "oriented height does not match rotation"
        }
        require(
            cropPixelRect.left >= 0 &&
                cropPixelRect.top >= 0 &&
                cropPixelRect.right <= orientedWidth &&
                cropPixelRect.bottom <= orientedHeight
        ) { "crop pixel rectangle must be within the oriented image" }
    }

    private val scaleX: Float get() = processedWidth.toFloat() / cropPixelRect.width
    private val scaleY: Float get() = processedHeight.toFloat() / cropPixelRect.height

    /** Maps a continuous source-pixel point into oriented pixels. */
    fun sourceToOriented(point: FloatPoint): FloatPoint = when (rotation) {
        FrameRotation.ROTATION_0 -> point
        FrameRotation.ROTATION_90 -> FloatPoint(sourceHeight - point.y, point.x)
        FrameRotation.ROTATION_180 -> FloatPoint(sourceWidth - point.x, sourceHeight - point.y)
        FrameRotation.ROTATION_270 -> FloatPoint(point.y, sourceWidth - point.x)
    }

    /** Maps a continuous oriented-pixel point back into source pixels. */
    fun orientedToSource(point: FloatPoint): FloatPoint = when (rotation) {
        FrameRotation.ROTATION_0 -> point
        FrameRotation.ROTATION_90 -> FloatPoint(point.y, sourceHeight - point.x)
        FrameRotation.ROTATION_180 -> FloatPoint(sourceWidth - point.x, sourceHeight - point.y)
        FrameRotation.ROTATION_270 -> FloatPoint(sourceWidth - point.y, point.x)
    }

    /** Maps an oriented-pixel point into crop pixels, whose origin is the crop top-left. */
    fun orientedToCrop(point: FloatPoint): FloatPoint = FloatPoint(
        point.x - cropPixelRect.left,
        point.y - cropPixelRect.top,
    )

    /** Maps a crop-pixel point back into oriented pixels. */
    fun cropToOriented(point: FloatPoint): FloatPoint = FloatPoint(
        point.x + cropPixelRect.left,
        point.y + cropPixelRect.top,
    )

    /** Maps a crop-pixel point into processed pixels. */
    fun cropToProcessed(point: FloatPoint): FloatPoint = FloatPoint(
        point.x * scaleX,
        point.y * scaleY,
    )

    /** Maps a processed-pixel point back into crop pixels. */
    fun processedToCrop(point: FloatPoint): FloatPoint = FloatPoint(
        point.x / scaleX,
        point.y / scaleY,
    )

    /** Maps a continuous source-pixel point into processed pixels. */
    fun sourceToProcessed(point: FloatPoint): FloatPoint =
        cropToProcessed(orientedToCrop(sourceToOriented(point)))

    /** Maps a continuous processed-pixel point back into source pixels. */
    fun processedToSource(point: FloatPoint): FloatPoint =
        orientedToSource(cropToOriented(processedToCrop(point)))

    /** Converts a source-normalized point to the nearest source pixel. */
    fun normalizedToSourcePixel(point: NormalizedPoint): PixelPoint = PixelPoint(
        (point.x * sourceWidth).roundToInt().coerceIn(0, sourceWidth),
        (point.y * sourceHeight).roundToInt().coerceIn(0, sourceHeight),
    )

    /**
     * Converts a source pixel to normalized source coordinates. The right and bottom image
     * edges map to `1.0`; pixels outside the image are rejected rather than clamped.
     */
    fun sourcePixelToNormalized(point: PixelPoint): NormalizedPoint {
        require(point.x in 0..sourceWidth && point.y in 0..sourceHeight) {
            "source pixel point must be within the source image"
        }
        return NormalizedPoint(
            point.x.toFloat() / sourceWidth,
            point.y.toFloat() / sourceHeight,
        )
    }

    /** Converts a source-normalized rectangle to the processed integer pixel rectangle. */
    fun sourceNormalizedRectToProcessedPixelRect(rect: NormalizedRect): PixelRect =
        rect.toPixelRect(sourceWidth, sourceHeight).mapWith(
            map = ::sourceToProcessed,
            width = processedWidth,
            height = processedHeight,
        )

    /** Converts a processed integer pixel rectangle back to normalized source coordinates. */
    fun processedPixelRectToSourceNormalizedRect(rect: PixelRect): NormalizedRect {
        require(
            rect.left >= 0 &&
                rect.top >= 0 &&
                rect.right <= processedWidth &&
                rect.bottom <= processedHeight
        ) { "processed pixel rectangle must be within the processed image" }
        val sourcePixels = rect.mapWith(
            map = ::processedToSource,
            width = sourceWidth,
            height = sourceHeight,
        )
        return sourcePixels.toNormalized(sourceWidth, sourceHeight)
    }

    companion object {
        /**
         * Builds a transform after validating dimensions, crop, and output aspect ratio.
         *
         * [crop] is normalized in **oriented** coordinates; null means the full oriented
         * frame. The output aspect ratio must match the integer crop rectangle within one
         * pixel of width or height, which tolerates normal aspect-preserving rounding.
         */
        fun create(
            sourceWidth: Int,
            sourceHeight: Int,
            rotation: FrameRotation,
            crop: NormalizedRect? = null,
            processedWidth: Int,
            processedHeight: Int,
        ): FrameTransform {
            require(sourceWidth > 0) { "source width must be positive" }
            require(sourceHeight > 0) { "source height must be positive" }
            require(processedWidth > 0) { "processed width must be positive" }
            require(processedHeight > 0) { "processed height must be positive" }

            val swapsAxes =
                rotation == FrameRotation.ROTATION_90 || rotation == FrameRotation.ROTATION_270
            val orientedWidth = if (swapsAxes) sourceHeight else sourceWidth
            val orientedHeight = if (swapsAxes) sourceWidth else sourceHeight

            val orientedCrop = crop ?: NormalizedRect(0f, 0f, 1f, 1f)
            val cropPixelRect = orientedCrop.toPixelRect(orientedWidth, orientedHeight)
            require(
                aspectPreserved(
                    cropWidth = cropPixelRect.width,
                    cropHeight = cropPixelRect.height,
                    outputWidth = processedWidth,
                    outputHeight = processedHeight,
                )
            ) { "processed dimensions must preserve the oriented crop aspect ratio" }

            return FrameTransform(
                sourceWidth = sourceWidth,
                sourceHeight = sourceHeight,
                rotation = rotation,
                crop = orientedCrop,
                orientedWidth = orientedWidth,
                orientedHeight = orientedHeight,
                cropPixelRect = cropPixelRect,
                scale = processedWidth.toFloat() / cropPixelRect.width,
                processedWidth = processedWidth,
                processedHeight = processedHeight,
            )
        }

        private fun aspectPreserved(
            cropWidth: Int,
            cropHeight: Int,
            outputWidth: Int,
            outputHeight: Int,
        ): Boolean {
            val widthFromHeight = cropWidth.toDouble() * outputHeight / cropHeight
            val heightFromWidth = cropHeight.toDouble() * outputWidth / cropWidth
            return abs(widthFromHeight - outputWidth) <= 1.0 ||
                abs(heightFromWidth - outputHeight) <= 1.0
        }
    }
}

/**
 * Converts a normalized rectangle to whole pixels, rounding outward so the result covers
 * the requested area and always keeps at least one pixel per edge.
 */
fun NormalizedRect.toPixelRect(width: Int, height: Int): PixelRect {
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }
    val leftPx = floor(left * width).toInt().coerceIn(0, width - 1)
    val topPx = floor(top * height).toInt().coerceIn(0, height - 1)
    val rightPx = ceil(right * width).toInt().coerceIn(leftPx + 1, width)
    val bottomPx = ceil(bottom * height).toInt().coerceIn(topPx + 1, height)
    return PixelRect(leftPx, topPx, rightPx, bottomPx)
}

/**
 * Converts a pixel rectangle to normalized coordinates. The rectangle must lie inside an
 * image of [width] x [height]; out-of-range edges are rejected, not clamped.
 */
fun PixelRect.toNormalized(width: Int, height: Int): NormalizedRect {
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }
    require(left >= 0 && top >= 0 && right <= width && bottom <= height) {
        "pixel rectangle must be within the image"
    }
    return NormalizedRect(
        left = left.toFloat() / width,
        top = top.toFloat() / height,
        right = right.toFloat() / width,
        bottom = bottom.toFloat() / height,
    )
}

private fun PixelRect.mapWith(
    map: (FloatPoint) -> FloatPoint,
    width: Int,
    height: Int,
): PixelRect {
    val topLeft = map(FloatPoint(left.toFloat(), top.toFloat()))
    val topRight = map(FloatPoint(right.toFloat(), top.toFloat()))
    val bottomLeft = map(FloatPoint(left.toFloat(), bottom.toFloat()))
    val bottomRight = map(FloatPoint(right.toFloat(), bottom.toFloat()))

    val mappedLeft = minOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
    val mappedTop = minOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
    val mappedRight = maxOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
    val mappedBottom = maxOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)

    val leftPx = floor(mappedLeft).toInt().coerceIn(0, width - 1)
    val topPx = floor(mappedTop).toInt().coerceIn(0, height - 1)
    val rightPx = ceil(mappedRight).toInt().coerceIn(leftPx + 1, width)
    val bottomPx = ceil(mappedBottom).toInt().coerceIn(topPx + 1, height)
    return PixelRect(leftPx, topPx, rightPx, bottomPx)
}
