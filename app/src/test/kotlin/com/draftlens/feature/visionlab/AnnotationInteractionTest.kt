package com.draftlens.feature.visionlab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.NormalizedRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationInteractionTest {

    private val interaction = AnnotationInteraction(longPressTimeoutMs = 400L, touchSlopPx = 10f)
    private val rect = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f)

    private fun slot(id: String, left: Float = 0.1f, top: Float = 0.1f) = DraftSlot(
        id = id,
        type = SlotType.BAN,
        team = SlotTeam.ALLY,
        index = 1,
        rect = NormalizedRect(left, top, left + 0.2f, top + 0.2f),
    )

    @Test fun `a short tap on a slot selects it`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = null)
        assertEquals(AnnotationInteraction.Phase.Pressed, down.state.phase)

        val up = interaction.up(down.state)
        assertEquals(AnnotationAction.Select("a"), up.action)
    }

    @Test fun `a tap on empty image space does nothing`() {
        val down = interaction.down(AnnotationTarget.Empty, selectedSlotId = null)
        val up = interaction.up(down.state)
        assertEquals(AnnotationAction.None, up.action)
    }

    @Test fun `a drag on the selected slot moves it`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        val drag = interaction.drag(down.state, distancePx = 40f)

        assertEquals(AnnotationInteraction.Phase.Moving, drag.state.phase)
        assertEquals(AnnotationAction.None, drag.action)
    }

    @Test fun `a drag on an unselected slot selects it but never moves it`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "b")
        val drag = interaction.drag(down.state, distancePx = 40f)

        assertEquals(AnnotationInteraction.Phase.Pressed, drag.state.phase)
        assertEquals(AnnotationAction.Select("a"), drag.action)
    }

    @Test fun `a movement below the touch slop never starts a move`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        val drag = interaction.drag(down.state, distancePx = 2f)

        assertEquals(AnnotationInteraction.Phase.Pressed, drag.state.phase)
        assertEquals(AnnotationAction.None, drag.action)
    }

    @Test fun `a long press on a slot opens its contextual menu`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        val longPress = interaction.longPress(down.state)

        assertEquals(AnnotationInteraction.Phase.Menu, longPress.state.phase)
        assertEquals(AnnotationAction.OpenMenu("a"), longPress.action)
    }

    @Test fun `a long press opens the menu without any movement`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        // No drag, no movement: the timeout alone opens the menu.
        val longPress = interaction.longPress(down.state)

        assertEquals(AnnotationAction.OpenMenu("a"), longPress.action)
        assertEquals(AnnotationInteraction.Phase.Menu, longPress.state.phase)
    }

    @Test fun `a slight movement after the long press does not delay or repeat the menu`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        val longPress = interaction.longPress(down.state)
        assertEquals(AnnotationAction.OpenMenu("a"), longPress.action)

        val afterMovement = interaction.drag(longPress.state, distancePx = 3f)

        assertEquals(AnnotationInteraction.Phase.Menu, afterMovement.state.phase)
        assertEquals(AnnotationAction.None, afterMovement.action)
    }

    @Test fun `a normal tap never opens the menu`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        val up = interaction.up(down.state)

        assertEquals(AnnotationAction.Select("a"), up.action)
        assertFalse(up.action is AnnotationAction.OpenMenu)
    }

    @Test fun `moving a selected slot never opens the menu`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        val drag = interaction.drag(down.state, distancePx = 40f)

        assertEquals(AnnotationInteraction.Phase.Moving, drag.state.phase)
        assertFalse(drag.action is AnnotationAction.OpenMenu)
    }

    @Test fun `a stationary long press on a slot is not a move`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        val longPress = interaction.longPress(down.state)
        val up = interaction.up(longPress.state)

        assertEquals(AnnotationAction.None, up.action)
    }

    @Test fun `a long press on empty image space starts drawing`() {
        val down = interaction.down(AnnotationTarget.Empty, selectedSlotId = null)
        val longPress = interaction.longPress(down.state)

        assertEquals(AnnotationInteraction.Phase.Drawing, longPress.state.phase)
        assertEquals(AnnotationAction.None, longPress.action)
    }

    @Test fun `drawing produces a rectangle when the pointer is released`() {
        val down = interaction.down(AnnotationTarget.Empty, selectedSlotId = null)
        val longPress = interaction.longPress(down.state)
        val up = interaction.up(longPress.state, NormalizedRect(0.2f, 0.3f, 0.5f, 0.6f))

        assertEquals(AnnotationAction.Draw(NormalizedRect(0.2f, 0.3f, 0.5f, 0.6f)), up.action)
    }

    @Test fun `drawing without a rectangle produces nothing`() {
        val down = interaction.down(AnnotationTarget.Empty, selectedSlotId = null)
        val longPress = interaction.longPress(down.state)
        val up = interaction.up(longPress.state, null)

        assertEquals(AnnotationAction.None, up.action)
    }

    @Test fun `a tap on the delete control deletes that slot`() {
        val down = interaction.down(AnnotationTarget.Delete("a"), selectedSlotId = "a")
        val up = interaction.up(down.state)

        assertEquals(AnnotationAction.Delete("a"), up.action)
    }

    @Test fun `a drag on a handle resizes and never moves`() {
        val down = interaction.down(
            AnnotationTarget.Handle("a", Corner.BOTTOM_RIGHT, rect),
            selectedSlotId = "a",
        )
        val drag = interaction.drag(down.state, distancePx = 40f)

        assertEquals(AnnotationInteraction.Phase.Resizing, drag.state.phase)
        assertEquals(AnnotationAction.None, drag.action)
    }

    @Test fun `a press outside the image does nothing`() {
        val down = interaction.down(AnnotationTarget.Outside, selectedSlotId = null)
        assertEquals(AnnotationInteraction.Phase.Idle, down.state.phase)
        assertEquals(AnnotationAction.None, interaction.up(down.state).action)
    }

    @Test fun `the resize gesture keeps the rectangle the gesture started with`() {
        val down = interaction.down(
            AnnotationTarget.Handle("a", Corner.BOTTOM_RIGHT, rect),
            selectedSlotId = "a",
        )
        assertEquals(rect, down.state.startRect)
    }

    // region the long-press deadline

    @Test fun `moving an unselected slot before the timeout selects it and never opens the menu`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "b")
        val moved = interaction.drag(down.state, distancePx = 40f)

        assertEquals(AnnotationAction.Select("a"), moved.action)
        assertTrue("the pending long press must be cancelled", moved.state.longPressCancelled)
        assertFalse(AnnotationInteraction.shouldAwaitLongPress(moved.state, timeoutMs = 400L, elapsedMs = 100L))
        // Even after the timeout would have elapsed, the menu never opens.
        val longPress = interaction.longPress(moved.state)
        assertEquals(AnnotationAction.None, longPress.action)
        assertEquals(AnnotationInteraction.Phase.Pressed, longPress.state.phase)
    }

    @Test fun `moving a selected slot before the timeout starts moving it and never opens the menu`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        val moved = interaction.drag(down.state, distancePx = 40f)

        assertEquals(AnnotationInteraction.Phase.Moving, moved.state.phase)
        assertTrue(moved.state.longPressCancelled)
        assertFalse(AnnotationInteraction.shouldAwaitLongPress(moved.state, 400L, 100L))
        assertEquals(AnnotationAction.None, interaction.longPress(moved.state).action)
    }

    @Test fun `moving on empty space before the timeout never turns into drawing or creating`() {
        val down = interaction.down(AnnotationTarget.Empty, selectedSlotId = null)
        val moved = interaction.drag(down.state, distancePx = 40f)

        assertTrue(moved.state.longPressCancelled)
        assertFalse(AnnotationInteraction.shouldAwaitLongPress(moved.state, 400L, 100L))
        val afterTimeout = interaction.longPress(moved.state)
        assertEquals(AnnotationAction.None, afterTimeout.action)
        assertEquals(AnnotationInteraction.Phase.Pressed, afterTimeout.state.phase)
        // Releasing here creates nothing, even with a drawn rectangle offered.
        assertEquals(
            AnnotationAction.None,
            interaction.up(
                moved.state,
                NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f),
            ).action,
        )
    }

    @Test fun `the long press timeout stays armed while the pointer is within the slop`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")

        assertTrue(AnnotationInteraction.shouldAwaitLongPress(down.state, 400L, 0L))
        assertTrue(AnnotationInteraction.shouldAwaitLongPress(down.state, 400L, 399L))
        assertFalse(AnnotationInteraction.shouldAwaitLongPress(down.state, 400L, 400L))
    }

    @Test fun `movement below the slop before the timeout leaves the long press armed`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        val moved = interaction.drag(down.state, distancePx = 3f)

        assertFalse(moved.state.longPressCancelled)
        assertTrue(AnnotationInteraction.shouldAwaitLongPress(moved.state, 400L, 399L))
    }

    @Test fun `after the timeout fires the deadline is gone and movement cannot delay or cancel the menu`() {
        val down = interaction.down(AnnotationTarget.Slot("a", rect), selectedSlotId = "a")
        val fired = interaction.longPress(down.state).state

        assertFalse(AnnotationInteraction.shouldAwaitLongPress(fired, 400L, 401L))
        val nudged = interaction.drag(fired, distancePx = 3f)
        assertEquals(AnnotationInteraction.Phase.Menu, nudged.state.phase)
        assertEquals(AnnotationAction.None, nudged.action)
        val dragged = interaction.drag(fired, distancePx = 80f)
        assertEquals(AnnotationInteraction.Phase.Menu, dragged.state.phase)
        assertEquals(AnnotationAction.None, dragged.action)
    }

    @Test fun `after the timeout on empty space a drag draws a rectangle but the deadline is gone`() {
        val down = interaction.down(AnnotationTarget.Empty, selectedSlotId = null)
        val drawing = interaction.longPress(down.state).state

        assertFalse(AnnotationInteraction.shouldAwaitLongPress(drawing, 400L, 401L))
        assertEquals(AnnotationInteraction.Phase.Drawing, interaction.drag(drawing, distancePx = 50f).state.phase)
    }

    @Test fun `creating a slot requires holding first reaching the threshold then dragging`() {
        // A drag past the slop before the timeout can never create a rectangle.
        val down = interaction.down(AnnotationTarget.Empty, selectedSlotId = null)
        val moved = interaction.drag(down.state, distancePx = 40f).state
        val rectangle = NormalizedRect(0.1f, 0.1f, 0.3f, 0.3f)

        assertEquals(AnnotationAction.None, interaction.longPress(moved).action)
        assertEquals(AnnotationAction.None, interaction.up(moved, rectangle).action)

        // Holding still, reaching the threshold, then dragging draws the rectangle.
        val held = interaction.down(AnnotationTarget.Empty, selectedSlotId = null)
        val armed = interaction.longPress(held.state)
        assertEquals(AnnotationInteraction.Phase.Drawing, armed.state.phase)
        assertEquals(AnnotationAction.Draw(rectangle), interaction.up(armed.state, rectangle).action)
    }

    // endregion
}

