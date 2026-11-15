package com.draftlens.heroes

import com.draftlens.vision.heroes.HeroCatalogEntry
import com.draftlens.vision.heroes.HeroCatalogSource
import com.draftlens.vision.heroes.HeroCatalogSync
import com.draftlens.vision.heroes.HeroHttpResponse
import com.draftlens.vision.heroes.HeroHttpResult
import com.draftlens.vision.heroes.HeroId
import com.draftlens.vision.heroes.HeroIds
import com.draftlens.vision.heroes.CrossCheckResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTransport(
    private val routes: Map<String, HeroHttpResult> = emptyMap(),
) : com.draftlens.vision.heroes.HeroCatalogTransport {
    val requested = mutableListOf<String>()

    override fun get(url: String, maxBytes: Long, accept: String?): HeroHttpResult {
        requested += url
        return routes[url]
            ?: routes.entries.firstOrNull { (route, _) -> url.startsWith(route) }?.value
            ?: HeroHttpResult.Failure("no route for $url", 404)
    }
}

private fun ronePage(records: List<Pair<Long, String>>, total: Int): HeroHttpResult.Success {
    val body = records.joinToString(",") { (id, name) ->
        """{"data":{"hero":{"data":{"head":"https://wsrv.nl/?url=$id.head","name":"$name","smallmap":"https://wsrv.nl/?url=$id.map"}},"hero_id":$id}}"""
    }
    return HeroHttpResult.Success(
        HeroHttpResponse(
            """{"code":0,"message":"OK","data":{"records":[$body],"total":$total}}""".trimIndent().toByteArray(),
            "application/json",
        ),
    )
}

class HeroCatalogSyncTest {

    @Test fun `a hero id is the slugified English name`() {
        assertEquals("yi-sun-shin", HeroIds.slugOf("Yi Sun-shin"))
        assertEquals("chang-e", HeroIds.slugOf("Chang'e"))
        assertEquals("x-borg", HeroIds.slugOf("X.Borg"))
        assertEquals("miya", HeroIds.slugOf("Miya"))
    }

    @Test fun `pagination fetches every page until the total is reached`() {
        val page1 = (1..100).map { it.toLong() to "Hero-$it" }
        val page2 = (101..134).map { it.toLong() to "Hero-$it" }
        val transport = FakeTransport(
            mapOf(
                "https://arena.fastapicloud.dev/api/heroes?size=100&index=1&lang=en" to ronePage(page1, total = 134),
                "https://arena.fastapicloud.dev/api/heroes?size=100&index=2&lang=en" to ronePage(page2, total = 134),
                "https://mlbbdex.com/api/v1/heroes" to HeroHttpResult.Success(
                    HeroHttpResponse("""{"data":[],"total":0,"source":{"name":"w","url":"u","license":"CC BY-SA"}}""".toByteArray(), "application/json"),
                ),
            ),
        )
        val report = HeroCatalogSync(transport).sync().getOrThrow()

        assertEquals(134, report.entries.size)
        assertEquals(2, transport.requested.count { it.contains("heroes?size=") })
        assertEquals("roneArenaHeroId", report.entries.first().externalIds.keys.first())
    }

    @Test fun `duplicate hero ids are collapsed and counted`() {
        val records = listOf(1L to "Miya", 1L to "Miya", 2L to "Layla")
        val transport = FakeTransport(
            mapOf(
                "https://arena.fastapicloud.dev/api/heroes?size=100&index=1&lang=en" to ronePage(records, total = 3),
                "https://mlbbdex.com/api/v1/heroes" to HeroHttpResult.Failure("offline", 503),
            ),
        )
        val report = HeroCatalogSync(transport).sync().getOrThrow()

        assertEquals(2, report.entries.size)
        assertEquals(1, report.duplicateIds)
        assertTrue(report.crossCheck is CrossCheckResult.Unavailable)
    }

    @Test fun `invalid records are skipped and counted`() {
        val body = """
            {"data":{"hero":{"data":{"head":"","name":"","smallmap":""}},"hero_id":7}},
            {"data":{"hero":{"data":{"head":"https://wsrv.nl/?url=a","name":"Miya","smallmap":"https://wsrv.nl/?url=b"}},"hero_id":8}},
            {"data":{"hero":null,"hero_id":9}}
        """.trimIndent()
        val transport = FakeTransport(
            mapOf(
                "https://arena.fastapicloud.dev/api/heroes?size=100&index=1&lang=en" to HeroHttpResult.Success(
                    HeroHttpResponse(
                        """{"code":0,"message":"OK","data":{"records":[$body],"total":3}}""".toByteArray(),
                        "application/json",
                    ),
                ),
                "https://mlbbdex.com/api/v1/heroes" to HeroHttpResult.Failure("offline", 503),
            ),
        )
        val report = HeroCatalogSync(transport).sync().getOrThrow()

        assertEquals(1, report.entries.size)
        assertEquals(2, report.invalidRecords)
        assertEquals("miya", report.entries.single().id.value)
        assertEquals(HeroCatalogSource.RONE_ARENA, report.entries.single().source)
        assertEquals(listOf("HEAD", "SMALLMAP").toSet(), report.entries.single().assets.map { it.variant.name }.toSet())
    }

