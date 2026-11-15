package com.draftlens.heroes

import com.draftlens.vision.heroes.HeroCatalogEntry
import com.draftlens.vision.heroes.HeroAsset
import com.draftlens.vision.heroes.HeroAssetVariant
import com.draftlens.vision.heroes.HeroCatalogSource
import com.draftlens.vision.heroes.HeroId
import com.draftlens.vision.heroes.HeroMatchConfig
import com.draftlens.vision.heroes.HeroMatchResult
import com.draftlens.vision.heroes.HeroMatcher
import com.draftlens.vision.heroes.HeroReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun solid(width: Int, height: Int, rgb: Int): IntArray =
    IntArray(width * height) { (0xFF000000.toInt() or rgb) }

private fun gradient(width: Int, height: Int): IntArray =
    IntArray(width * height) { i ->
        val x = i % width
        val y = i / width
        0xFF000000.toInt() or (x * 255 / width) or ((y * 255 / height) shl 8) or (((x + y) * 255 / (width + height)) shl 16)
    }

private fun entry(id: String, name: String) = HeroCatalogEntry(
    id = HeroId(id),
    canonicalName = name,
    aliases = emptyList(),
    roles = emptyList(),
    lanes = emptyList(),
    specialties = emptyList(),
    assets = listOf(HeroAsset(HeroAssetVariant.HEAD, "https://example.com/$id.png")),
    externalIds = emptyMap(),
    source = HeroCatalogSource.RONE_ARENA,
)

private fun reference(id: String, pixels: IntArray, width: Int, height: Int) =
    HeroReference(entry(id, id), pixels, width, height)

class HeroMatcherTest {

    private val miya = reference("miya", gradient(32, 32), 32, 32)
    private val layla = reference("layla", solid(32, 32, 0x0000FF), 32, 32)
    private val laylaLike = reference("layla-like", solid(32, 32, 0x0000FE), 32, 32)

    @Test fun `identical images are a perfect match`() {
        val result = HeroMatcher.match(
            queryPixels = gradient(32, 32),
            queryWidth = 32,
            queryHeight = 32,
            references = listOf(miya, layla),
        ) as HeroMatchResult.Candidates

        assertEquals("miya", result.matches.first().entry.id.value)
        assertEquals(1f, result.matches.first().similarity, 0.001f)
    }

    @Test fun `ranking order follows similarity`() {
        val query = solid(16, 16, 0x0000FF)
        val result = HeroMatcher.match(query, 16, 16, listOf(miya, layla, laylaLike)) as HeroMatchResult.Candidates

        assertEquals("layla", result.matches[0].entry.id.value)
        assertEquals("layla-like", result.matches[1].entry.id.value)
        assertTrue(result.matches[0].similarity >= result.matches[1].similarity)
    }

    @Test fun `evidence below the threshold yields no match with the best similarity reported`() {
        val black = solid(16, 16, 0x000000)
        val result = HeroMatcher.match(black, 16, 16, listOf(miya), HeroMatchConfig(noMatchThreshold = 0.99f))

        assertTrue(result is HeroMatchResult.NoMatch)
        assertTrue((result as HeroMatchResult.NoMatch).bestSimilarity < 0.99f)
    }

    @Test fun `an ambiguous gap marks the top candidate`() {
        val query = solid(16, 16, 0x0000FF)
        val result = HeroMatcher.match(
            query,
            16,
            16,
            listOf(layla, laylaLike),
            HeroMatchConfig(ambiguousGap = 0.10f),
        ) as HeroMatchResult.Candidates

        assertTrue(result.matches[0].ambiguous)
    }

    @Test fun `references with corrupt dimensions are skipped`() {
        val corrupt = reference("corrupt", IntArray(10), 3, 3)
        val result = HeroMatcher.match(solid(8, 8, 0x0000FF), 8, 8, listOf(corrupt, layla))

        assertTrue(result is HeroMatchResult.Candidates)
        assertEquals("layla", (result as HeroMatchResult.Candidates).matches.first().entry.id.value)
    }

    @Test fun `hamming distance counts differing bits`() {
        assertEquals(0, HeroMatcher.hammingDistance(0b1010, 0b1010))
        assertEquals(2, HeroMatcher.hammingDistance(0b1010, 0b0101))
        assertEquals(64, HeroMatcher.hammingDistance(Long.MAX_VALUE, Long.MIN_VALUE + 1))
    }

    @Test fun `color distance is zero for identical descriptors`() {
        val a = HeroMatcher.colorDescriptor(gradient(16, 16), 16, 16)

        assertEquals(0f, HeroMatcher.colorDistance(a, a), 0f)
    }

    @Test fun `a malformed query throws instead of lying`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            HeroMatcher.match(IntArray(5), 2, 2, listOf(miya))
        }
    }

    @Test fun `ranking is deterministic for repeated calls`() {
        val query = solid(16, 16, 0x00FF00)
        val a = HeroMatcher.match(query, 16, 16, listOf(miya, layla, laylaLike))
        val b = HeroMatcher.match(query, 16, 16, listOf(miya, layla, laylaLike))

        assertEquals(a, b)
    }
}
