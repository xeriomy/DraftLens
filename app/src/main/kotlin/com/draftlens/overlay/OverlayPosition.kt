package com.draftlens.overlay

import kotlin.math.roundToInt

/**
 * Position math for the single circular floating control.
 *
 * Positions are stored as normalized fractions (0..1) of the *free axis* (screen span
 * minus the button size), so one saved position survives rotation and density changes:
 * portrait saves y against a tall screen, landscape re-derives y against a short one from
 * the same fraction. Pixel math happens here, in pure Kotlin, and is unit-tested; the
 * WindowManager layer only paints the result.
 */
object OverlayPosition {

    enum class HorizontalEdge { LEFT, RIGHT }

    /** Normalized position; x and y in 0..1, both measured against the free axis. */
    data class Normalized(val x: Float, val y: Float)

    /** Pixel position relative to the top-left of the screen. */
    data class Pixels(val left: Int, val top: Int)

    /** Pixel position after snapping the horizontal axis to an edge. */
    data class Snapped(val left: Int, val top: Int, val edge: HorizontalEdge)

    private fun freeSpan(screenSizePx: Int, sizePx: Int): Int = (screenSizePx - sizePx).coerceAtLeast(0)

    fun pixelsToNormalized(leftPx: Int, topPx: Int, screenWidthPx: Int, screenHeightPx: Int, sizePx: Int): Normalized {
        val maxX = freeSpan(screenWidthPx, sizePx)
        val maxY = freeSpan(screenHeightPx, sizePx)
        val clampedLeft = leftPx.coerceIn(0, maxX)
        val clampedTop = topPx.coerceIn(0, maxY)
        return Normalized(
            x = if (maxX == 0) 0f else clampedLeft.toFloat() / maxX,
            y = if (maxY == 0) 0f else clampedTop.toFloat() / maxY,
        )
    }

    fun normalizedToPixels(normalized: Normalized, screenWidthPx: Int, screenHeightPx: Int, sizePx: Int): Pixels {
        val maxX = freeSpan(screenWidthPx, sizePx)
        val maxY = freeSpan(screenHeightPx, sizePx)
        val x = normalized.x.coerceIn(0f, 1f)
        val y = normalized.y.coerceIn(0f, 1f)
        return Pixels(left = (x * maxX).roundToInt(), top = (y * maxY).roundToInt())
    }

    /** Clamps an arbitrary pixel position back inside the screen. */
    fun clampToScreen(leftPx: Int, topPx: Int, screenWidthPx: Int, screenHeightPx: Int, sizePx: Int): Pixels =
        Pixels(
            left = leftPx.coerceIn(0, freeSpan(screenWidthPx, sizePx)),
            top = topPx.coerceIn(0, freeSpan(screenHeightPx, sizePx)),
        )

    /**
     * Snaps horizontally to the nearest edge by button-center comparison; the vertical
     * position is preserved (already clamped to the screen).
     */
    fun snapToNearestEdge(leftPx: Int, topPx: Int, screenWidthPx: Int, screenHeightPx: Int, sizePx: Int): Snapped {
        val clamped = clampToScreen(leftPx, topPx, screenWidthPx, screenHeightPx, sizePx)
        val center = clamped.left + sizePx / 2
        val atLeft = center <= screenWidthPx / 2
        return Snapped(
            left = if (atLeft) 0 else freeSpan(screenWidthPx, sizePx),
            top = clamped.top,
            edge = if (atLeft) HorizontalEdge.LEFT else HorizontalEdge.RIGHT,
        )
    }

}
