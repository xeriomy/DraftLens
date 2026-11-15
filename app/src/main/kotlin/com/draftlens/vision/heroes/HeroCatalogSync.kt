package com.draftlens.vision.heroes

import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pulls the hero catalog from Rone Arena and cross-checks it against MLBBDex.
 *
 * Pure logic against [HeroCatalogTransport], so tests run with fake responses.
 * HTTPS only, one page size, a page cap, and duplicate-ID protection are enforced here.
 */
class HeroCatalogSync(
    private val transport: HeroCatalogTransport,
    private val roneArenaBases: List<String> = listOf(
        "https://arena.fastapicloud.dev/api",
        "https://arena.rone.dev/api",
    ),
    private val mlbbdexBase: String = "https://mlbbdex.com/api/v1",
    private val pageSize: Int = 100,
    private val maxPages: Int = 20,
) {

    data class SyncReport(
        val entries: List<HeroCatalogEntry>,
        val invalidRecords: Int,
        val duplicateIds: Int,
        val crossCheck: CrossCheckResult,
    )

    fun sync(): Result<SyncReport> {
        val mlbbdex = fetchMlbbdexNames() // null on failure: cross-check degrades, sync continues
        val list = when (val outcome = fetchRoneArenaList(mlbbdex)) {
            is ResultData.Success -> outcome.value
            is ResultData.Failure -> return Result.failure(outcome.cause)
        }
        val (entries, invalid, duplicates) = list
        return Result.success(
            SyncReport(
                entries = entries,
                invalidRecords = invalid,
                duplicateIds = duplicates,
                crossCheck = crossCheck(mlbbdex, entries),
            ),
        )
    }

    // region fetching

    private sealed interface ResultData<out T> {
        data class Success<T>(val value: T) : ResultData<T>
        data class Failure(val cause: Exception) : ResultData<Nothing>
    }

    /** Rone Arena list with a one-shot mirror fallback: same code and data on the other host. */
    private fun fetchRoneArenaList(mlbbdex: List<MlbbdexName>?): ResultData<Triple<List<HeroCatalogEntry>, Int, Int>> {
        for (base in roneArenaBases) {
            when (val outcome = fetchRoneArenaListFrom(base, mlbbdex)) {
                is ResultData.Success -> return outcome
                is ResultData.Failure -> continue // try the mirror, if there is one
            }
        }
        return ResultData.Failure(IllegalStateException("No Rone Arena host responded with a usable catalog"))
    }

    private fun fetchRoneArenaListFrom(
        base: String,
        mlbbdex: List<MlbbdexName>?,
    ): ResultData<Triple<List<HeroCatalogEntry>, Int, Int>> {
        val seen = LinkedHashMap<Long, RoneHeroRecord>()
        var invalid = 0
        var duplicates = 0
        var total = Int.MAX_VALUE
        var page = 1
        while (page <= maxPages && seen.size < total) {
            val url = "$base/heroes?size=$pageSize&index=$page&lang=en"
            val raw = when (val result = transport.get(url, MAX_JSON_BYTES, "application/json")) {
                is HeroHttpResult.Success -> result.response.bytes
                is HeroHttpResult.Failure ->
                    return ResultData.Failure(IllegalStateException("Rone Arena catalog request failed: ${result.message}"))
            }
            val parsed = try {
                json.decodeFromString(RoneHeroesResponse.serializer(), raw.decodeToString())
            } catch (e: SerializationException) {
                return ResultData.Failure(IllegalArgumentException("Rone Arena catalog page $page is not valid JSON: ${e.message}", e))
            } catch (e: IllegalArgumentException) {
                return ResultData.Failure(IllegalArgumentException("Rone Arena catalog page $page is invalid: ${e.message}", e))
            }
            if (parsed.code != 0) {
                return ResultData.Failure(IllegalStateException("Rone Arena catalog page $page returned code ${parsed.code} (${parsed.message})"))
            }
            total = parsed.data.total.coerceAtLeast(0)
            val records = parsed.data.records
            if (records.isEmpty()) break
            for (record in records) {
                val heroId = record.data.heroId
                val hero = record.data.hero?.data
                if (heroId == null || hero == null || hero.name.isNullOrBlank() ||
                    (hero.head.isNullOrBlank() && hero.smallmap.isNullOrBlank())
                ) {
                    invalid++
                    continue
                }
                if (seen.putIfAbsent(heroId, record) != null) {
                    duplicates++
                }
            }
            if (records.size < pageSize) break
            page++
        }
        if (seen.isEmpty()) {
            return ResultData.Failure(IllegalStateException("Rone Arena catalog returned no usable hero records"))
        }
        val bySlug = mlbbdex?.associateBy { it.slug } ?: emptyMap()
        val entries = seen.values.mapNotNull { record ->
            val hero = record.data.hero!!.data!!
            val name = hero.name!!.trim()
            val id = HeroId(HeroIds.slugOf(name))
            val reference = bySlug[id.value]
            buildList {
                hero.head?.takeIf { it.isNotBlank() }?.let { add(HeroAsset(HeroAssetVariant.HEAD, it)) }
                hero.smallmap?.takeIf { it.isNotBlank() }?.let { add(HeroAsset(HeroAssetVariant.SMALLMAP, it)) }
            }.takeIf { it.isNotEmpty() }?.let { assets ->
                HeroCatalogEntry(
                    id = id,
                    canonicalName = name,
                    aliases = emptyList(),
                    roles = reference?.roles.orEmpty(),
                    lanes = reference?.lanes.orEmpty(),
                    specialties = reference?.specialties.orEmpty(),
                    assets = assets,
                    externalIds = mapOf(
                        "roneArenaHeroId" to record.data.heroId!!.toString(),
                        "mlbbdexSlug" to id.value,
                    ),
                    source = HeroCatalogSource.RONE_ARENA,
                )
            }
        }
        return ResultData.Success(Triple(entries, invalid, duplicates))
    }

    private data class MlbbdexName(
        val slug: String,
        val name: String,
        val roles: List<String>,
        val lanes: List<String>,
        val specialties: List<String>,
    )

    /** Names from MLBBDex for the cross-check and for roles/lanes. Null when unreachable. */
    private fun fetchMlbbdexNames(): List<MlbbdexName>? {
        val raw = when (val result = transport.get("$mlbbdexBase/heroes", MAX_JSON_BYTES, "application/json")) {
            is HeroHttpResult.Success -> result.response.bytes
            is HeroHttpResult.Failure -> return null
        }
        return try {
            json.decodeFromString(MlbbdexResponse.serializer(), raw.decodeToString())
                .data
                .filter { it.slug.isNotBlank() && it.name.isNotBlank() }
                .map {
                    MlbbdexName(
                        slug = it.slug,
                        name = it.name,
                        roles = it.roles,
                        lanes = it.lanes,
                        specialties = it.specialties,
                    )
                }
        } catch (e: Exception) {
            null
        }
    }

    private fun crossCheck(mlbbdex: List<MlbbdexName>?, entries: List<HeroCatalogEntry>): CrossCheckResult {
        val names = mlbbdex ?: return CrossCheckResult.Unavailable("MLBBDex /api/v1/heroes could not be read")
        val roneSlugs = entries.map { it.id.value }.toSet()
        val dexSlugs = names.map { it.slug }.toSet()
        return CrossCheckResult.Available(
            CatalogCrossCheck(
                roneArenaCount = roneSlugs.size,
                mlbbdexCount = dexSlugs.size,
                matchedNames = roneSlugs.intersect(dexSlugs).size,
                missingInRoneArena = names.filter { it.slug !in roneSlugs }.map { it.name },
                missingInMlbbdex = entries.filter { it.id.value !in dexSlugs }.map { it.canonicalName },
            ),
        )
    }

    // endregion

    private companion object {
        const val MAX_JSON_BYTES = 16L * 1024L * 1024L // 16 MiB cap on catalog pages
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }

    // region wire formats (Rone Arena list and MLBBDex list)

    @Serializable
    private data class RoneHeroesResponse(
        val code: Int = 0,
        val message: String = "",
        val data: RoneListData = RoneListData(),
    )

    @Serializable
    private data class RoneListData(
        val records: List<RoneHeroRecord> = emptyList(),
        val total: Int = 0,
    )

    @Serializable
    private data class RoneHeroRecord(val data: RoneRecordData = RoneRecordData())

    @Serializable
    private data class RoneRecordData(
        val hero: RoneHero? = null,
        val hero_id: Long? = null,
    ) {
        val heroId: Long? get() = hero_id
    }

    @Serializable
    private data class RoneHero(val data: RoneHeroDetails? = null)

    @Serializable
    private data class RoneHeroDetails(
        val head: String? = null,
        val name: String? = null,
        val smallmap: String? = null,
    )

    @Serializable
    private data class MlbbdexResponse(val data: List<MlbbdexHero> = emptyList())

    @Serializable
    private data class MlbbdexHero(
        val slug: String = "",
        val name: String = "",
        val roles: List<String> = emptyList(),
        val lanes: List<String> = emptyList(),
        val specialties: List<String> = emptyList(),
    )

    // endregion
}
