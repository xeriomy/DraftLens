package com.draftlens.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Sprint 0 scaffolding: no entities yet. Adding the first entity in a later milestone
 * (hero cache, draft history) needs no structural change to this class.
 */
@Database(entities = [], version = 1, exportSchema = false)
abstract class DraftLensDatabase : RoomDatabase()
