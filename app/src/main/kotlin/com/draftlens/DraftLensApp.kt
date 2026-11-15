package com.draftlens

import android.app.Application
import com.draftlens.capture.CaptureVisionFrameSource
import com.draftlens.datastore.AppPreferencesRepository
import com.draftlens.datastore.CalibrationProfileDataStore
import com.draftlens.datastore.HeroRecognitionStore
import com.draftlens.vision.calibration.CalibrationRepository
import com.draftlens.vision.heroes.AndroidHeroCatalogTransport
import com.draftlens.vision.heroes.FileHeroCatalogRepository
import com.draftlens.vision.heroes.HeroCatalogRepository
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
     * The hero catalog: provider-independent model, file snapshot in filesDir and a
     * private image cache in cacheDir. Only public static catalog data is downloaded.
     */
    val heroCatalog: HeroCatalogRepository by lazy {
        FileHeroCatalogRepository(filesDir, cacheDir, AndroidHeroCatalogTransport())
    }

    /** Per-slot hero confirmations. Metadata only, never pixels. */
    val heroRecognitionStore: HeroRecognitionStore by lazy {
        HeroRecognitionStore(heroConfirmationsDataStore)
    }

    /**
     * The capture-to-vision adapter, owned by the process rather than by a screen, so a
     * retained Vision Lab keeps receiving frames when the Activity is recreated.
     */
    val visionSource: VisionFrameSource by lazy {
        CaptureVisionFrameSource.fromHolder(CoroutineScope(SupervisorJob() + Dispatchers.Default))
    }
}

/** Confirmation document lives in its own preferences store, like the calibration document. */
private val android.content.Context.heroConfirmationsDataStore by
    androidx.datastore.preferences.preferencesDataStore(name = "hero_recognitions")
