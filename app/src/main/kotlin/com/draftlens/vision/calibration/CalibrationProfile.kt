package com.draftlens.vision.calibration

import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.model.OrientationCategory
import kotlinx.serialization.Serializable

/** Current calibration JSON schema version (envelope, profile and slot all 1). */
object CalibrationSchema {
    const val CURRENT: Int = 1
}

/**
 * Calibration metadata for one reference layout. Never holds screenshots,
 * bitmaps, transform ids or pixels; [slots] are manual annotations only.
 * [targetAspectRatio] is always `orientedWidth / orientedHeight`, where oriented
 * means after pixel-orientation correction and before crop or scale.
 */
@Serializable
data class CalibrationProfile(
    val schemaVersion: Int = CalibrationSchema.CURRENT,
    val profileId: String,
    val label: String? = null,
    val targetAspectRatio: Double,
    val expectedOrientation: OrientationCategory,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val slots: List<DraftSlot> = emptyList(),
)

@Serializable
data class CalibrationDocument(
    val schemaVersion: Int = CalibrationSchema.CURRENT,
    val profiles: List<CalibrationProfile> = emptyList(),
)
