package com.draftlens.vision.calibration

import kotlinx.coroutines.flow.Flow

/** Result of loading the calibration document; never collapses failure into an empty list. */
sealed interface CalibrationLoadResult {
    data class Success(val profiles: List<CalibrationProfile>) : CalibrationLoadResult
    data class UnsupportedSchema(val message: String) : CalibrationLoadResult
    data class Corrupt(val message: String) : CalibrationLoadResult
}

/**
 * Persistence for calibration profiles. Implementations store one JSON document;
 * unsupported or corrupt documents stay unreadable until [clearDocument].
 */
interface CalibrationRepository {
    val profiles: Flow<CalibrationLoadResult>
    suspend fun upsert(profile: CalibrationProfile): Result<Unit>
    suspend fun delete(profileId: String): Result<Unit>
    suspend fun clearDocument(): Result<Unit>
}
