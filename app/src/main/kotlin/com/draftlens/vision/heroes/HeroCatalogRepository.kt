package com.draftlens.vision.heroes

import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** What the UI knows about the catalog. */
sealed interface HeroCatalogState {
    data object NotSynced : HeroCatalogState
    data class Syncing(val stage: String) : HeroCatalogState
    data class Ready(val entryCount: Int, val syncedAtMs: Long) : HeroCatalogState
    data class Error(val message: String, val cachedEntryCount: Int?) : HeroCatalogState
}

/**
 * The hero catalog: a provider-independent model, a file-based atomic snapshot and a
 * private image cache. No network happens until [refresh] is called; [snapshot] serves
 * cached offline browsing.
 */
interface HeroCatalogRepository {
    val state: StateFlow<HeroCatalogState>

    /** The in-memory or on-disk snapshot, or null when nothing usable is cached. */
    fun snapshot(): CatalogSnapshot?

    /** Downloads the catalog and its reference images; returns the stored snapshot. */
    suspend fun refresh(): Result<CatalogSnapshot>

    /** The cached image file for [id]/[variant] when it exists and is non-empty. */
    fun assetFile(id: HeroId, variant: HeroAssetVariant): File?
}
