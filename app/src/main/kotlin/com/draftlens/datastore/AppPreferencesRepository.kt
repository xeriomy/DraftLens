package com.draftlens.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.draftlens.capture.FrameSampler
import com.draftlens.overlay.OverlayPosition

private val Context.dataStore by preferencesDataStore(name = "draftlens_preferences")

class AppPreferencesRepository(private val context: Context) {

    private object Keys {
        val SAMPLING_INTERVAL = longPreferencesKey("sampling_interval_ms")
        val LAST_CAPTURE_AT = longPreferencesKey("last_capture_at_ms")
        val CONSENT_NOTE = stringPreferencesKey("consent_note")
        val FLOATING_CONTROL_ENABLED = booleanPreferencesKey("floating_control_enabled")
        val FLOATING_POSITION_X = floatPreferencesKey("floating_position_x")
        val FLOATING_POSITION_Y = floatPreferencesKey("floating_position_y")
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

    /** True only when the user asked for the floating freeze button (and it may be shown during capture). */
    val floatingControlEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.FLOATING_CONTROL_ENABLED] ?: false }

    /** Persisted normalized (0..1) position of the floating control; null until first placement. */
    val floatingPosition: Flow<OverlayPosition.Normalized?> = context.dataStore.data.map { prefs ->
        val x = prefs[Keys.FLOATING_POSITION_X]
        val y = prefs[Keys.FLOATING_POSITION_Y]
        if (x == null || y == null) null else OverlayPosition.Normalized(x, y)
    }

    suspend fun setFloatingControlEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FLOATING_CONTROL_ENABLED] = enabled }
    }

    suspend fun setFloatingPosition(position: OverlayPosition.Normalized) {
        context.dataStore.edit {
            it[Keys.FLOATING_POSITION_X] = position.x.coerceIn(0f, 1f)
            it[Keys.FLOATING_POSITION_Y] = position.y.coerceIn(0f, 1f)
        }
    }
}
