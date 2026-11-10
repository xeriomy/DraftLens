package com.draftlens.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.draftlens.vision.calibration.CalibrationLoadResult
import com.draftlens.vision.calibration.CalibrationProfile
import com.draftlens.vision.model.OrientationCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Pins the calibration store contract: an unsupported or corrupt document stays
 * exactly as it is, mutations refuse to run, and clearing the document is the
 * only escape route back to an empty list.
 */
class CalibrationProfileDataStoreTest {

    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: CalibrationProfileDataStore

    private val profilesKey = stringPreferencesKey("profiles_json")

    @Before fun setUp() {
        directory = Files.createTempDirectory("draftlens-calibration").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "calibration.preferences_pb")
        }
        repository = CalibrationProfileDataStore(dataStore)
    }

    @After fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    private fun profile(id: String = "profile-1") = CalibrationProfile(
        profileId = id,
        label = "Ranked",
        targetAspectRatio = 16.0 / 9.0,
        expectedOrientation = OrientationCategory.LANDSCAPE,
        createdAtMs = 1_000L,
        updatedAtMs = 2_000L,
    )

    private suspend fun writeRaw(raw: String) {
        dataStore.edit { it[profilesKey] = raw }
    }

    private suspend fun readRaw(): String? = dataStore.data.first()[profilesKey]

    @Test fun `empty store loads an empty profile list`() = runTest {
        val result = repository.profiles.first()
        assertEquals(CalibrationLoadResult.Success(emptyList()), result)
    }

    @Test fun `upsert stores a profile that is listed afterwards`() = runTest {
        assertTrue(repository.upsert(profile()).isSuccess)
        assertEquals(CalibrationLoadResult.Success(listOf(profile())), repository.profiles.first())
    }

    @Test fun `upsert replaces a profile that has the same id`() = runTest {
        assertTrue(repository.upsert(profile()).isSuccess)
        val updated = profile().copy(label = "Updated", updatedAtMs = 3_000L)
        assertTrue(repository.upsert(updated).isSuccess)
        assertEquals(CalibrationLoadResult.Success(listOf(updated)), repository.profiles.first())
    }

    @Test fun `delete removes the profile`() = runTest {
        assertTrue(repository.upsert(profile()).isSuccess)
        assertTrue(repository.delete("profile-1").isSuccess)
        assertEquals(CalibrationLoadResult.Success(emptyList()), repository.profiles.first())
    }

    @Test fun `unsupported schema is reported and the document is not replaced`() = runTest {
        val raw = """{"schemaVersion":2,"profiles":[]}"""
        writeRaw(raw)

        val result = repository.profiles.first()
        assertTrue("expected UnsupportedSchema but was $result", result is CalibrationLoadResult.UnsupportedSchema)

        assertTrue(repository.upsert(profile()).isFailure)
        assertTrue(repository.delete("profile-1").isFailure)
        assertEquals(raw, readRaw())
    }

    @Test fun `corrupt document is reported and not silently reset`() = runTest {
        val raw = "{not json"
        writeRaw(raw)

        val result = repository.profiles.first()
        assertTrue("expected Corrupt but was $result", result is CalibrationLoadResult.Corrupt)

        assertTrue(repository.upsert(profile()).isFailure)
        assertEquals(raw, readRaw())
    }

    @Test fun `clear document is the escape route out of an unsupported document`() = runTest {
        writeRaw("""{"schemaVersion":2,"profiles":[]}""")

        assertTrue(repository.clearDocument().isSuccess)
        assertEquals(CalibrationLoadResult.Success(emptyList()), repository.profiles.first())
    }
}