    @Test fun `roles and lanes come from MLBBDex by slug`() {
        val rone = ronePage(listOf(1L to "Luo Yi"), total = 1)
        val mlbbdex = HeroHttpResult.Success(
            HeroHttpResponse(
                """
                {"data":[{"slug":"luo-yi","name":"Luo Yi","roles":["Mage"],"lanes":["Mid"],"specialties":["Poke"]},
                         {"slug":"miya","name":"Miya","roles":["Marksman"],"lanes":["Gold"],"specialties":["Damage"]}],
                 "total":2,"source":{"name":"w","url":"u","license":"CC BY-SA"}}
                """.trimIndent().toByteArray(),
                "application/json",
            ),
        )
        val transport = FakeTransport(
            mapOf(
                "https://arena.fastapicloud.dev/api/heroes?size=100&index=1&lang=en" to rone,
                "https://mlbbdex.com/api/v1/heroes" to mlbbdex,
            ),
        )
        val report = HeroCatalogSync(transport).sync().getOrThrow()

        val entry = report.entries.single()
        assertEquals(listOf("Mage"), entry.roles)
        assertEquals(listOf("Mid"), entry.lanes)
        assertEquals(listOf("Poke"), entry.specialties)
        val cross = report.crossCheck as CrossCheckResult.Available
        assertEquals(1, cross.crossCheck.matchedNames)
        assertEquals(1, cross.crossCheck.missingInRoneArena.size)
        assertEquals(0, cross.crossCheck.missingInMlbbdex.size)
    }

    @Test fun `a full outage returns failure, not a bogus empty catalog`() {
        val transport = FakeTransport(
            mapOf(
                "https://arena.fastapicloud.dev/api/heroes" to HeroHttpResult.Failure("down", 503),
                "https://arena.rone.dev/api/heroes" to HeroHttpResult.Failure("down", 503),
                "https://mlbbdex.com/api/v1/heroes" to HeroHttpResult.Failure("down", 503),
            ),
        )
        assertTrue(HeroCatalogSync(transport).sync().isFailure)
    }

    @Test fun `the mirror host is tried when the primary host fails`() {
        val transport = FakeTransport(
            mapOf(
                "https://arena.fastapicloud.dev/api/heroes?size=100&index=1&lang=en" to HeroHttpResult.Failure("down", 503),
                "https://arena.rone.dev/api/heroes?size=100&index=1&lang=en" to ronePage(listOf(1L to "Miya"), total = 1),
                "https://mlbbdex.com/api/v1/heroes" to HeroHttpResult.Failure("down", 503),
            ),
        )
        val report = HeroCatalogSync(transport).sync().getOrThrow()

        assertEquals(1, report.entries.size)
        assertTrue(transport.requested.any { it.startsWith("https://arena.rone.dev/api/heroes") })
    }

    @Test fun `a non-zero API code is a failure`() {
        val transport = FakeTransport(
            mapOf(
                "https://arena.fastapicloud.dev/api/heroes?size=100&index=1&lang=en" to HeroHttpResult.Success(
                    HeroHttpResponse(
                        """{"code":1001,"message":"bad","data":{"records":[],"total":0}}""".toByteArray(),
                        "application/json",
                    ),
                ),
                "https://arena.rone.dev/api/heroes?size=100&index=1&lang=en" to HeroHttpResult.Success(
                    HeroHttpResponse(
                        """{"code":1001,"message":"bad","data":{"records":[],"total":0}}""".toByteArray(),
                        "application/json",
                    ),
                ),
            ),
        )
        assertTrue(HeroCatalogSync(transport).sync().isFailure)
    }

    @Test fun `aliases and external ids are provider-specific and never reused as identity`() {
        val transport = FakeTransport(
            mapOf(
                "https://arena.fastapicloud.dev/api/heroes?size=100&index=1&lang=en" to ronePage(listOf(42L to "Yi Sun-shin"), total = 1),
                "https://mlbbdex.com/api/v1/heroes" to HeroHttpResult.Failure("down", 503),
            ),
        )
        val report = HeroCatalogSync(transport).sync().getOrThrow()
        val entry = report.entries.single()

        assertEquals(HeroId("yi-sun-shin"), entry.id)
        assertEquals("42", entry.externalIds["roneArenaHeroId"])
        assertEquals("yi-sun-shin", entry.externalIds["mlbbdexSlug"])
    }
}
