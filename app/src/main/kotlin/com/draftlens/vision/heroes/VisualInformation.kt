package com.draftlens.vision.heroes

import kotlin.math.abs

/**
 * The outcome of the deterministic visual-information analysis of one crop.
 *
 * [insufficientReason] is null when the crop carries enough structure for matching to mean
 * anything. All signals are deterministic functions of the pixels: no model, no randomness,
 * no platform APIs.
 *
 * **The thresholds are conservative heuristics, not calibrated MLBB recognition thresholds.**
 * They were chosen to separate "a blank gray rectangle" (the confirmed product failure) from
 * ordinary portraits. They are not an accuracy measurement and will be revisited only after
 * real-device evaluation with manually labeled samples.
 */
data class VisualInformationReport(
    val width: Int,
    val height: Int,
    /** Mean luma in [0, 255]. Informational only. */
    val meanLuminance: Float,
    /** Variance of per-pixel luma in luma². */
    val luminanceVariance: Float,
    /** Variance of the most varied color channel (R, G or B) in channel². */
    val colorVariance: Float,
    /** Fraction of horizontal/vertical neighbor pairs whose luma step exceeds the edge threshold, in [0, 1]. */
    val edgeDensity: Float,
    /** Raw count of neighbor pairs above the edge threshold. */
    val meaningfulDifferences: Int,
    /** Null when the crop is usable; otherwise a short reason it is not. */
    val insufficientReason: String?,
)

object VisualInformationAnalyzer {

    /** Crops below this on either axis carry no reliable detail for the matcher. */
    const val MIN_USEFUL_DIMENSION = 12

    /** Luma variance below this (std ~1.4/255) means a flat or single-colour image. */
    const val FLAT_LUMINANCE_VARIANCE = 2.0f

    /** Channel variance below this, together with flat luma, confirms a single-colour image. */
    const val FLAT_COLOR_VARIANCE = 2.0f

    /** Below this the image only shows smooth tonality: no texture, no edges, no detail. */
    const val DETAIL_LUMINANCE_VARIANCE = 8.0f

    /** Less than 1% of neighbor pairs stepping at least [EDGE_DELTA] luma units apart means no detail. */
    const val MIN_EDGE_DENSITY = 0.01f

    /** Luma step (0-255) that counts a neighbor pair as an edge. */
    const val EDGE_DELTA = 12

    private const val LUMA_R = 0.299f
    private const val LUMA_G = 0.587f
    private const val LUMA_B = 0.114f

    /**
     * Analyses [pixels] laid out row-major for a [width] x [height] image.
     * Never throws; a dimension/pixel-count mismatch is reported as insufficient.
     */
    fun analyze(pixels: IntArray, width: Int, height: Int): VisualInformationReport {
        if (width <= 0 || height <= 0 || pixels.size != width * height) {
            return VisualInformationReport(
                width = width.coerceAtLeast(0),
                height = height.coerceAtLeast(0),
                meanLuminance = 0f,
                luminanceVariance = 0f,
                colorVariance = 0f,
                edgeDensity = 0f,
                meaningfulDifferences = 0,
                insufficientReason = "the image dimensions do not match its pixel data",
            )
        }

        val count = width * height
        var lumaSum = 0.0
        var lumaSumOfSquares = 0.0
        var rSum = 0.0
        var gSum = 0.0
        var bSum = 0.0
        var rSumOfSquares = 0.0
        var gSumOfSquares = 0.0
        var bSumOfSquares = 0.0
        for (i in 0 until count) {
            val p = pixels[i]
            val r = ((p shr 16) and 0xFF).toDouble()
            val g = ((p shr 8) and 0xFF).toDouble()
            val b = (p and 0xFF).toDouble()
            val l = LUMA_R * r + LUMA_G * g + LUMA_B * b
            lumaSum += l
            lumaSumOfSquares += l * l
            rSum += r
            gSum += g
            bSum += b
            rSumOfSquares += r * r
            gSumOfSquares += g * g
            bSumOfSquares += b * b
        }
        val meanLuma = (lumaSum / count).toFloat()
        val lumaVariance = varianceOf(lumaSumOfSquares, lumaSum, count)
        val colorVariance = maxOf(
            varianceOf(rSumOfSquares, rSum, count),
            maxOf(varianceOf(gSumOfSquares, gSum, count), varianceOf(bSumOfSquares, bSum, count)),
        )

        // Edge pass keeps only the current and the previous row of luma values.
        var meaningfulDifferences = 0
        var previousRow = FloatArray(width)
        var currentRow = FloatArray(width)
        for (y in 0 until height) {
            val rowBase = y * width
            for (x in 0 until width) currentRow[x] = lumaOf(pixels[rowBase + x])
            for (x in 0 until width - 1) {
                if (abs(currentRow[x] - currentRow[x + 1]) > EDGE_DELTA) meaningfulDifferences++
            }
            if (y > 0) {
                for (x in 0 until width) {
                    if (abs(previousRow[x] - currentRow[x]) > EDGE_DELTA) meaningfulDifferences++
                }
            }
            val swap = previousRow
            previousRow = currentRow
            currentRow = swap
        }
        val neighborPairs = (width - 1) * height + width * (height - 1)
        val edgeDensity = if (neighborPairs > 0) meaningfulDifferences.toFloat() / neighborPairs else 0f

        val minSide = minOf(width, height)
        val reason = when {
            minSide < MIN_USEFUL_DIMENSION ->
                "smaller than ${MIN_USEFUL_DIMENSION}x$MIN_USEFUL_DIMENSION pixels"
            lumaVariance < FLAT_LUMINANCE_VARIANCE && colorVariance < FLAT_COLOR_VARIANCE ->
                "near-uniform (flat or single-colour)"
            edgeDensity < MIN_EDGE_DENSITY && lumaVariance < DETAIL_LUMINANCE_VARIANCE ->
                "no edges or texture (too little detail)"
            else -> null
        }

        return VisualInformationReport(
            width = width,
            height = height,
            meanLuminance = meanLuma,
            luminanceVariance = lumaVariance,
            colorVariance = colorVariance,
            edgeDensity = edgeDensity,
            meaningfulDifferences = meaningfulDifferences,
            insufficientReason = reason,
        )
    }

    private fun varianceOf(sumOfSquares: Double, sum: Double, count: Int): Float {
        val mean = sum / count
        return (sumOfSquares / count - mean * mean).coerceAtLeast(0.0).toFloat()
    }

    private fun lumaOf(p: Int): Float {
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        return LUMA_R * r + LUMA_G * g + LUMA_B * b
    }
}
