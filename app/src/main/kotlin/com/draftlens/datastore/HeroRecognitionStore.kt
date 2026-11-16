package com.draftlens.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** One manually confirmed hero, keyed by the slot's stable id. Metadata only, never pixels. */
@Serializable
data class HeroConfirmation(
    val heroId: String,
    val heroName: String,
    val confirmedAtMs: Long,
)

@Serializable
private data class HeroConfirmationDocument(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val entries: Map<String, HeroConfirmation> = emptyMap(),
) {
    companion object {
        const val CURRENT_SCHEMA = 1
    }
}

/**
 * Persists the user's per-slot hero confirmations in the preferences DataStore under
 * `hero_confirmations_json`. Separate from the annotation schema on purpose: the slot codec
 * stays v1-compatible, and a corrupt document here never blocks annotations.
 */
class HeroRecognitionStore(private val dataStore: DataStore<Preferences>) {

    private val key = stringPreferencesKey("hero_confirmations_json")

    /** Emits the confirmations map on every change; empty on a missing or invalid document. */
    val confirmations: Flow<Map<String, HeroConfirmation>> = dataStore.data.map { prefs ->
        decode(prefs[key])
    }

    suspend fun load(): Map<String, HeroConfirmation> =
        dataStore.data.first()[key].let(::decode)

    suspend fun confirm(slotId: String, confirmation: HeroConfirmation) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key]).toMutableMap()
            current[slotId] = confirmation
            prefs[key] = encode(current)
        }
    }

    suspend fun clear(slotId: String) {
        dataStore.edit { prefs ->
            val current = decode(prefs[key]).toMutableMap()
            current.remove(slotId)
            prefs[key] = encode(current)
        }
    }

    private fun decode(raw: String?): Map<String, HeroConfirmation> {
        if (raw == null) return emptyMap()
        return try {
            val document = Json { ignoreUnknownKeys = false }.decodeFromString(HeroConfirmationDocument.serializer(), raw)
            if (document.schemaVersion != HeroConfirmationDocument.CURRENT_SCHEMA) return emptyMap()
            document.entries
        } catch (e: SerializationException) {
            emptyMap()
        } catch (e: IllegalArgumentException) {
            emptyMap()
        }
    }

    private fun encode(entries: Map<String, HeroConfirmation>): String =
        Json { encodeDefaults = true }.encodeToString(HeroConfirmationDocument.serializer(), HeroConfirmationDocument(entries = entries))
}
