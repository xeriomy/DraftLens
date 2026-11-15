package com.draftlens.vision.heroes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * File-based catalog: one atomic snapshot JSON plus one file per reference image.
 * Writes go to a `.tmp` sibling first and are renamed into place, so an interrupted
 * sync never leaves a half-read snapshot behind.
 */
class FileHeroCatalogRepository(
    private val dataDir: File,
    private val cacheDir: File,
    private val transport: HeroCatalogTransport,
    private val clock: () -> Long = System::currentTimeMillis,
) : HeroCatalogRepository {

    private val snapshotFile = File(dataDir, "hero-catalog/catalog.json")
    private val assetsDir = File(cacheDir, "hero-assets")

    @Volatile
    private var cached: CatalogSnapshot? = null

    private val _state = MutableStateFlow<HeroCatalogState>(HeroCatalogState.NotSynced)
    override val state: StateFlow<HeroCatalogState> = _state.asStateFlow()

    init {
        // If the cache cannot be decoded this records an Error state and returns null.
        cached = readDiskSnapshot()
        cached?.let { _state.value = HeroCatalogState.Ready(it.entries.size, it.syncedAtMs) }
        // Otherwise leave whatever readDiskSnapshot put in place (Error), or NotSynced.
    }

    override fun snapshot(): CatalogSnapshot? {
        cached?.let { return it }
        val disk = readDiskSnapshot() ?: return null
        cached = disk
        return disk
    }

    override suspend fun refresh(): Result<CatalogSnapshot> {
        _state.value = HeroCatalogState.Syncing("Downloading catalog")
        val sync = HeroCatalogSync(transport)
        val report = sync.sync().getOrElse { failure ->
            _state.value = HeroCatalogState.Error(
                message = failure.message ?: "The hero catalog could not be downloaded",
                cachedEntryCount = cached?.entries?.size,
            )
            return Result.failure(failure)
        }
        _state.value = HeroCatalogState.Syncing("Downloading hero images")
        val assetFailures = downloadAssets(report.entries)
        val snapshot = CatalogSnapshot(
            schemaVersion = CatalogSnapshot.CURRENT_SCHEMA,
            syncedAtMs = clock(),
            sourceAttribution = listOf(SourceAttribution.RONE_ARENA, SourceAttribution.MLBBDEX),
            entries = report.entries,
            assetFailures = assetFailures,
            crossCheck = (report.crossCheck as? CrossCheckResult.Available)?.crossCheck,
        )
        if (!writeAtomically(snapshotFile, HeroCatalogCodec.encode(snapshot).toByteArray())) {
            val failure = IllegalStateException("The hero catalog snapshot could not be written")
            _state.value = HeroCatalogState.Error(failure.message!!, cached?.entries?.size)
            return Result.failure(failure)
        }
        cached = snapshot
        _state.value = HeroCatalogState.Ready(snapshot.entries.size, snapshot.syncedAtMs)
        return Result.success(snapshot)
    }

    override fun assetFile(id: HeroId, variant: HeroAssetVariant): File? {
        val file = assetFileFor(id, variant)
        return file.takeIf { it.exists() && it.length() > 0L }
    }

    // region internals

    private fun assetFileFor(id: HeroId, variant: HeroAssetVariant): File =
        File(assetsDir, "${id.value}-${variant.name.lowercase()}.img")

    /** Returns the names of heroes whose images failed to download or validate. */
    private fun downloadAssets(entries: List<HeroCatalogEntry>): List<String> {
        val failures = mutableListOf<String>()
        for (entry in entries) {
            val asset = entry.assets.firstOrNull { it.variant == HeroAssetVariant.HEAD } ?: continue
            val target = assetFileFor(entry.id, HeroAssetVariant.HEAD)
            if (target.exists() && target.length() > 0L) continue // keep the cache; refresh is about the catalog
            val ok = downloadAsset(asset, target)
            if (!ok) {
                runCatching { target.delete() }
                failures += entry.canonicalName
            }
        }
        return failures
    }

    /** HTTPS + image content check via magic bytes, with a size cap enforced by the transport. */
    private fun downloadAsset(asset: HeroAsset, target: File): Boolean {
        val response = when (val result = transport.get(asset.url, MAX_IMAGE_BYTES, null)) {
            is HeroHttpResult.Success -> result.response
            is HeroHttpResult.Failure -> return false
        }
        if (asset.url.startsWith("https://").not()) return false
        if (response.bytes.size > MAX_IMAGE_BYTES) return false
        val contentType = response.contentType?.lowercase()
        if (contentType != null && !contentType.startsWith("image/")) return false
        if (!looksLikeImage(response.bytes)) return false
        return writeAtomically(target, response.bytes)
    }

    private fun looksLikeImage(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val isPng = bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() && bytes[2] == 'N'.code.toByte()
        val isJpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val isWebp = bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte()
        val isGif = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte()
        return isPng || isJpeg || isWebp || isGif
    }

    private fun readDiskSnapshot(): CatalogSnapshot? {
        val file = snapshotFile
        if (!file.exists()) return null
        return try {
            HeroCatalogCodec.decode(file.readText())
        } catch (e: Exception) {
            _state.value = HeroCatalogState.Error(
                "The cached hero catalog is invalid and must be re-downloaded: ${e.message}",
                cachedEntryCount = null,
            )
            null
        }
    }

    private fun writeAtomically(target: File, bytes: ByteArray): Boolean = runCatching {
        val parent = target.parentFile ?: return false
        parent.mkdirs()
        val tmp = File(parent, "${target.name}.tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) {
            // renameTo can fail across file systems; fall back to a copy+delete.
            target.writeBytes(bytes)
            tmp.delete()
        }
        true
    }.getOrDefault(false)

    // endregion

    private companion object {
        const val MAX_IMAGE_BYTES = 4L * 1024L * 1024L // 4 MiB per reference image
    }
}
