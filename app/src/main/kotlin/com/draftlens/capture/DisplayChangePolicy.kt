package com.draftlens.capture

/**
 * What a display change requires.
 *
 * The capture size and the device orientation change independently: a device can rotate
 * without the captured content changing size, and the size can change without the
 * orientation changing.
 */
sealed interface DisplayChange {

    /** The capture size is unchanged; only the rotation metadata needs updating. */
    data object RotationOnly : DisplayChange

    /** The capture size changed; the ImageReader and the VirtualDisplay must be replaced. */
    data class Resize(val width: Int, val height: Int) : DisplayChange
}

/**
 * Decides what a display change requires.
 *
 * Pure JVM logic, so the "same size, new rotation" case is unit-testable without Android.
 */
object DisplayChangePolicy {

    fun classify(
        currentWidth: Int,
        currentHeight: Int,
        newWidth: Int,
        newHeight: Int,
    ): DisplayChange =
        if (currentWidth == newWidth && currentHeight == newHeight) {
            DisplayChange.RotationOnly
        } else {
            DisplayChange.Resize(newWidth, newHeight)
        }
}
