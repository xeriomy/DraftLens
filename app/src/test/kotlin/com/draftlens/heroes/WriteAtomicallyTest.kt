package com.draftlens.heroes

import com.draftlens.vision.heroes.FileHeroCatalogRepository
import com.draftlens.vision.heroes.HeroCatalogTransport
import com.draftlens.vision.heroes.HeroHttpResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

private object EmptyTransport : HeroCatalogTransport {
    override fun get(url: String, maxBytes: Long, accept: String?): HeroHttpResult =
        HeroHttpResult.Failure("no network in this test", 503)
}

/**
 * Failure-path coverage for the atomic snapshot/asset write. Successful writes are covered
 * end-to-end by the catalog tests; these tests focus on "never a partial target, never a
 * leftover temp file".
 */
class WriteAtomicallyTest {

    private fun tempDirs(): Pair<File, File> =
        Files.createTempDirectory("hero-catalog").toFile() to Files.createTempDirectory("hero-assets").toFile()

    private fun repo(data: File, cache: File, now: Long = 1234L): FileHeroCatalogRepository =
        FileHeroCatalogRepository(data, cache, EmptyTransport) { now }

    @Test fun `an atomic write stores the exact bytes`() {
        val (data, cache) = tempDirs()
        val target = File(data, "snapshot/catalog.json")

        val ok = repo(data, cache).writeAtomically(target, "payload".toByteArray())

        assertTrue(ok)
        assertEquals("payload", target.readText())
    }

    @Test fun `a failed atomic write leaves a directory target untouched and removes the temp file`() {
        val (data, cache) = tempDirs()
        // The atomic move cannot replace a non-empty/occupied directory; it must fail cleanly.
        val target = File(data, "catalog.json").also { it.mkdirs() }

        val ok = repo(data, cache).writeAtomically(target, "new".toByteArray())

        assertFalse(ok)
        assertTrue("the previous target must remain a directory", target.isDirectory)
        assertTrue("no temp file may remain", data.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun `a failed atomic write when the parent is a file reports failure without touching the blocker`() {
        val (data, cache) = tempDirs()
        val blocker = File(data, "blocker").also { it.writeText("file") }
        val target = File(blocker, "catalog.json")

        val ok = repo(data, cache).writeAtomically(target, "new".toByteArray())

        assertFalse(ok)
        assertEquals("file", blocker.readText())
        assertTrue("no temp file may remain next to the blocker", data.listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun `abandoned temp siblings older than an hour are swept on init`() {
        val (data, cache) = tempDirs()
        val assetsDir = File(cache, "hero-assets").also { it.mkdirs() }
        val now = System.currentTimeMillis()
        val stale = File(assetsDir, "miya-head.img.999.tmp").also { it.writeText("x") }
        val fresh = File(assetsDir, "layla-head.img.111.tmp").also { it.writeText("x") }
        stale.setLastModified(now - 2 * 60 * 60 * 1000)
        fresh.setLastModified(now - 60 * 1000)

        repo(data, cache, now = now)

        assertFalse("stale temp files are swept", stale.exists())
        assertTrue("fresh temp files are kept", fresh.exists())
    }

    private fun workingCatalogTransport(): HeroCatalogTransport = object : HeroCatalogTransport {
        private val body = """
            {"code":0,"message":"OK","data":{"records":[
              {"data":{"hero":{"data":{"head":"https://wsrv.nl/?url=miya.head","name":"Miya","smallmap":"https://wsrv.nl/?url=miya.map"}},"hero_id":1}}
            ],"total":1}}
        """.trimIndent().toByteArray()

        override fun get(url: String, maxBytes: Long, accept: String?): HeroHttpResult = when {
            url.startsWith("https://arena.fastapicloud.dev/api/heroes") ->
                HeroHttpResult.Success(com.draftlens.vision.heroes.HeroHttpResponse(body, "application/json"))
            else -> HeroHttpResult.Failure("no route for $url", 404)
        }
    }

    @Test fun `a failed snapshot write leaves no temp files behind`() = kotlinx.coroutines.runBlocking {
        val (data, cache) = tempDirs()
        // "hero-catalog" is a FILE, so the snapshot sibling temp file cannot be created.
        val blocker = File(data, "hero-catalog")
        blocker.writeText("blocked")
        val repo = FileHeroCatalogRepository(data, cache, workingCatalogTransport()) { 1234L }

        val result = repo.refresh()

        assertTrue("a refresh against a blocked data dir must fail", result.isFailure)
        assertTrue("no temp file may remain beside the blocker", data.listFiles()!!.none { it.name.endsWith(".tmp") })
    }
}