class AnnotationTargetResolverTest {

    private val geometry = CanvasGeometry.of(
        com.draftlens.vision.model.OriginalFrame(
            id = com.draftlens.vision.model.FrameId("target-test"),
            payload = com.draftlens.vision.model.PixelArrayFramePayload(1000, 500, IntArray(500_000)),
            sourceWidth = 1000,
            sourceHeight = 500,
            displayRotation = com.draftlens.vision.model.FrameRotation.ROTATION_0,
            rotation = com.draftlens.vision.model.FrameRotation.ROTATION_0,
            capturedAtElapsedRealtimeMs = 1,
            wallClockTimestampMs = 1,
            captureLatencyMs = 1,
        ),
    )

    private val fitted = fittedImageRect(Size(1000f, 500f), 1000, 500)

    private fun slot(id: String, left: Float = 0.1f, top: Float = 0.1f) = DraftSlot(
        id = id,
        type = SlotType.BAN,
        team = SlotTeam.ALLY,
        index = 1,
        rect = NormalizedRect(left, top, left + 0.2f, top + 0.2f),
    )

    @Test fun `a point on a slot resolves to that slot`() {
        val target = AnnotationTargetResolver.resolve(
            geometry = geometry,
            fitted = fitted,
            offset = Offset(200f, 150f),
            slots = listOf(slot("a")),
            selectedSlotId = null,
            handleRadiusPx = 12f,
        )
        assertEquals(AnnotationTarget.Slot("a", slot("a").rect), target)
    }

