package com.draftlens.heroes

import com.draftlens.vision.heroes.FileHeroCatalogRepository
import com.draftlens.vision.heroes.HeroAssetVariant
import com.draftlens.vision.heroes.HeroCatalogState
import com.draftlens.vision.heroes.HeroHttpResponse
import com.draftlens.vision.heroes.HeroHttpResult
import com.draftlens.vision.heroes.HeroId
import com.draftlens.vision.testRealPng
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

private fun fakePng(): ByteArray = testRealPng()

private fun listRoute(): String = "https://arena.fastapicloud.dev/api/heroes?size=100&index=1&lang=en"

private fun listBody(): String = """
    {"code":0,"message":"OK","data":{"records":[
      {"data":{"hero":{"data":{"head":"https://wsrv.nl/?url=miya.head","name":"Miya","smallmap":"https://wsrv.nl/?url=miya.map"}},"hero_id":1}},
      {"data":{"hero":{"data":{"head":"https://wsrv.nl/?url=layla.head","name":"Layla","smallmap":"https://wsrv.nl/?url=layla.map"}},"hero_id":2}}
    ],"total":2}}
""".trimIndent()

private fun syncRoutes(pngBytes: ByteArray = fakePng(), contentType: String? = "image/png"): Map<String, HeroHttpResult> = mapOf(
    listRoute() to HeroHttpResult.Success(HeroHttpResponse(listBody().toByteArray(), "application/json")),
    "https://wsrv.nl/?url=miya.head" to HeroHttpResult.Success(HeroHttpResponse(pngBytes, contentType)),
    "https://wsrv.nl/?url=layla.head" to HeroHttpResult.Success(HeroHttpResponse(pngBytes, contentType)),
    "https://mlbbdex.com/api/v1/heroes" to HeroHttpResult.Failure("offline", 503),
)

class FileHeroCatalogRepositoryTest {

    private fun tempDirs(): Pair<File, File> =
        Files.createTempDirectory("hero-catalog").toFile() to Files.createTempDirectory("hero-assets").toFile()

