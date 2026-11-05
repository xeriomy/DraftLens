package com.draftlens.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.draftlens.capture.CaptureStateHolder
import com.draftlens.capture.FrameSampler
import com.draftlens.datastore.AppPreferencesRepository
import com.draftlens.models.CaptureStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI-facing view of the in-process capture state holder.
 * The foreground service owns capture; this class only exposes its flows and user actions.
 */
class HomeViewModel(
    private val preferences: AppPreferencesRepository
) : ViewModel() {

    val uiState = CaptureStateHolder.state
    val latestFrame = CaptureStateHolder.latestFrame

    private val _permissionStatus = MutableStateFlow("Not requested")
    val permissionStatus: StateFlow<String> = _permissionStatus.asStateFlow()

    private val _intervalMs = MutableStateFlow(FrameSampler.DEFAULT_INTERVAL_MS)
    val intervalMs: StateFlow<Long> = _intervalMs.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.samplingIntervalMs.collect { _intervalMs.value = it }
        }
    }

    fun onPermissionRequested() {
        CaptureStateHolder.setStatus(CaptureStatus.AWAITING_PERMISSION)
        _permissionStatus.value = "Requested — awaiting system consent"
    }

    fun onPermissionGranted() {
        _permissionStatus.value = "Granted (session-scoped)"
        viewModelScope.launch { preferences.recordCaptureStarted() }
    }

    fun onPermissionDenied() {
        _permissionStatus.value = "Denied by user"
        CaptureStateHolder.setStatus(CaptureStatus.IDLE)
    }

    fun onCaptureStopped() {
        _permissionStatus.value = "Capture stopped"
    }

    fun setIntervalMs(ms: Long) {
        viewModelScope.launch { preferences.setSamplingIntervalMs(ms) }
    }
}
