package com.draftlens

import android.app.Application
import com.draftlens.datastore.AppPreferencesRepository
import com.draftlens.datastore.CalibrationProfileDataStore
import com.draftlens.vision.calibration.CalibrationRepository

class DraftLensApp : Application() {
    val preferences: AppPreferencesRepository by lazy {
        AppPreferencesRepository(this)
    }

    /** Calibration metadata only; never screenshots or pixels. */
    val calibrationProfiles: CalibrationRepository by lazy {
        CalibrationProfileDataStore(this)
    }
}
