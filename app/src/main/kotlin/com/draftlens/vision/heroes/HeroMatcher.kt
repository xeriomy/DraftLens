package com.draftlens.vision.heroes

import kotlin.math.abs

/**
 * Hero matching v0: a dHash perceptual hash plus a normalized 3x3 mean-RGB appearance
 * descriptor, combined into one deterministic similarity in [0, 1].
 *
 * The score is a heuristic, **not** a calibrated confidence or a probability. Treat it as a
 * ranking aid; the user always confirms. Comparison happens on fixed-size grids, so the
 * reference image resolution does not need to match the crop.
 */

/** Tunables for matching. The threshold is a starting point, calibrated on synthetic fixtures only. */
data class HeroMatchConfig(
    /** Below this similarity the result is "no match". */
    val noMatchThreshold: Float = 0.55f,
    val topK: Int = 3,
    /** Two candidates closer than this in similarity are reported as ambiguous. */
    val ambiguousGap: Float = 0.05f,
    /** Weight of the perceptual hash against the color descriptor in the combined score. */
    val hashWeight: Float = 0.6f,
) {
    init {
        require(noMatchThreshold in 0f..1f) { "noMatchThreshold must be within [0, 1]" }
        require(topK >= 1) { "topK must be at least 1" }
        require(ambiguousGap >= 0f) { "ambiguousGap must not be negative" }
        require(hashWeight in 0f..1f) { "hashWeight must be within [0, 1]" }
    }
}

data class HeroReference(
    val entry: HeroCatalogEntry,
    val pixels: IntArray,
    val width: Int,
    val height: Int,
)

data class HeroMatch(
    val entry: HeroCatalogEntry,
    /** Similarity in [0, 1]. Not a probability. */
    val similarity: Float,
    /** True when this candidate is within ambiguousGap of the next-best candidate. */
    val ambiguous: Boolean,
)

sealed interface HeroMatchResult {
    data class Candidates(val matches: List<HeroMatch>) : HeroMatchResult
    /** Evidence is weak; [bestSimilarity] is reported so the UI can say how far off it was. */
    data class NoMatch(val bestSimilarity: Float) : HeroMatchResult
}

object HeroMatcher {

    private const val HASH_WIDTH = 9
    private const val HASH_HEIGHT = 8
    private const val GRID = 3

    fun match(
        queryPixels: IntArray,
        queryWidth: Int,
        queryHeight: Int,
        references: List<HeroReference>,
        config: HeroMatchConfig = HeroMatchConfig(),
    ): HeroMatchResult {
        if (queryPixels.size != queryWidth * queryHeight || queryWidth <= 0 || queryHeight <= 0) {
            throw IllegalArgumentException("query pixels do not match the query dimensions")
        }
        val usable = references.filter { it.pixels.size == it.width * it.height && it.width > 0 && it.height > 0 }
        if (usable.isEmpty()) throw IllegalStateException("no usable hero reference images are available")

        val queryHash = perceptualHash(queryPixels, queryWidth, queryHeight)
        val queryColor = colorDescriptor(queryPixels, queryWidth, queryHeight)
        val ranked = usable
            .map { reference ->
                val hash = perceptualHash(reference.pixels, reference.width, reference.height)
                val color = colorDescriptor(reference.pixels, reference.width, reference.height)
                val hashScore = 1f - hammingDistance(queryHash, hash).toFloat() / 64f
                val colorScore = 1f - colorDistance(queryColor, color)
                val similarity = (config.hashWeight * hashScore + (1f - config.hashWeight) * colorScore)
                    .coerceIn(0f, 1f)
                reference.entry to similarity
            }
            .sortedByDescending { it.second }

        val best = ranked.first().second
        if (best < config.noMatchThreshold) return HeroMatchResult.NoMatch(best)
        val gapToNext = ranked.getOrNull(1)?.let { best - it.second } ?: Float.MAX_VALUE
        val ambiguous = gapToNext < config.ambiguousGap
        val matches = ranked.take(config.topK).mapIndexed { index, (entry, similarity) ->
            // The gap to the *runner-up* marks the top candidate; the gap to the *next* marks later ones.
            val entryAmbiguous = if (index == 0) {
                ambiguous
            } else {
                val gap = similarity - (ranked.getOrNull(index + 1)?.second ?: 0f)
                gap < config.ambiguousGap
            }
            HeroMatch(entry, similarity, entryAmbiguous)
        }
        return HeroMatchResult.Candidates(matches)
    }

    /** 64-bit dHash: downsampled grayscale, bit c of row r = gray > right neighbor. */
    fun perceptualHash(pixels: IntArray, width: Int, height: Int): Long {
        val gray = downsampleGray(pixels, width, height, HASH_WIDTH, HASH_HEIGHT)
        var hash = 0L
        for (row in 0 until HASH_HEIGHT) {
            for (col in 0 until HASH_WIDTH - 1) {
                hash = hash shl 1
                if (gray[row][col] > gray[row][col + 1]) hash = hash or 1L
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = (a xor b).countOneBits()

    /** 27 floats: the mean RGB of a 3x3 grid, normalized to [0, 1]. */
    fun colorDescriptor(pixels: IntArray, width: Int, height: Int): FloatArray {
        val grid = Array(GRID) { FloatArray(GRID * 3) }
        val counts = IntArray(GRID * GRID)
        for (y in 0 until height) {
            val gy = (y * GRID) / height
            for (x in 0 until width) {
                val gx = (x * GRID) / width
                val cell = gy * GRID + gx
                val p = pixels[y * width + x]
                grid[gy][gx * 3 + 0] += ((p shr 16) and 0xFF).toFloat()
                grid[gy][gx * 3 + 1] += ((p shr 8) and 0xFF).toFloat()
                grid[gy][gx * 3 + 2] += (p and 0xFF).toFloat()
                counts[cell]++
            }
        }
        val out = FloatArray(GRID * GRID * 3)
        for (gy in 0 until GRID) {
            for (gx in 0 until GRID) {
                val cell = gy * GRID + gx
                val count = counts[cell].coerceAtLeast(1)
                out[cell * 3 + 0] = grid[gy][gx * 3 + 0] / (255f * count)
                out[cell * 3 + 1] = grid[gy][gx * 3 + 1] / (255f * count)
                out[cell * 3 + 2] = grid[gy][gx * 3 + 2] / (255f * count)
            }
        }
        return out
    }

    /** Mean absolute difference of two descriptors, in [0, 1]. */
    fun colorDistance(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "descriptors must have the same size" }
        if (a.isEmpty()) return 0f
        var sum = 0f
        for (i in a.indices) sum += abs(a[i] - b[i])
        return sum / a.size
    }

    /** Area-average downsample to [columns] x [rows] grayscale values. */
    private fun downsampleGray(
        pixels: IntArray,
        width: Int,
        height: Int,
        columns: Int,
        rows: Int,
    ): Array<FloatArray> {
        val out = Array(rows) { FloatArray(columns) }
        for (row in 0 until rows) {
            val y0 = row * height / rows
            val y1 = (row + 1) * height / rows
            for (col in 0 until columns) {
                val x0 = col * width / columns
                val x1 = (col + 1) * width / columns
                var sum = 0f
                var count = 0
                for (y in y0 until y1.coerceAtMost(height)) {
                    for (x in x0 until x1.coerceAtMost(width)) {
                        val p = pixels[y * width + x]
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        sum += 0.299f * r + 0.587f * g + 0.114f * b
                        count++
                    }
                }
                out[row][col] = if (count > 0) sum / count else 0f
            }
        }
        return out
    }
}
