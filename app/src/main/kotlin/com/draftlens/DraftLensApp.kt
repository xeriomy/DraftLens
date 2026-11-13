package com.draftlens

import android.app.Application
import com.draftlens.capture.CaptureVisionFrameSource
import com.draftlens.datastore.AppPreferencesRepository
import com.draftlens.datastore.CalibrationProfileDataStore
import com.draftlens.vision.calibration.CalibrationRepository
import com.draftlens.vision.model.VisionFrameSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DraftLensApp : Application() {
    val preferences: AppPreferencesRepository by lazy {
        AppPreferencesRepository(this)
    }

    /** Calibration metadata only; never screenshots or pixels. */
    val calibrationProfiles: CalibrationRepository by lazy {
        CalibrationProfileDataStore(this)
    }

    /**
     * The capture-to-vision adapter, owned by the process rather than by a screen, so a
     * retained Vision Lab keeps receiving frames when the Activity is recreated.
     */
    val visionSource: VisionFrameSource by lazy {
        CaptureVisionFrameSource.fromHolder(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}
