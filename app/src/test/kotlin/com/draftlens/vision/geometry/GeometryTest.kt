package com.draftlens.vision.geometry

import com.draftlens.vision.model.FrameRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GeometryTest {

    @Test fun `normalized rect rejects reversed or out of range bounds`() {
        assertThrows(IllegalArgumentException::class.java) { NormalizedRect(0.5f, 0f, 0.2f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { NormalizedRect(-0.1f, 0f, 0.5f, 1f) }
        assertThrows(IllegalArgumentException::class.java) { NormalizedRect(0f, 0f, 1.1f, 1f) }
    }

    @Test fun `all rotations round trip source points`() {
        for (rotation in FrameRotation.entries) {
            val swapsAxes = rotation == FrameRotation.ROTATION_90 || rotation == FrameRotation.ROTATION_270
            val transform = FrameTransform.create(
                sourceWidth = 4, sourceHeight = 2,
                rotation = rotation, crop = null,
                processedWidth = if (swapsAxes) 2 else 4,
                processedHeight = if (swapsAxes) 4 else 2,
            )
            val source = FloatPoint(1.25f, 0.75f)
            assertNear(source, transform.processedToSource(transform.sourceToProcessed(source)), 0.01f)
        }
    }

    @Test fun `cropped downscaled 90 degree transform round trips`() {
        val crop = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f)
        val transform = FrameTransform.create(
            sourceWidth = 1000, sourceHeight = 500,
            rotation = FrameRotation.ROTATION_90,
            crop = crop, processedWidth = 160, processedHeight = 240,
        )
        val point = FloatPoint(123f, 210f)
        assertNear(point, transform.processedToSource(transform.sourceToProcessed(point)), 1.0f)
    }

    @Test fun `portrait and landscape dimensions follow rotation`() {
        val landscape = FrameTransform.create(
            sourceWidth = 1000, sourceHeight = 500,
            rotation = FrameRotation.ROTATION_0, crop = null,
            processedWidth = 1000, processedHeight = 500,
        )
        assertEquals(1000, landscape.orientedWidth)
        assertEquals(500, landscape.orientedHeight)

        val portrait = FrameTransform.create(
            sourceWidth = 500, sourceHeight = 1000,
            rotation = FrameRotation.ROTATION_0, crop = null,
            processedWidth = 500, processedHeight = 1000,
        )
        assertEquals(500, portrait.orientedWidth)
        assertEquals(1000, portrait.orientedHeight)

        val quarterTurn = FrameTransform.create(
            sourceWidth = 1000, sourceHeight = 500,
            rotation = FrameRotation.ROTATION_90, crop = null,
            processedWidth = 500, processedHeight = 1000,
        )
        assertEquals(500, quarterTurn.orientedWidth)
        assertEquals(1000, quarterTurn.orientedHeight)

        val threeQuarterTurn = FrameTransform.create(
            sourceWidth = 1000, sourceHeight = 500,
            rotation = FrameRotation.ROTATION_270, crop = null,
            processedWidth = 500, processedHeight = 1000,
        )
        assertEquals(500, threeQuarterTurn.orientedWidth)
        assertEquals(1000, threeQuarterTurn.orientedHeight)

        val halfTurn = FrameTransform.create(
            sourceWidth = 1000, sourceHeight = 500,
            rotation = FrameRotation.ROTATION_180, crop = null,
            processedWidth = 1000, processedHeight = 500,
        )
        assertEquals(1000, halfTurn.orientedWidth)
        assertEquals(500, halfTurn.orientedHeight)
    }

    @Test fun `rotation maps source edges to oriented edges`() {
        val ninety = FrameTransform.create(1000, 500, FrameRotation.ROTATION_90, null, 500, 1000)
        assertNear(FloatPoint(500f, 0f), ninety.sourceToOriented(FloatPoint(0f, 0f)), 0.0001f)
        assertNear(FloatPoint(500f, 1000f), ninety.sourceToOriented(FloatPoint(1000f, 0f)), 0.0001f)
        assertNear(FloatPoint(0f, 1000f), ninety.sourceToOriented(FloatPoint(1000f, 500f)), 0.0001f)
        assertNear(FloatPoint(0f, 0f), ninety.sourceToOriented(FloatPoint(0f, 500f)), 0.0001f)

        val oneEighty = FrameTransform.create(1000, 500, FrameRotation.ROTATION_180, null, 1000, 500)
        assertNear(FloatPoint(1000f, 500f), oneEighty.sourceToOriented(FloatPoint(0f, 0f)), 0.0001f)
        assertNear(FloatPoint(0f, 0f), oneEighty.sourceToOriented(FloatPoint(1000f, 500f)), 0.0001f)

        val twoSeventy = FrameTransform.create(1000, 500, FrameRotation.ROTATION_270, null, 500, 1000)
        assertNear(FloatPoint(0f, 1000f), twoSeventy.sourceToOriented(FloatPoint(0f, 0f)), 0.0001f)
        assertNear(FloatPoint(500f, 0f), twoSeventy.sourceToOriented(FloatPoint(1000f, 500f)), 0.0001f)
    }

    @Test fun `full crop is an identity mapping`() {
        val transform = FrameTransform.create(
            sourceWidth = 640, sourceHeight = 480,
            rotation = FrameRotation.ROTATION_0,
            crop = NormalizedRect(0f, 0f, 1f, 1f),
            processedWidth = 640, processedHeight = 480,
        )
        assertEquals(PixelRect(0, 0, 640, 480), transform.cropPixelRect)
        assertEquals(1f, transform.scale, 0f)
        val point = FloatPoint(320.5f, 240.25f)
        assertNear(point, transform.sourceToProcessed(point), 0.0001f)
        assertNear(point, transform.processedToSource(point), 0.0001f)

        val nullCrop = FrameTransform.create(
            sourceWidth = 640, sourceHeight = 480,
            rotation = FrameRotation.ROTATION_0, crop = null,
            processedWidth = 640, processedHeight = 480,
        )
        assertEquals(NormalizedRect(0f, 0f, 1f, 1f), nullCrop.crop)
        assertEquals(PixelRect(0, 0, 640, 480), nullCrop.cropPixelRect)
    }

    @Test fun `rotation and crop are measured in oriented pixels`() {
        val crop = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f)
        val transform = FrameTransform.create(
            sourceWidth = 1000, sourceHeight = 500,
            rotation = FrameRotation.ROTATION_90,
            crop = crop, processedWidth = 160, processedHeight = 240,
        )
        assertEquals(500, transform.orientedWidth)
        assertEquals(1000, transform.orientedHeight)
        assertEquals(PixelRect(50, 200, 450, 800), transform.cropPixelRect)
        assertEquals(0.4f, transform.scale, 0.0001f)
        assertNear(FloatPoint(50f, 200f), transform.cropToOriented(FloatPoint(0f, 0f)), 0.0001f)
        assertNear(FloatPoint(0f, 0f), transform.orientedToCrop(FloatPoint(50f, 200f)), 0.0001f)
    }

    @Test fun `normalized rectangle maps through rotation and crop`() {
        val transform = FrameTransform.create(
            sourceWidth = 1000, sourceHeight = 500,
            rotation = FrameRotation.ROTATION_90, crop = null,
            processedWidth = 500, processedHeight = 1000,
        )
        val sourceRect = NormalizedRect(0.1f, 0.2f, 0.3f, 0.4f)
        val processedRect = transform.sourceNormalizedRectToProcessedPixelRect(sourceRect)
        assertEquals(PixelRect(300, 100, 400, 300), processedRect)
        assertEquals(
            sourceRect,
            transform.processedPixelRectToSourceNormalizedRect(processedRect),
        )
    }

    @Test fun `cropped rectangle maps to and from processed pixels`() {
        val crop = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f)
        val transform = FrameTransform.create(
            sourceWidth = 1000, sourceHeight = 500,
            rotation = FrameRotation.ROTATION_0,
            crop = crop, processedWidth = 800, processedHeight = 300,
        )
        val sourceRect = NormalizedRect(0.1f, 0.2f, 0.9f, 0.8f)
        assertEquals(
            PixelRect(0, 0, 800, 300),
            transform.sourceNormalizedRectToProcessedPixelRect(sourceRect),
        )
        assertEquals(
            sourceRect,
            transform.processedPixelRectToSourceNormalizedRect(PixelRect(0, 0, 800, 300)),
        )
    }

    @Test fun `invalid dimensions throw`() {
        assertThrows(IllegalArgumentException::class.java) {
            FrameTransform.create(0, 500, FrameRotation.ROTATION_0, null, 10, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameTransform.create(1000, 0, FrameRotation.ROTATION_0, null, 10, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameTransform.create(-1, 500, FrameRotation.ROTATION_0, null, 10, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameTransform.create(1000, 500, FrameRotation.ROTATION_0, null, 0, 10)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrameTransform.create(1000, 500, FrameRotation.ROTATION_0, null, 10, -1)
        }
    }

    @Test fun `crop outside image bounds throws`() {
        assertThrows(IllegalArgumentException::class.java) { NormalizedRect(0f, 0f, 1.5f, 1f) }
        assertThrows(IllegalArgumentException::class.java) {
            PixelRect(-1, 0, 10, 10).toNormalized(100, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelRect(0, 0, 101, 100).toNormalized(100, 100)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PixelRect(0, 0, 10, 10).toNormalized(0, 100)
        }
    }

    @Test fun `crop edges keep at least one pixel`() {
        val transform = FrameTransform.create(
            sourceWidth = 100, sourceHeight = 100,
            rotation = FrameRotation.ROTATION_0,
            crop = NormalizedRect(0.4f, 0.4f, 0.4001f, 0.4001f),
            processedWidth = 1, processedHeight = 1,
        )
        assertEquals(1, transform.cropPixelRect.width)
        assertEquals(1, transform.cropPixelRect.height)
    }

    @Test fun `aspect mismatch throws while one pixel rounding is accepted`() {
        val crop = NormalizedRect(0f, 0f, 1f, 1f)
        assertThrows(IllegalArgumentException::class.java) {
            FrameTransform.create(400, 600, FrameRotation.ROTATION_0, crop, 160, 200)
        }
        val rounded = FrameTransform.create(400, 600, FrameRotation.ROTATION_0, crop, 160, 241)
        assertEquals(160, rounded.processedWidth)
        assertEquals(241, rounded.processedHeight)
    }

    @Test fun `normalized and pixel points reject non-finite and out of range values`() {
        assertThrows(IllegalArgumentException::class.java) { NormalizedPoint(Float.NaN, 0f) }
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedPoint(0f, Float.NEGATIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) { NormalizedPoint(-0.001f, 0f) }
        assertThrows(IllegalArgumentException::class.java) { NormalizedPoint(0f, 1.001f) }
        assertEquals(NormalizedPoint(0f, 0f), NormalizedPoint(0f, 0f))
        assertEquals(NormalizedPoint(1f, 1f), NormalizedPoint(1f, 1f))

        assertThrows(IllegalArgumentException::class.java) { FloatPoint(Float.NaN, 0f) }
        assertThrows(IllegalArgumentException::class.java) {
            FloatPoint(0f, Float.POSITIVE_INFINITY)
        }

        val transform = FrameTransform.create(
            sourceWidth = 10, sourceHeight = 10,
            rotation = FrameRotation.ROTATION_0, crop = null,
            processedWidth = 10, processedHeight = 10,
        )
        assertThrows(IllegalArgumentException::class.java) {
            transform.sourcePixelToNormalized(PixelPoint(-1, 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            transform.sourcePixelToNormalized(PixelPoint(11, 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            transform.sourcePixelToNormalized(PixelPoint(0, Int.MAX_VALUE))
        }
        assertEquals(NormalizedPoint(0f, 0f), transform.sourcePixelToNormalized(PixelPoint(0, 0)))
        assertEquals(NormalizedPoint(1f, 1f), transform.sourcePixelToNormalized(PixelPoint(10, 10)))
        assertEquals(PixelPoint(5, 5), transform.normalizedToSourcePixel(NormalizedPoint(0.5f, 0.5f)))
    }

    @Test fun `normalized pixel helper rejects zero dimensions`() {
        assertEquals(PixelRect(0, 0, 10, 20), NormalizedRect(0f, 0f, 1f, 1f).toPixelRect(10, 20))
        assertThrows(IllegalArgumentException::class.java) {
            NormalizedRect(0f, 0f, 1f, 1f).toPixelRect(0, 20)
        }
    }

    private fun assertNear(expected: FloatPoint, actual: FloatPoint, tolerance: Float) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
    }
}
