package com.draftlens.heroes

import com.draftlens.vision.heroes.CatalogSnapshot
import com.draftlens.vision.heroes.HeroAlias
import com.draftlens.vision.heroes.HeroAsset
import com.draftlens.vision.heroes.HeroAssetVariant
import com.draftlens.vision.heroes.HeroCatalogCodec
import com.draftlens.vision.heroes.HeroCatalogEntry
import com.draftlens.vision.heroes.HeroCatalogSource
import com.draftlens.vision.heroes.HeroId
import com.draftlens.vision.heroes.SourceAttribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private fun entry(id: String, name: String) = HeroCatalogEntry(
    id = HeroId(id),
    canonicalName = name,
    aliases = listOf(HeroAlias("en", name)),
    roles = listOf("Marksman"),
    lanes = listOf("Gold"),
    specialties = listOf("Finisher"),
    assets = listOf(HeroAsset(HeroAssetVariant.HEAD, "https://example.com/$id.png")),
    externalIds = mapOf("roneArenaHeroId" to "1", "mlbbdexSlug" to id),
    source = HeroCatalogSource.RONE_ARENA,
)

private fun snapshot() = CatalogSnapshot(
    schemaVersion = CatalogSnapshot.CURRENT_SCHEMA,
    syncedAtMs = 1000L,
    sourceAttribution = listOf(SourceAttribution.RONE_ARENA, SourceAttribution.MLBBDEX),
    entries = listOf(entry("miya", "Miya"), entry("layla", "Layla")),
    assetFailures = emptyList(),
    crossCheck = null,
)

class HeroCatalogCodecTest {

    @Test fun `a snapshot round-trips byte for byte`() {
        val decoded = HeroCatalogCodec.decode(HeroCatalogCodec.encode(snapshot()))

        assertEquals(snapshot(), decoded)
    }

    @Test fun `encoding the current schema succeeds`() {
        assertTrue(HeroCatalogCodec.encode(snapshot()).contains("\"schemaVersion\":1"))
    }

    @Test fun `a newer schema version is refused`() {
        val raw = HeroCatalogCodec.encode(snapshot()).replace("\"schemaVersion\":1", "\"schemaVersion\":2")

        assertThrows(IllegalArgumentException::class.java) { HeroCatalogCodec.decode(raw) }
    }

    @Test fun `a blank hero id is refused at the model boundary`() {
        assertThrows(IllegalArgumentException::class.java) { HeroId("") }
        assertThrows(IllegalArgumentException::class.java) { HeroId("   ") }
    }

    @Test fun `a blank hero name is refused on encode`() {
        assertThrows(IllegalArgumentException::class.java) {
            HeroCatalogCodec.encode(snapshot().copy(entries = listOf(entry("miya", " "))))
        }
    }

    @Test fun `a non-https asset url is refused on encode`() {
        val insecure = entry("miya", "Miya").copy(
            assets = listOf(HeroAsset(HeroAssetVariant.HEAD, "http://example.com/miya.png")),
        )

        assertThrows(IllegalArgumentException::class.java) {
            HeroCatalogCodec.encode(snapshot().copy(entries = listOf(insecure)))
        }
    }

    @Test fun `duplicate hero ids are refused on decode`() {
        val raw = """
            {"schemaVersion":1,"syncedAtMs":1000,"sourceAttribution":[],"entries":[
              {"id":"miya","canonicalName":"Miya","aliases":[],"roles":[],"lanes":[],"specialties":[],"assets":[{"variant":"HEAD","url":"https://e.com/1.png"}],"externalIds":{},"source":"RONE_ARENA"},
              {"id":"miya","canonicalName":"Miya","aliases":[],"roles":[],"lanes":[],"specialties":[],"assets":[{"variant":"HEAD","url":"https://e.com/2.png"}],"externalIds":{},"source":"RONE_ARENA"}
            ],"assetFailures":[],"crossCheck":null}
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) { HeroCatalogCodec.decode(raw) }
    }

    @Test fun `unknown fields are rejected`() {
        val raw = HeroCatalogCodec.encode(snapshot()).replace("\"entries\":[", "\"surprise\":true,\"entries\":[")

        assertThrows(Exception::class.java) { HeroCatalogCodec.decode(raw) }
    }
}
