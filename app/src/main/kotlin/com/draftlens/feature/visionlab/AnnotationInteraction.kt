package com.draftlens.feature.visionlab

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.draftlens.vision.annotation.DraftSlot

/**
 * What a pointer landed on when it went down.
 */
sealed interface AnnotationTarget {

    /** A resize handle of the selected slot. */
    data class Handle(
        val slotId: String,
        val corner: Corner,
        val rect: com.draftlens.vision.geometry.NormalizedRect,
    ) : AnnotationTarget

    /** The delete control of the selected slot. */
    data class Delete(val slotId: String) : AnnotationTarget

    /** An existing slot rectangle. */
    data class Slot(
        val slotId: String,
        val rect: com.draftlens.vision.geometry.NormalizedRect,
    ) : AnnotationTarget

    /** Empty space inside the displayed image. */
    data object Empty : AnnotationTarget

    /** Outside the displayed image; nothing here belongs to the annotation canvas. */
    data object Outside : AnnotationTarget
}

/**
 * A discrete decision the interaction model produced.
 */
sealed interface AnnotationAction {

    /** The slot with this id becomes the selected slot. */
    data class Select(val slotId: String) : AnnotationAction

    /** The contextual property editor of this slot opens. */
    data class OpenMenu(val slotId: String) : AnnotationAction

    /** The slot with this id is deleted. */
    data class Delete(val slotId: String) : AnnotationAction

    /** A new slot is created with this rectangle. */
    data class Draw(val rect: com.draftlens.vision.geometry.NormalizedRect) : AnnotationAction

    /** Nothing happens. */
    data object None : AnnotationAction
}

/**
 * The annotation interaction model.
 *
 * The rules of the canvas live here as an explicit state machine, so they can be tested
 * without Compose and so the gestures cannot compete unpredictably:
 *
 * | interaction | action |
 * |---|---|
 * | short tap on a slot | select that slot |
 * | drag inside a selected slot | move the selected slot |
 * | long press on a slot | open its contextual menu |
 * | drag a resize handle | resize the selected slot |
 * | long press on empty image, then drag | draw a new slot |
 * | short tap on empty image | nothing |
 * | tap the delete control | delete the selected slot |
 *
 * The Compose layer feeds this model pointer facts and renders the result; it never
 * decides on its own what a gesture means.
 */
