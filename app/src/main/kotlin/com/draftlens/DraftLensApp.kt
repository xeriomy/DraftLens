package com.draftlens

import android.app.Application
import com.draftlens.datastore.AppPreferencesRepository

class DraftLensApp : Application() {
    val preferences: AppPreferencesRepository by lazy {
        AppPreferencesRepository(this)
    }
}
