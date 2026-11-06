package com.draftlens.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.draftlens.capture.FrameSampler

private val Context.dataStore by preferencesDataStore(name = "draftlens_preferences")

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val SAMPLING_INTERVAL = longPreferencesKey("sampling_interval_ms")
        val LAST_CAPTURE_AT = longPreferencesKey("last_capture_at_ms")
        val CONSENT_NOTE = stringPreferencesKey("consent_note")
    }

    val samplingIntervalMs: Flow<Long> = context.dataStore.data.map {
        it[Keys.SAMPLING_INTERVAL] ?: FrameSampler.DEFAULT_INTERVAL_MS
    }

    suspend fun setSamplingIntervalMs(ms: Long) {
        val clamped = FrameSampler.clampInterval(ms)
        context.dataStore.edit { it[Keys.SAMPLING_INTERVAL] = clamped }
    }

    suspend fun recordCaptureStarted() {
        context.dataStore.edit { it[Keys.LAST_CAPTURE_AT] = System.currentTimeMillis() }
    }

    suspend fun currentSamplingIntervalMs(): Long = samplingIntervalMs.first()
}
