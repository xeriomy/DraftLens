package com.draftlens.preprocessing

import com.draftlens.vision.geometry.FloatPoint
import com.draftlens.vision.geometry.FrameTransform
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.preprocessing.PreprocessingMath
import com.draftlens.vision.preprocessing.ProcessFrameRequest
import com.draftlens.vision.preprocessing.sourceToProcessedMatrixElements
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceToProcessedMatrixTest {

    @Test fun `rotation corrections are encoded without scale or offset when no crop applies`() {
        val identity = elementsFor(FrameRotation.ROTATION_0, crop = null, maxProcessedDimension = 500)
        assertArrayEquals(
            floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            identity,
            0f,
        )

        val rotated90 = elementsFor(FrameRotation.ROTATION_90, crop = null, maxProcessedDimension = 500)
        assertArrayEquals(
            floatArrayOf(0f, -1f, 3f, 1f, 0f, 0f, 0f, 0f, 1f),
            rotated90,
            0f,
        )

        val rotated180 = elementsFor(FrameRotation.ROTATION_180, crop = null, maxProcessedDimension = 500)
        assertArrayEquals(
            floatArrayOf(-1f, 0f, 4f, 0f, -1f, 3f, 0f, 0f, 1f),
            rotated180,
            0f,
        )

        val rotated270 = elementsFor(FrameRotation.ROTATION_270, crop = null, maxProcessedDimension = 500)
        assertArrayEquals(
            floatArrayOf(0f, 1f, 0f, -1f, 0f, 4f, 0f, 0f, 1f),
            rotated270,
            0f,
        )
    }

    @Test fun `matrix agrees with the transform for every rotation and a cropped downscaled case`() {
        val cases = listOf(
            Case(FrameRotation.ROTATION_0, crop = null, maxProcessedDimension = 500),
            Case(FrameRotation.ROTATION_90, crop = null, maxProcessedDimension = 500),
            Case(FrameRotation.ROTATION_180, crop = null, maxProcessedDimension = 500),
            Case(FrameRotation.ROTATION_270, crop = null, maxProcessedDimension = 500),
            Case(FrameRotation.ROTATION_90, crop = NormalizedRect(0f, 0.25f, 1f, 0.75f), maxProcessedDimension = 2),
            Case(FrameRotation.ROTATION_270, crop = NormalizedRect(0.25f, 0f, 0.75f, 1f), maxProcessedDimension = 2),
            Case(FrameRotation.ROTATION_0, crop = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f), maxProcessedDimension = 2),
        )

        for ((rotation, crop, maxProcessedDimension) in cases) {
            val transform = transformFor(rotation, crop, maxProcessedDimension)
            val elements = elementsFor(rotation, crop, maxProcessedDimension)
            var y = 0f
            while (y <= transform.sourceHeight) {
                var x = 0f
                while (x <= transform.sourceWidth) {
                    val (mappedX, mappedY) = applyRowMajor(elements, x, y)
                    val expected = transform.sourceToProcessed(FloatPoint(x, y))
                    assertEquals(
                        "rotation=$rotation x=$x y=$y",
                        expected.x.toDouble(),
                        mappedX.toDouble(),
                        1e-3,
                    )
                    assertEquals(
                        "rotation=$rotation x=$x y=$y",
                        expected.y.toDouble(),
                        mappedY.toDouble(),
                        1e-3,
                    )
                    x += 0.5f
                }
                y += 0.5f
            }
        }
    }

    private data class Case(
        val rotation: FrameRotation,
        val crop: NormalizedRect?,
        val maxProcessedDimension: Int,
    )

    private fun transformFor(
        rotation: FrameRotation,
        crop: NormalizedRect?,
        maxProcessedDimension: Int,
    ): FrameTransform = PreprocessingMath.plan(
        syntheticOriginal(width = 4, height = 3, rotation = rotation),
        ProcessFrameRequest(maxProcessedDimension = maxProcessedDimension, crop = crop),
    ).getOrThrow().transform

    private fun elementsFor(
        rotation: FrameRotation,
        crop: NormalizedRect?,
        maxProcessedDimension: Int,
    ): FloatArray = sourceToProcessedMatrixElements(
        transformFor(rotation, crop, maxProcessedDimension),
    )

    private fun applyRowMajor(m: FloatArray, x: Float, y: Float): Pair<Float, Float> =
        (m[0] * x + m[1] * y + m[2]) to (m[3] * x + m[4] * y + m[5])
}
