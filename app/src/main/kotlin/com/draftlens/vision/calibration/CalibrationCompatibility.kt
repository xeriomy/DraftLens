package com.draftlens.vision.calibration

import com.draftlens.vision.model.OrientationCategory
import kotlin.math.abs

/**
 * Compatibility of a calibration profile against an oriented frame. Oriented
 * means after pixel-orientation correction and before crop or scale. Aspect
 * ratio tolerance is [RATIO_TOLERANCE] relative; orientation must match exactly.
 */
sealed interface CalibrationCompatibility {

    data object Compatible : CalibrationCompatibility

    /** Expected vs actual values for UI; [message] is safe to show. */
    data class Incompatible(
        val expectedAspectRatio: Double,
        val actualAspectRatio: Double,
        val expectedOrientation: OrientationCategory,
        val actualOrientation: OrientationCategory,
        val ratioMismatch: Boolean,
        val orientationMismatch: Boolean,
    ) : CalibrationCompatibility {
        val message: String get() = buildString {
            append("Calibration does not match this frame.")
            if (ratioMismatch) {
                append(" Expected aspect ratio ${format(expectedAspectRatio)}, actual ${format(actualAspectRatio)}.")
            }
            if (orientationMismatch) {
                append(" Expected orientation $expectedOrientation, actual $actualOrientation.")
            }
        }

        private fun format(value: Double): String = String.format(java.util.Locale.US, "%.4f", value)
    }

    companion object {
        const val RATIO_TOLERANCE: Double = 0.01

        fun check(
            profile: CalibrationProfile,
            orientedWidth: Int,
            orientedHeight: Int,
        ): CalibrationCompatibility {
            require(orientedWidth > 0 && orientedHeight > 0) {
                "oriented dimensions must be positive, was ${orientedWidth}x${orientedHeight}"
            }
            require(profile.targetAspectRatio.isFinite() && profile.targetAspectRatio > 0.0) {
                "targetAspectRatio must be finite and positive, was ${profile.targetAspectRatio}"
            }
            val actualAspectRatio = orientedWidth.toDouble() / orientedHeight.toDouble()
            val actualOrientation = OrientationCategory.from(orientedWidth, orientedHeight)
            val expected = profile.targetAspectRatio
            val ratioMismatch = abs(actualAspectRatio - expected) > expected * RATIO_TOLERANCE
            val orientationMismatch = profile.expectedOrientation != actualOrientation
            return if (!ratioMismatch && !orientationMismatch) {
                Compatible
            } else {
                Incompatible(
                    expectedAspectRatio = expected,
                    actualAspectRatio = actualAspectRatio,
                    expectedOrientation = profile.expectedOrientation,
                    actualOrientation = actualOrientation,
                    ratioMismatch = ratioMismatch,
                    orientationMismatch = orientationMismatch,
                )
            }
        }
    }
}
