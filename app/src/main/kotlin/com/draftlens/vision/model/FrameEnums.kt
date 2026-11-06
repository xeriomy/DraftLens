package com.draftlens.vision.model

enum class PixelFormat { ARGB_8888 }

enum class FrameRotation { ROTATION_0, ROTATION_90, ROTATION_180, ROTATION_270 }

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
