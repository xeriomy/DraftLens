package com.draftlens.vision.heroes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * File-based hero catalog: one snapshot JSON plus one file per reference image and a small
 * metadata sidecar per image (`*.meta`) binding it to its source URL and content hash.
 *
 * Guarantees:
 * - Snapshot writes are temp-file-then-atomic-move into place; an interrupted write never
 *   leaves a half-read or truncated snapshot, and a failed write leaves the previous
 *   snapshot untouched (there is no direct-write fallback).
 * - A cached asset is reused only when it passes full validation: present, within the
 *   4 MiB cap, matching sidecar (URL, size, SHA-256), magic bytes and a container
 *   structure check (plus an actual decode on Android, see `DraftLensApp`). Corrupt or
 *   stale files are never served; a changed source URL forces a re-download.
 * - Refresh keeps the previous valid asset when the download fails; nothing is deleted
 *   on failure paths, and a stale asset is never exposed under a changed URL.
 * - Temp files are created as siblings of the target and swept after an hour on init.
 */
class FileHeroCatalogRepository(
    private val dataDir: File,
    private val cacheDir: File,
    private val transport: HeroCatalogTransport,
    // Trailing lambdas bind to the last parameter, so the clock must come last;
    // imageValidator is a SAM too — a lambda would otherwise land on it.
    private val imageValidator: ReferenceImageValidator = StructuralImageValidator,
    private val clock: () -> Long = System::currentTimeMillis,
) : HeroCatalogRepository {

    private val snapshotFile = File(dataDir, "hero-catalog/catalog.json")
    private val assetsDir = File(cacheDir, "hero-assets")

    @Volatile
    private var cached: CatalogSnapshot? = null

    private val _state = MutableStateFlow<HeroCatalogState>(HeroCatalogState.NotSynced)
    override val state: StateFlow<HeroCatalogState> = _state.asStateFlow()

    init {
        sweepStaleTempFiles(snapshotFile.parentFile)
        sweepStaleTempFiles(assetsDir)
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
        val expectedUrl = currentAssetUrl(id, variant) ?: return null
        return file.takeIf { isAssetFresh(it, expectedUrl) }
    }

    // region internals

    private fun assetFileFor(id: HeroId, variant: HeroAssetVariant): File =
        File(assetsDir, "${id.value}-${variant.name.lowercase()}.img")

    private fun metaFileFor(assetFile: File): File = File(assetsDir, "${assetFile.name}.meta")

    /** The URL the current catalog advertises for this asset; null when unknown. */
    private fun currentAssetUrl(id: HeroId, variant: HeroAssetVariant): String? =
        (cached ?: snapshot())
            ?.entries?.firstOrNull { it.id == id }
            ?.assets?.firstOrNull { it.variant == variant }
            ?.url

    /**
     * Full serve-time validation of an existing cache entry against [expectedUrl].
     * A file that fails anything here is invisible to the rest of the app.
     */
    private fun isAssetFresh(file: File, expectedUrl: String): Boolean {
        if (!file.isFile) return false
        val length = file.length()
        if (length == 0L || length > MAX_IMAGE_BYTES) return false
        val meta = readMeta(metaFileFor(file)) ?: return false
        if (meta.url != expectedUrl) return false
        if (meta.bytes != length) return false
        if (meta.contentType != null && !meta.contentType.startsWith("image/")) return false
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        if (sha256Hex(bytes) != meta.sha256) return false
        if (!looksLikeImage(bytes)) return false
        return imageValidator.isDecodable(bytes)
    }

    /** Returns the names of heroes whose images failed to download or validate. */
    private fun downloadAssets(entries: List<HeroCatalogEntry>): List<String> {
        val failures = mutableListOf<String>()
        for (entry in entries) {
            val asset = entry.assets.firstOrNull { it.variant == HeroAssetVariant.HEAD } ?: continue
            val target = assetFileFor(entry.id, HeroAssetVariant.HEAD)
            // Unchanged URL and a valid file: keep the cache untouched.
            if (isAssetFresh(target, asset.url)) continue
            // Changed URL or corrupt cache: re-download. A failure keeps the on-disk files
            // untouched; they are served only if they pass validation for the *current* URL.
            if (!refreshAsset(asset, target)) {
                failures += entry.canonicalName
            }
        }
        return failures
    }

    /**
     * Fetches and fully validates the image, then atomically replaces the sidecar metadata
     * before the image itself. A stale metadata sidecar never matches a fresh image and
     * vice versa, so a crash or failure in between leaves the entry unserved and
     * self-healing on the next refresh. The previous image is only ever replaced by a
     * fully validated download.
     */
    private fun refreshAsset(asset: HeroAsset, target: File): Boolean {
        val downloaded = fetchAsset(asset) ?: return false
        val meta = HeroAssetMeta(
            url = asset.url,
            sha256 = sha256Hex(downloaded.bytes),
            bytes = downloaded.bytes.size.toLong(),
            contentType = downloaded.contentType,
            savedAtMs = clock(),
        )
        val metaBytes = Json { encodeDefaults = true }
            .encodeToString(HeroAssetMeta.serializer(), meta)
            .toByteArray(Charsets.UTF_8)
        if (!writeAtomically(metaFileFor(target), metaBytes)) return false
        if (!writeAtomically(target, downloaded.bytes)) {
            // The metadata now describes an image that was not written; drop it so the
            // previous pair is not shadowed by a mismatch that never heals.
            runCatching { metaFileFor(target).delete() }
            return false
        }
        return true
    }

    /** HTTPS + cap + content type + magic bytes + container validation. */
    private fun fetchAsset(asset: HeroAsset): DownloadedAsset? {
        if (!asset.url.startsWith("https://")) return null
        val response = when (val result = transport.get(asset.url, MAX_IMAGE_BYTES, null)) {
            is HeroHttpResult.Success -> result.response
            is HeroHttpResult.Failure -> return null
        }
        if (response.bytes.size > MAX_IMAGE_BYTES) return null
        val contentType = response.contentType?.lowercase()
        if (contentType != null && !contentType.startsWith("image/")) return null
        val bytes = response.bytes
        if (!looksLikeImage(bytes)) return null
        if (!imageValidator.isDecodable(bytes)) return null
        return DownloadedAsset(bytes, contentType)
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

    private fun readMeta(file: File): HeroAssetMeta? = try {
        Json { ignoreUnknownKeys = true }.decodeFromString(HeroAssetMeta.serializer(), file.readText())
    } catch (e: Exception) {
        null
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(separator = "") { "%02x".format(it) }

    /**
     * Writes [bytes] to [target] by writing a sibling temp file in the same directory
     * (same filesystem), flushing and syncing it, then atomically moving it into place.
     * If the atomic move cannot be completed, the previous [target] is preserved and the
     * temp file is deleted. Never writes directly to [target].
     */
    internal fun writeAtomically(target: File, bytes: ByteArray): Boolean {
        val parent = target.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val tmp = try {
            File.createTempFile("${target.name}.", ".tmp", parent)
        } catch (e: Exception) {
            return false
        }
        return try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.flush()
                // Best effort on filesystems that support it; close is the correctness floor.
                runCatching { out.fd.sync() }
            }
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            true
        } catch (e: Exception) {
            // Atomic replacement was not possible: keep the previous target untouched.
            runCatching { tmp.delete() }
            false
        }
    }

    /** Removes abandoned `*.tmp` siblings older than an hour; never touches other files. */
    private fun sweepStaleTempFiles(dir: File?) {
        if (dir == null) return
        runCatching {
            val cutoff = clock() - TEMP_SWEEP_AGE_MS
            dir.listFiles { file -> file.isFile && file.name.endsWith(".tmp") }?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }
    }

    // endregion

    private companion object {
        const val MAX_IMAGE_BYTES = 4L * 1024L * 1024L // 4 MiB per reference image
        const val TEMP_SWEEP_AGE_MS = 60L * 60L * 1000L // sweep abandoned temp siblings hourly
    }
}

/** Sidecar binding one cached reference image to its source and content. */
@Serializable
internal data class HeroAssetMeta(
    val url: String,
    val sha256: String,
    val bytes: Long,
    val contentType: String? = null,
    val savedAtMs: Long,
)

private data class DownloadedAsset(val bytes: ByteArray, val contentType: String?)
