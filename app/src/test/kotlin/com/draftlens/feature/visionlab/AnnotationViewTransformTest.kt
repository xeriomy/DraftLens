package com.draftlens.feature.visionlab

import androidx.compose.ui.geometry.Offset
import com.draftlens.vision.geometry.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationViewTransformTest {

    @Test fun `the inverse undoes the forward mapping`() {
        val transform = AnnotationViewTransform(scale = 2.5f, offset = Offset(30f, -12f))
        val pivot = Offset(400f, 300f)
        val point = Offset(220f, 180f)

        val screen = transform.forward(point, pivot)
        val back = transform.inverse(screen, pivot)

        assertEquals(point.x, back.x, 0.001f)
        assertEquals(point.y, back.y, 0.001f)
    }

    @Test fun `the identity transform changes nothing`() {
        val transform = AnnotationViewTransform()
        val pivot = Offset(400f, 300f)
        val point = Offset(220f, 180f)

        assertTrue(transform.isIdentity)
        assertEquals(point.x, transform.forward(point, pivot).x, 0.001f)
        assertEquals(point.x, transform.inverse(point, pivot).x, 0.001f)
    }

    @Test fun `the scale is clamped to the usable range`() {
        val clamped = AnnotationViewTransform(scale = 99f).clamped(
            AnnotationViewTransform.MIN_SCALE,
            AnnotationViewTransform.MAX_SCALE,
        )
        assertEquals(AnnotationViewTransform.MAX_SCALE, clamped.scale, 0.001f)

        val floored = AnnotationViewTransform(scale = 0.01f).clamped(
            AnnotationViewTransform.MIN_SCALE,
            AnnotationViewTransform.MAX_SCALE,
        )
        assertEquals(AnnotationViewTransform.MIN_SCALE, floored.scale, 0.001f)
    }
}

class SlotPlacementTest {

    @Test fun `an offset slot stays inside the image`() {
        val rect = NormalizedRect(0.9f, 0.9f, 0.98f, 0.98f)
        val placed = SlotPlacement.offsetRect(rect)

        assertTrue(placed.right <= 1f)
        assertTrue(placed.bottom <= 1f)
        assertEquals(rect.width, placed.width, 0.001f)
        assertEquals(rect.height, placed.height, 0.001f)
    }

    @Test fun `an offset slot is moved away from its source`() {
        val rect = NormalizedRect(0.2f, 0.2f, 0.4f, 0.4f)
        val placed = SlotPlacement.offsetRect(rect)

        assertTrue(placed.left > rect.left)
        assertTrue(placed.top > rect.top)
    }
}
