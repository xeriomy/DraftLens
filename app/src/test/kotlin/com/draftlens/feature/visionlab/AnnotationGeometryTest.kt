package com.draftlens.feature.visionlab

import androidx.compose.ui.unit.dp
import com.draftlens.vision.geometry.NormalizedPoint
import com.draftlens.vision.geometry.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnotationGeometryTest {

    @Test fun `a created rectangle spans the two drag points`() {
        val rect = AnnotationGeometry.rectBetween(
            NormalizedPoint(0.2f, 0.3f),
            NormalizedPoint(0.6f, 0.7f),
        )
        assertEquals(NormalizedRect(0.2f, 0.3f, 0.6f, 0.7f), rect)
    }

    @Test fun `a created rectangle is ordered regardless of drag direction`() {
        val rect = AnnotationGeometry.rectBetween(
            NormalizedPoint(0.6f, 0.7f),
            NormalizedPoint(0.2f, 0.3f),
        )
        assertEquals(NormalizedRect(0.2f, 0.3f, 0.6f, 0.7f), rect)
    }

    @Test fun `a drag smaller than the minimum size creates nothing`() {
        assertNull(
            AnnotationGeometry.rectBetween(
                NormalizedPoint(0.2f, 0.3f),
                NormalizedPoint(0.204f, 0.304f),
            ),
        )
    }

    @Test fun `dragging the bottom right corner resizes the rectangle`() {
        val start = NormalizedRect(0.2f, 0.3f, 0.5f, 0.6f)
        val resized = AnnotationGeometry.resizedRect(start, Corner.BOTTOM_RIGHT, NormalizedPoint(0.7f, 0.8f))
        assertEquals(NormalizedRect(0.2f, 0.3f, 0.7f, 0.8f), resized)
    }

    @Test fun `dragging the top left corner moves the opposite edges only`() {
        val start = NormalizedRect(0.2f, 0.3f, 0.5f, 0.6f)
        val resized = AnnotationGeometry.resizedRect(start, Corner.TOP_LEFT, NormalizedPoint(0.1f, 0.15f))
        assertEquals(NormalizedRect(0.1f, 0.15f, 0.5f, 0.6f), resized)
    }

    @Test fun `a resize that would collapse the rectangle is refused`() {
        val start = NormalizedRect(0.2f, 0.3f, 0.5f, 0.6f)
        assertNull(AnnotationGeometry.resizedRect(start, Corner.BOTTOM_RIGHT, NormalizedPoint(0.205f, 0.65f)))
    }
}

class AnnotationWorkspaceTest {

    @Test fun `a portrait frame gets a tall workspace`() {
        val height = AnnotationWorkspace.heightFor(360.dp, 1080f / 1920f, 240.dp)
        assertEquals(640f, height.value, 0.5f)
    }

    @Test fun `a landscape frame keeps the minimum workspace height`() {
        val height = AnnotationWorkspace.heightFor(360.dp, 1920f / 1080f, 240.dp)
        assertEquals(240f, height.value, 0.5f)
    }

    @Test fun `a tall landscape frame still uses the full width`() {
        val height = AnnotationWorkspace.heightFor(360.dp, 2400f / 1080f, 240.dp)
        assertEquals(240f, height.value, 0.5f)
    }

    @Test fun `an unknown ratio falls back to the minimum`() {
        val height = AnnotationWorkspace.heightFor(360.dp, 0f, 240.dp)
        assertEquals(240f, height.value, 0.5f)
    }
}
