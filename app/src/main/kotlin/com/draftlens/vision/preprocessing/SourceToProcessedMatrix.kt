package com.draftlens.vision.preprocessing

import com.draftlens.vision.geometry.FrameTransform
import com.draftlens.vision.model.FrameRotation

/**
 * Pure-Kotlin 3x3 affine matrix (row-major, homogeneous `p' = M * p`) mapping source
 * pixels to processed pixels. [AndroidFramePreprocessor] builds its `android.graphics.Matrix`
 * from the same elements so the JVM-tested math is the production matrix. No Android types.
 */
internal fun sourceToProcessedMatrixElements(transform: FrameTransform): FloatArray {
    val oriented = orientedMatrix(transform)
    val cropOffset = translationMatrix(
        -transform.cropPixelRect.left.toFloat(),
        -transform.cropPixelRect.top.toFloat(),
    )
    val scale = scaleMatrix(
        transform.processedWidth.toFloat() / transform.cropPixelRect.width,
        transform.processedHeight.toFloat() / transform.cropPixelRect.height,
    )
    // p' = S * Tcrop * Torient * R * p; multiply in reverse order of application.
    return multiply3x3(scale, multiply3x3(cropOffset, oriented))
}

private fun orientedMatrix(transform: FrameTransform): FloatArray = when (transform.rotation) {
    FrameRotation.ROTATION_0 -> IDENTITY
    FrameRotation.ROTATION_90 -> floatArrayOf(
        0f, -1f, transform.sourceHeight.toFloat(),
        1f, 0f, 0f,
        0f, 0f, 1f,
    )
    FrameRotation.ROTATION_180 -> floatArrayOf(
        -1f, 0f, transform.sourceWidth.toFloat(),
        0f, -1f, transform.sourceHeight.toFloat(),
        0f, 0f, 1f,
    )
    FrameRotation.ROTATION_270 -> floatArrayOf(
        0f, 1f, 0f,
        -1f, 0f, transform.sourceWidth.toFloat(),
        0f, 0f, 1f,
    )
}

private fun translationMatrix(dx: Float, dy: Float): FloatArray = floatArrayOf(
    1f, 0f, dx,
    0f, 1f, dy,
    0f, 0f, 1f,
)

private fun scaleMatrix(sx: Float, sy: Float): FloatArray = floatArrayOf(
    sx, 0f, 0f,
    0f, sy, 0f,
    0f, 0f, 1f,
)

private fun multiply3x3(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(9)
    for (row in 0 until 3) {
        for (col in 0 until 3) {
            var sum = 0f
            for (k in 0 until 3) sum += a[row * 3 + k] * b[k * 3 + col]
            out[row * 3 + col] = sum
        }
    }
    return out
}

private val IDENTITY = floatArrayOf(
    1f, 0f, 0f,
    0f, 1f, 0f,
    0f, 0f, 1f,
)
