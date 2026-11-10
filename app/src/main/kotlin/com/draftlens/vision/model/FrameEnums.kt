package com.draftlens.vision.model

enum class PixelFormat { ARGB_8888 }

enum class FrameRotation { ROTATION_0, ROTATION_90, ROTATION_180, ROTATION_270 }

/** Clockwise rotation in degrees, matching the Canvas rotation convention. */
val FrameRotation.degrees: Int
    get() = when (this) {
        FrameRotation.ROTATION_0 -> 0
        FrameRotation.ROTATION_90 -> 90
        FrameRotation.ROTATION_180 -> 180
        FrameRotation.ROTATION_270 -> 270
    }

enum class OrientationCategory {
    PORTRAIT, LANDSCAPE, SQUARE;

    companion object {
        fun from(width: Int, height: Int): OrientationCategory = when {
            width > height -> LANDSCAPE
            height > width -> PORTRAIT
            else -> SQUARE
        }
    }
}
