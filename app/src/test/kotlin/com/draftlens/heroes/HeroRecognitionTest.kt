package com.draftlens.heroes

import com.draftlens.vision.heroes.HeroCatalogEntry
import com.draftlens.vision.heroes.HeroAsset
import com.draftlens.vision.heroes.HeroAssetVariant
import com.draftlens.vision.heroes.HeroCatalogSource
import com.draftlens.vision.heroes.HeroId
import com.draftlens.vision.heroes.HeroIdentifyOutcome
import com.draftlens.vision.heroes.HeroRecognition
import com.draftlens.vision.heroes.HeroReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(id: String) = HeroCatalogEntry(
    id = HeroId(id),
    canonicalName = id,
    aliases = emptyList(),
    roles = emptyList(),
    lanes = emptyList(),
    specialties = emptyList(),
    assets = listOf(HeroAsset(HeroAssetVariant.HEAD, "https://example.com/$id.png")),
    externalIds = emptyMap(),
    source = HeroCatalogSource.RONE_ARENA,
)

class HeroRecognitionTest {

    private fun gradient(width: Int, height: Int): IntArray =
        IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            0xFF000000.toInt() or (x * 255 / width) or ((y * 255 / height) shl 8) or (((x + y) * 255 / (width + height)) shl 16)
        }

    private val miyaReference = HeroReference(entry("miya"), gradient(32, 32), 32, 32)

    @Test fun `an exact reference answers with the hero name and similarity`() {
        val outcome = HeroRecognition.identify(
            pixels = gradient(32, 32),
            width = 32,
            height = 32,
            references = listOf(miyaReference),
        )

        val candidates = (outcome as HeroIdentifyOutcome.Candidates).heroCandidates
        assertEquals(1, candidates.size)
        assertEquals("miya", candidates.single().hero.canonicalName)
        assertEquals(1f, candidates.single().similarity, 0.001f)
    }

    @Test fun `no usable references produce an honest failure, not a guess`() {
        val outcome = HeroRecognition.identify(
            pixels = gradient(16, 16),
            width = 16,
            height = 16,
            references = listOf(HeroReference(entry("broken"), IntArray(3), 1, 1)),
        )

        assertTrue(outcome is HeroIdentifyOutcome.Failed)
        assertTrue((outcome as HeroIdentifyOutcome.Failed).message.contains("Sync the hero catalog"))
    }

    @Test fun `evidence below the threshold reports no match instead of a wrong hero`() {
        val black = IntArray(32 * 32) { 0xFF000000.toInt() }
        val outcome = HeroRecognition.identify(
            pixels = black,
            width = 32,
            height = 32,
            references = listOf(miyaReference),
            config = com.draftlens.vision.heroes.HeroMatchConfig(noMatchThreshold = 0.9f),
        )

        assertTrue(outcome is HeroIdentifyOutcome.NoMatch)
    }
}