    @Test fun `a point on empty image space resolves to empty`() {
        val target = AnnotationTargetResolver.resolve(
            geometry = geometry,
            fitted = fitted,
            offset = Offset(800f, 400f),
            slots = listOf(slot("a")),
            selectedSlotId = null,
            handleRadiusPx = 12f,
        )
        assertEquals(AnnotationTarget.Empty, target)
    }

    @Test fun `a point outside the image resolves to outside`() {
        val target = AnnotationTargetResolver.resolve(
            geometry = geometry,
            fitted = fitted,
            offset = Offset(2000f, 100f),
            slots = listOf(slot("a")),
            selectedSlotId = null,
            handleRadiusPx = 12f,
        )
        assertEquals(AnnotationTarget.Outside, target)
    }

    @Test fun `the delete control of the selected slot wins over the slot itself`() {
        val selected = slot("a")
        val delete = geometry.deleteControlCenter(selected.rect, fitted)
        val target = AnnotationTargetResolver.resolve(
            geometry = geometry,
            fitted = fitted,
            offset = delete,
            slots = listOf(selected, slot("b", left = 0.2f)),
            selectedSlotId = "a",
            handleRadiusPx = 12f,
        )
        assertEquals(AnnotationTarget.Delete("a"), target)
    }

    @Test fun `the delete control sits exactly where the top right handle was`() {
        val selected = slot("a")
        val bounds = geometry.normalizedToCanvas(selected.rect, fitted)

        assertEquals(bounds.topRight, geometry.deleteControlCenter(selected.rect, fitted))
    }

