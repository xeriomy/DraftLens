package com.draftlens

import android.app.Application
import com.draftlens.database.DraftLensDatabase
import com.draftlens.datastore.AppPreferencesRepository
import androidx.room.Room

class DraftLensApp : Application() {
    val database: DraftLensDatabase by lazy {
        Room.databaseBuilder(this, DraftLensDatabase::class.java, "draftlens.db").build()
    }

    val preferences: AppPreferencesRepository by lazy {
        AppPreferencesRepository(this)
    }
}