class AnnotationInteraction(
    val longPressTimeoutMs: Long = DEFAULT_LONG_PRESS_TIMEOUT_MS,
    val touchSlopPx: Float = DEFAULT_TOUCH_SLOP_PX,
) {

    enum class Phase {
        Idle,
        Pressed,
        Moving,
        Resizing,
        Drawing,
        Menu,
        DeletePending,
    }

    data class State(
        val phase: Phase = Phase.Idle,
        val slotId: String? = null,
        val corner: Corner? = null,
        /** The rectangle of the slot when the gesture started. */
        val startRect: com.draftlens.vision.geometry.NormalizedRect? = null,
        /** True when the pressed slot was already the selected one. */
        val pressedSlotWasSelected: Boolean = false,
        /** True once the long-press threshold elapsed. */
        val longPressFired: Boolean = false,
    )

    data class Result(val state: State, val action: AnnotationAction = AnnotationAction.None)

    /** The pointer went down on [target]. */
    fun down(target: AnnotationTarget, selectedSlotId: String?): Result = when (target) {
        is AnnotationTarget.Handle -> Result(
            State(
                phase = Phase.Resizing,
                slotId = target.slotId,
                corner = target.corner,
                startRect = target.rect,
            ),
        )

        is AnnotationTarget.Delete -> Result(
            State(phase = Phase.DeletePending, slotId = target.slotId),
        )

        is AnnotationTarget.Slot -> Result(
            State(
                phase = Phase.Pressed,
                slotId = target.slotId,
                startRect = target.rect,
                pressedSlotWasSelected = target.slotId == selectedSlotId,
            ),
        )

        AnnotationTarget.Empty -> Result(State(phase = Phase.Pressed))
        AnnotationTarget.Outside -> Result(State(phase = Phase.Idle))
    }

    /** The pointer moved [distancePx] away from where it went down. */
    fun drag(state: State, distancePx: Float): Result {
        if (distancePx < touchSlopPx) return Result(state)
        return when (state.phase) {
            Phase.Pressed -> when {
                state.slotId != null && state.pressedSlotWasSelected ->
                    Result(state.copy(phase = Phase.Moving))
                state.slotId != null ->
                    // An unselected slot is selected, but never moved: a drag must not move
                    // the wrong slot.
                    Result(state, AnnotationAction.Select(state.slotId))
                state.longPressFired -> Result(state.copy(phase = Phase.Drawing))
                else -> Result(state)
            }
            else -> Result(state)
        }
    }

    /** The long-press threshold elapsed without the pointer moving past the touch slop. */
    fun longPress(state: State): Result = when (state.phase) {
        Phase.Pressed -> if (state.slotId != null) {
            Result(
                state.copy(phase = Phase.Menu, longPressFired = true),
                AnnotationAction.OpenMenu(state.slotId),
            )
        } else {
            Result(state.copy(phase = Phase.Drawing, longPressFired = true))
        }
        else -> Result(state)
    }

    /**
     * The pointer went up. [drawnRect] is the rectangle drawn during a create gesture, if
     * any; it is only used when the gesture actually drew one.
     */
    fun up(state: State, drawnRect: com.draftlens.vision.geometry.NormalizedRect? = null): Result =
        when (state.phase) {
            Phase.Pressed -> if (state.slotId != null) {
                Result(State(phase = Phase.Idle), AnnotationAction.Select(state.slotId))
            } else {
                // A tap on empty image space does nothing.
                Result(State(phase = Phase.Idle))
            }
            Phase.Drawing -> Result(
                State(phase = Phase.Idle),
                drawnRect?.let { AnnotationAction.Draw(it) } ?: AnnotationAction.None,
            )
            Phase.DeletePending -> Result(
                State(phase = Phase.Idle),
                state.slotId?.let { AnnotationAction.Delete(it) } ?: AnnotationAction.None,
            )
            Phase.Menu, Phase.Moving, Phase.Resizing, Phase.Idle -> Result(State(phase = Phase.Idle))
        }

    companion object {
        const val DEFAULT_LONG_PRESS_TIMEOUT_MS = 450L
        const val DEFAULT_TOUCH_SLOP_PX = 24f
    }
}

/**
 * Works out what a pointer position is over.
 *
 * The delete control and the resize handles belong to the selected slot and are checked
 * first, so a touch on them never reaches another slot.
 */
object AnnotationTargetResolver {

    fun resolve(
        geometry: CanvasGeometry,
        fitted: Rect,
        offset: Offset,
        slots: List<DraftSlot>,
        selectedSlotId: String?,
        handleRadiusPx: Float,
    ): AnnotationTarget {
        val selected = slots.firstOrNull { it.id == selectedSlotId }
        if (selected != null) {
            val delete = geometry.deleteControlCenter(selected.rect, fitted)
            if ((offset - delete).getDistance() <= DELETE_CONTROL_RADIUS_PX * 1.5f) {
                return AnnotationTarget.Delete(selected.id)
            }
            geometry.handleAt(selected.rect, offset, fitted, handleRadiusPx)?.let { corner ->
                return AnnotationTarget.Handle(selected.id, corner, selected.rect)
            }
        }
        val point = geometry.canvasToNormalized(offset, fitted) ?: return AnnotationTarget.Outside
        AnnotationHitTest.slotAt(point, slots)?.let { return AnnotationTarget.Slot(it.id, it.rect) }
        return AnnotationTarget.Empty
    }

    /** Visual radius of the delete control, in canvas pixels. */
    const val DELETE_CONTROL_RADIUS_PX = 14f
}