    @Test fun `the top right corner has no resize handle`() {
        val selected = slot("a")
        val bounds = geometry.normalizedToCanvas(selected.rect, fitted)

        val handle = geometry.handleAt(selected.rect, bounds.topRight, fitted, radius = 12f)

        assertNull(handle)
    }

    @Test fun `the remaining resize handles are still there`() {
        val selected = slot("a")
        val bounds = geometry.normalizedToCanvas(selected.rect, fitted)

        assertEquals(
            Corner.TOP_LEFT,
            geometry.handleAt(selected.rect, bounds.topLeft, fitted, radius = 12f),
        )
        assertEquals(
            Corner.BOTTOM_LEFT,
            geometry.handleAt(selected.rect, Offset(bounds.left, bounds.bottom), fitted, radius = 12f),
        )
        assertEquals(
            Corner.BOTTOM_RIGHT,
            geometry.handleAt(selected.rect, bounds.bottomRight, fitted, radius = 12f),
        )
    }

    @Test fun `a resize handle of the selected slot resolves to that handle`() {
        val selected = slot("a")
        val bounds = geometry.normalizedToCanvas(selected.rect, fitted)
        val target = AnnotationTargetResolver.resolve(
            geometry = geometry,
            fitted = fitted,
            offset = bounds.bottomRight,
            slots = listOf(selected),
            selectedSlotId = "a",
            handleRadiusPx = 12f,
        )
        assertEquals(AnnotationTarget.Handle("a", Corner.BOTTOM_RIGHT, selected.rect), target)
    }
}