    @Test fun `refresh stores the snapshot and downloads the assets`() = runBlocking {
        val (data, cache) = tempDirs()
        val repo = FileHeroCatalogRepository(data, cache, FakeTransport(syncRoutes())) { 1234L }

        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertEquals(1234L, result.getOrThrow().syncedAtMs)
        assertEquals(HeroCatalogState.Ready(2, 1234L), repo.state.value)
        assertEquals(2, repo.snapshot()?.entries?.size)
        assertNotNull(repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD))
        assertTrue(File(data, "hero-catalog/catalog.json").exists())
    }

    @Test fun `cached snapshot is served offline after a restart`() = runBlocking {
        val (data, cache) = tempDirs()
        val first = FileHeroCatalogRepository(data, cache, FakeTransport(syncRoutes())) { 1234L }
        first.refresh()

        // A fresh repository instance with a dead network must serve the cache.
        val second = FileHeroCatalogRepository(data, cache, FakeTransport()) { 9999L }

        assertEquals(HeroCatalogState.Ready(2, 1234L), second.state.value)
        assertEquals(2, second.snapshot()?.entries?.size)
        assertEquals("Layla", second.snapshot()?.entries?.last()?.canonicalName)
    }

    @Test fun `a magic-byte-invalid asset is rejected but the catalog is stored`() = runBlocking {
        val (data, cache) = tempDirs()
        val repo = FileHeroCatalogRepository(
            data,
            cache,
            FakeTransport(syncRoutes(pngBytes = "not an image".toByteArray())),
        ) { 1234L }

        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertEquals(listOf("Miya", "Layla"), result.getOrThrow().assetFailures)
        assertNull(repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD))
    }

    @Test fun `an oversized asset is rejected`() = runBlocking {
        val (data, cache) = tempDirs()
        val big = fakePng() + ByteArray(5 * 1024 * 1024)
        val repo = FileHeroCatalogRepository(
            data,
            cache,
            FakeTransport(syncRoutes(pngBytes = big)),
        ) { 1234L }

        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertEquals(listOf("Miya", "Layla"), result.getOrThrow().assetFailures)
    }

    @Test fun `a non-image content type is rejected`() = runBlocking {
        val (data, cache) = tempDirs()
        val repo = FileHeroCatalogRepository(
            data,
            cache,
            FakeTransport(syncRoutes(contentType = "text/html")),
        ) { 1234L }

        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertEquals(listOf("Miya", "Layla"), result.getOrThrow().assetFailures)
    }

    @Test fun `sync failure surfaces an error state and keeps the cache`() = runBlocking {
        val (data, cache) = tempDirs()
        val good = FileHeroCatalogRepository(data, cache, FakeTransport(syncRoutes())) { 1234L }
        good.refresh()

        val broken = FileHeroCatalogRepository(data, cache, FakeTransport()) { 9999L }
        val result = broken.refresh()

        assertTrue(result.isFailure)
        val state = broken.state.value
        assertTrue(state is HeroCatalogState.Error)
        assertEquals(2, (state as HeroCatalogState.Error).cachedEntryCount)
        // The cache is still usable for offline browsing.
        assertEquals(2, broken.snapshot()?.entries?.size)
    }

    @Test fun `a corrupt cached snapshot reports an error and is not used`() {
        val (data, cache) = tempDirs()
        val snapshotDir = File(data, "hero-catalog").also { it.mkdirs() }
        File(snapshotDir, "catalog.json").writeText("{ not json")

        val repo = FileHeroCatalogRepository(data, cache, FakeTransport()) { 1234L }

        assertTrue(repo.state.value is HeroCatalogState.Error)
        assertNull(repo.snapshot())
    }

    @Test fun `duplicate hero ids in a cached snapshot are refused`() {
        val (data, cache) = tempDirs()
        val snapshotDir = File(data, "hero-catalog").also { it.mkdirs() }
        // encode() would refuse a duplicate, so the cache can only contain this raw document
        // if something else wrote it; the codec must still refuse it.
        File(snapshotDir, "catalog.json").writeText(
            """
            {"schemaVersion":1,"syncedAtMs":1000,"sourceAttribution":[],"entries":[
              {"id":"miya","canonicalName":"Miya","aliases":[],"roles":[],"lanes":[],"specialties":[],"assets":[],"externalIds":{},"source":"RONE_ARENA"},
              {"id":"miya","canonicalName":"Miya","aliases":[],"roles":[],"lanes":[],"specialties":[],"assets":[],"externalIds":{},"source":"RONE_ARENA"}
            ],"assetFailures":[],"crossCheck":null}
            """.trimIndent(),
        )

        val repo = FileHeroCatalogRepository(data, cache, FakeTransport()) { 1234L }

        assertTrue(repo.state.value is HeroCatalogState.Error)
        assertNull(repo.snapshot())
    }

    private class CountingTransport(
        var routes: Map<String, HeroHttpResult>,
    ) : com.draftlens.vision.heroes.HeroCatalogTransport {
        val requested = mutableListOf<String>()
        override fun get(url: String, maxBytes: Long, accept: String?): HeroHttpResult {
            requested += url
            return routes[url] ?: HeroHttpResult.Failure("no route for $url", 404)
        }
    }

    private fun greenPng(): ByteArray = com.draftlens.vision.testRealGreenPng()

    private fun listBodyWithHeads(miya: String, layla: String): String = """
        {"code":0,"message":"OK","data":{"records":[
          {"data":{"hero":{"data":{"head":"$miya","name":"Miya","smallmap":"https://wsrv.nl/?url=miya.map"}},"hero_id":1}},
          {"data":{"hero":{"data":{"head":"$layla","name":"Layla","smallmap":"https://wsrv.nl/?url=layla.map"}},"hero_id":2}}
        ],"total":2}}
    """.trimIndent()

    @Test fun `an unchanged catalog does not re-download a valid cached asset`() = runBlocking {
        val (data, cache) = tempDirs()
        val transport = CountingTransport(syncRoutes())
        val repo = FileHeroCatalogRepository(data, cache, transport) { 1234L }
        repo.refresh()
        val downloadsAfterFirstSync = transport.requested.count { it.contains(".head") }
        assertEquals(2, downloadsAfterFirstSync)

        repo.refresh()

        assertEquals(2, transport.requested.count { it.contains(".head") })
    }

    @Test fun `a changed asset url forces a re-download and the new asset is served`() = runBlocking {
        val (data, cache) = tempDirs()
        val transport = CountingTransport(syncRoutes())
        val repo = FileHeroCatalogRepository(data, cache, transport) { 1234L }
        repo.refresh()

        transport.routes = mapOf(
            listRoute() to HeroHttpResult.Success(HeroHttpResponse(
                listBodyWithHeads("https://wsrv.nl/?url=miya2.head", "https://wsrv.nl/?url=layla2.head").toByteArray(),
                "application/json",
            )),
            "https://wsrv.nl/?url=miya2.head" to HeroHttpResult.Success(HeroHttpResponse(greenPng(), "image/png")),
            "https://wsrv.nl/?url=layla2.head" to HeroHttpResult.Success(HeroHttpResponse(greenPng(), "image/png")),
            "https://mlbbdex.com/api/v1/heroes" to HeroHttpResult.Failure("offline", 503),
        )
        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertTrue(transport.requested.contains("https://wsrv.nl/?url=miya2.head"))
        assertEquals(
            greenPng().toList(),
            repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD)!!.readBytes().toList(),
        )
    }

    @Test fun `an asset with a changed url is not served from the old cache when the refresh fails`() = runBlocking {
        val (data, cache) = tempDirs()
        val transport = CountingTransport(syncRoutes())
        val repo = FileHeroCatalogRepository(data, cache, transport) { 1234L }
        repo.refresh()

        transport.routes = mapOf(
            listRoute() to HeroHttpResult.Success(HeroHttpResponse(
                listBodyWithHeads("https://wsrv.nl/?url=miya2.head", "https://wsrv.nl/?url=layla2.head").toByteArray(),
                "application/json",
            )),
            // No route for miya2.head: its download fails. Layla still refreshes fine.
            "https://wsrv.nl/?url=layla2.head" to HeroHttpResult.Success(HeroHttpResponse(greenPng(), "image/png")),
            "https://mlbbdex.com/api/v1/heroes" to HeroHttpResult.Failure("offline", 503),
        )
        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().assetFailures.contains("Miya"))
        // The previous asset must not be exposed under the changed URL.
        assertNull(repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD))
    }

    @Test fun `a valid cached asset is still served when the network dies and the url is unchanged`() = runBlocking {
        val (data, cache) = tempDirs()
        val transport = CountingTransport(syncRoutes())
        val repo = FileHeroCatalogRepository(data, cache, transport) { 1234L }
        repo.refresh()

        transport.routes = emptyMap()
        assertTrue(repo.refresh().isFailure)

        assertNotNull(repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD))
    }

    @Test fun `a corrupted cached asset is not served`() = runBlocking {
        val (data, cache) = tempDirs()
        val repo = FileHeroCatalogRepository(data, cache, CountingTransport(syncRoutes())) { 1234L }
        repo.refresh()
        val file = File(cache, "hero-assets/miya-head.img")
        file.writeBytes("tampered".toByteArray())

        assertNull(repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD))
    }

    @Test fun `a forged url binding in the metadata blocks the asset`() = runBlocking {
        val (data, cache) = tempDirs()
        val repo = FileHeroCatalogRepository(data, cache, CountingTransport(syncRoutes())) { 1234L }
        repo.refresh()
        val meta = File(cache, "hero-assets/miya-head.img.meta")
        meta.writeText(meta.readText().replace("miya.head", "attacker.head"))

        assertNull(repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD))
    }

    @Test fun `a corrupt cache is re-downloaded on refresh`() = runBlocking {
        val (data, cache) = tempDirs()
        val transport = CountingTransport(syncRoutes())
        val repo = FileHeroCatalogRepository(data, cache, transport) { 1234L }
        repo.refresh()
        val file = File(cache, "hero-assets/miya-head.img")
        file.writeBytes("tampered".toByteArray())

        repo.refresh()

        assertEquals(fakePng().toList(), file.readBytes().toList())
        assertEquals(2, transport.requested.count { it == "https://wsrv.nl/?url=miya.head" })
    }

    @Test fun `a missing metadata sidecar forces a re-download`() = runBlocking {
        val (data, cache) = tempDirs()
        val transport = CountingTransport(syncRoutes())
        val repo = FileHeroCatalogRepository(data, cache, transport) { 1234L }
        repo.refresh()
        assertTrue(File(cache, "hero-assets/miya-head.img.meta").delete())

        repo.refresh()

        assertEquals(2, transport.requested.count { it == "https://wsrv.nl/?url=miya.head" })
    }

    @Test fun `an oversized cached asset is rejected at serve time`() = runBlocking {
        val (data, cache) = tempDirs()
        val repo = FileHeroCatalogRepository(data, cache, CountingTransport(syncRoutes())) { 1234L }
        repo.refresh()
        val file = File(cache, "hero-assets/miya-head.img")
        file.writeBytes(fakePng() + ByteArray(5 * 1024 * 1024))

        assertNull(repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD))
    }

    @Test fun `a truncated png is rejected at download time`() = runBlocking {
        val (data, cache) = tempDirs()
        val truncated = fakePng().copyOf(30)
        val repo = FileHeroCatalogRepository(
            data, cache, CountingTransport(syncRoutes(pngBytes = truncated)),
        ) { 1234L }

        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertEquals(listOf("Miya", "Layla"), result.getOrThrow().assetFailures)
        assertNull(repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD))
    }

    @Test fun `an html error page with a png signature is rejected`() = runBlocking {
        val (data, cache) = tempDirs()
        val html = byteArrayOf(
            0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(),
            0x0D, 0x0A, 0x1A, 0x0A,
        ) + "<html>not an image</html>".toByteArray()
        val repo = FileHeroCatalogRepository(
            data, cache, CountingTransport(syncRoutes(pngBytes = html)),
        ) { 1234L }

        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertEquals(listOf("Miya", "Layla"), result.getOrThrow().assetFailures)
        assertNull(repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD))
    }

    @Test fun `a missing content type is accepted`() = runBlocking {
        val (data, cache) = tempDirs()
        val repo = FileHeroCatalogRepository(
            data, cache, CountingTransport(syncRoutes(contentType = null)),
        ) { 1234L }

        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().assetFailures.isEmpty())
        assertNotNull(repo.assetFile(HeroId("miya"), HeroAssetVariant.HEAD))
    }
}
