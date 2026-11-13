package com.draftlens.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.draftlens.vision.calibration.CalibrationDocument
import com.draftlens.vision.calibration.CalibrationDocumentException
import com.draftlens.vision.calibration.CalibrationLoadResult
import com.draftlens.vision.calibration.CalibrationProfile
import com.draftlens.vision.calibration.CalibrationProfileCodec
import com.draftlens.vision.calibration.CalibrationRepository
import com.draftlens.vision.calibration.UnsupportedCalibrationSchemaException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Calibration persistence backed by a single JSON string under `profiles_json`
 * in the `draftlens_calibration` preferences store. Stores calibration metadata
 * only: never screenshots, bitmaps, transform ids or pixels. Mutating operations
 * read and decode the current document first; on unsupported/corrupt data they
 * fail without writing. [clearDocument] is the only escape route.
 */
private val Context.calibrationDataStore by preferencesDataStore(name = "draftlens_calibration")

class CalibrationProfileDataStore(private val dataStore: DataStore<Preferences>) : CalibrationRepository {

    constructor(context: Context) : this(context.calibrationDataStore)

    private object Keys {
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
    }

    override val profiles: Flow<CalibrationLoadResult> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.PROFILES_JSON] ?: return@map CalibrationLoadResult.Success(emptyList())
        toLoadResult(raw)
    }

    override suspend fun upsert(profile: CalibrationProfile): Result<Unit> = mutate { document ->
        val index = document.profiles.indexOfFirst { it.profileId == profile.profileId }
        val updated = if (index >= 0) {
            document.profiles.toMutableList().also { it[index] = profile }
        } else {
            document.profiles + profile
        }
        document.copy(profiles = updated)
    }

    override suspend fun delete(profileId: String): Result<Unit> = mutate { document ->
        document.copy(profiles = document.profiles.filterNot { it.profileId == profileId })
    }

    override suspend fun clearDocument(): Result<Unit> = runCatching {
        dataStore.edit { it.remove(Keys.PROFILES_JSON) }
    }

    /**
     * Reads and decodes the current document inside the edit transaction; any
     * failure aborts the edit, so unsupported/corrupt data is never overwritten.
     */
    private suspend fun mutate(transform: (CalibrationDocument) -> CalibrationDocument): Result<Unit> = try {
        dataStore.edit { prefs ->
            val document = decode(prefs[Keys.PROFILES_JSON])
            prefs[Keys.PROFILES_JSON] = CalibrationProfileCodec.encodeDocument(transform(document))
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun decode(raw: String?): CalibrationDocument =
        if (raw == null) CalibrationDocument() else CalibrationProfileCodec.decodeDocument(raw)

    private fun toLoadResult(raw: String): CalibrationLoadResult = try {
        CalibrationLoadResult.Success(CalibrationProfileCodec.decodeDocument(raw).profiles)
    } catch (e: UnsupportedCalibrationSchemaException) {
        CalibrationLoadResult.UnsupportedSchema(e.message ?: "Unsupported calibration schema.")
    } catch (e: CalibrationDocumentException) {
        CalibrationLoadResult.Corrupt(e.message ?: "The calibration data is invalid.")
    }
}
