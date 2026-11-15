package com.draftlens.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HeroRecognitionStoreTest {

    private lateinit var directory: File
    private lateinit var scope: CoroutineScope
    private lateinit var store: HeroRecognitionStore

    @Before fun setUp() {
        directory = Files.createTempDirectory("hero-confirmations").toFile()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "confirmations.preferences_pb")
        }
        store = HeroRecognitionStore(dataStore)
    }

    @After fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test fun `confirm then load round-trips the hero and the timestamp`() = runBlocking {
        store.confirm("slot-1", HeroConfirmation("miya", "Miya", 1000L))
        store.confirm("slot-2", HeroConfirmation("layla", "Layla", 2000L))

        assertEquals(
            mapOf(
                "slot-1" to HeroConfirmation("miya", "Miya", 1000L),
                "slot-2" to HeroConfirmation("layla", "Layla", 2000L),
            ),
            store.load(),
        )
    }

    @Test fun `confirming the same slot replaces the previous hero`() = runBlocking {
        store.confirm("slot-1", HeroConfirmation("miya", "Miya", 1000L))
        store.confirm("slot-1", HeroConfirmation("layla", "Layla", 2000L))

        assertEquals(HeroConfirmation("layla", "Layla", 2000L), store.load()["slot-1"])
    }

    @Test fun `clear removes only the slot it is given`() = runBlocking {
        store.confirm("slot-1", HeroConfirmation("miya", "Miya", 1000L))
        store.confirm("slot-2", HeroConfirmation("layla", "Layla", 2000L))

        store.clear("slot-1")

        assertEquals(mapOf("slot-2" to HeroConfirmation("layla", "Layla", 2000L)), store.load())
    }

    @Test fun `a corrupt confirmation document reads as empty, never as garbage`() = runBlocking {
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "corrupt.preferences_pb")
        }
        dataStore.edit { it[stringPreferencesKey("hero_confirmations_json")] = "{ this is not json" }

        val corrupt = HeroRecognitionStore(dataStore)

        assertTrue(corrupt.load().isEmpty())
    }

    @Test fun `a document with an unsupported schema version reads as empty`() = runBlocking {
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope) {
            File(directory, "future.preferences_pb")
        }
        dataStore.edit {
            it[stringPreferencesKey("hero_confirmations_json")] =
                """{"schemaVersion":99,"entries":{"slot-1":{"heroId":"miya","heroName":"Miya","confirmedAtMs":1000}}}"""
        }

        val future = HeroRecognitionStore(dataStore)

        assertTrue(future.load().isEmpty())
    }
}
