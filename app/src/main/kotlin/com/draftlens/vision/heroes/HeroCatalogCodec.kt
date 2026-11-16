package com.draftlens.vision.heroes

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Strict codec for the cached catalog snapshot. Unknown fields are rejected. */
object HeroCatalogCodec {

    private val json = Json {
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    fun encode(snapshot: CatalogSnapshot): String {
        validate(snapshot)
        return json.encodeToString(CatalogSnapshot.serializer(), snapshot)
    }

    fun decode(text: String): CatalogSnapshot {
        val snapshot = try {
            json.decodeFromString(CatalogSnapshot.serializer(), text)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("The cached hero catalog is not valid or uses an unknown field. ${e.message}", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("The cached hero catalog is invalid: ${e.message}", e)
        }
        validate(snapshot)
        return snapshot
    }

    private fun validate(snapshot: CatalogSnapshot) {
        if (snapshot.schemaVersion != CatalogSnapshot.CURRENT_SCHEMA) {
            throw IllegalArgumentException(
                "Unsupported hero catalog schema version ${snapshot.schemaVersion}. Expected ${CatalogSnapshot.CURRENT_SCHEMA}.",
            )
        }
        val seen = mutableSetOf<HeroId>()
        for (entry in snapshot.entries) {
            if (entry.id.value.isBlank()) throw IllegalArgumentException("A hero entry has a blank id.")
            if (entry.canonicalName.isBlank()) throw IllegalArgumentException("Hero '${entry.id.value}' has a blank name.")
            if (!seen.add(entry.id)) throw IllegalArgumentException("Duplicate hero id '${entry.id.value}' in the cached catalog.")
            for (asset in entry.assets) {
                if (!asset.url.startsWith("https://")) {
                    throw IllegalArgumentException("Hero '${entry.id.value}' asset is not HTTPS: ${asset.url}")
                }
            }
        }
    }
}
