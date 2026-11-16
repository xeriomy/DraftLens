package com.draftlens.vision.heroes

import kotlinx.serialization.Serializable

/**
 * The hero catalog.
 *
 * Provider-independent identity is the normalized English name turned into a slug
 * ([HeroId]). Provider-specific ids (Rone Arena `hero_id`, MLBBDex `slug`) are stored in
 * [HeroCatalogEntry.externalIds]; display names are never used as identity.
 */

/** Stable, provider-independent identity: lower-case hyphenated slug. */
@Serializable
@JvmInline
value class HeroId(val value: String) {
    init {
        require(value.isNotBlank()) { "hero id must not be blank" }
    }
}

/** A localized name for a hero. Only English is synced in this sprint. */
@Serializable
data class HeroAlias(val language: String, val name: String)

@Serializable
enum class HeroAssetVariant { HEAD, HEAD_BIG, SMALLMAP, SQUAREHEAD, SQUAREHEAD_BIG }

/**
 * A downloadable reference image. [contentType] and [sizeBytes] are filled in after the
 * asset has been downloaded once; they stay null until then.
 */
@Serializable
data class HeroAsset(
    val variant: HeroAssetVariant,
    val url: String,
    val contentType: String? = null,
    val sizeBytes: Long? = null,
)

@Serializable
enum class HeroCatalogSource { RONE_ARENA, MLBBDEX }

@Serializable
data class SourceAttribution(
    val name: String,
    val url: String,
    val license: String,
    val notice: String,
) {
    companion object {
        val RONE_ARENA = SourceAttribution(
            name = "Rone Arena API (unofficial community project)",
            url = "https://arena.rone.dev",
            license = "BSD-3-Clause",
            notice = "Rone Arena is not affiliated with or endorsed by Moonton; the game is named only to describe the data. " +
                "Game names and visual assets remain the property of their respective rights holders.",
        )
        val MLBBDEX = SourceAttribution(
            name = "MLBBDex (independent fan project)",
            url = "https://mlbbdex.com",
            license = "CC BY-SA (community wiki data); written content MIT",
            notice = "Game visuals remain the property of Shanghai Moonton Technology Co., Ltd.",
        )
    }
}

/** One hero. Identity is [id]; names are for display only. */
@Serializable
data class HeroCatalogEntry(
    val id: HeroId,
    val canonicalName: String,
    val aliases: List<HeroAlias>,
    val roles: List<String>,
    val lanes: List<String>,
    val specialties: List<String>,
    val assets: List<HeroAsset>,
    val externalIds: Map<String, String>,
    val source: HeroCatalogSource,
)

/**
 * Completeness cross-check of the Rone Arena catalog against MLBBDex.
 * Not evidence; just a signal that the name sets roughly agree.
 */
@Serializable
data class CatalogCrossCheck(
    val roneArenaCount: Int,
    val mlbbdexCount: Int,
    val matchedNames: Int,
    val missingInRoneArena: List<String>,
    val missingInMlbbdex: List<String>,
)

/** What a full sync produced. */
@Serializable
data class CatalogSnapshot(
    val schemaVersion: Int,
    val syncedAtMs: Long,
    val sourceAttribution: List<SourceAttribution>,
    val entries: List<HeroCatalogEntry>,
    val assetFailures: List<String>,
    val crossCheck: CatalogCrossCheck?,
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

/** Result of the MLBBDex completeness check. */
sealed interface CrossCheckResult {
    data class Available(val crossCheck: CatalogCrossCheck) : CrossCheckResult
    data class Unavailable(val reason: String) : CrossCheckResult
}
