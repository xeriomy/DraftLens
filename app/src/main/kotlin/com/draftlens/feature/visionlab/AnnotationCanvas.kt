package com.draftlens.feature.visionlab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.android.BitmapFramePayload
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max
import kotlin.math.min
import kotlin.time.TimeSource

/**
 * The actions the contextual property editor can perform on a slot.
 */
data class SlotMenuActions(
    val onTypeChange: (String, SlotType) -> Unit,
    val onTeamChange: (String, SlotTeam) -> Unit,
    val onIndexChange: (String, Int) -> Unit,
    val onLabelChange: (String, String?) -> Unit,
    val onCopy: (String) -> Unit,
    val onPaste: (String) -> Unit,
    val onDuplicate: (String) -> Unit,
    val onClose: () -> Unit,
    /** True when the application's own clipboard holds a slot. */
    val hasClipboard: Boolean = false,
)

/**
 * Interactive canvas over the oriented frame.
 *
 * The canvas draws the oriented, uncropped frame. Slot rectangles are stored in
 * normalized *source* coordinates, so every pointer position is mapped through the
 * frame transform instead of being guessed from screen dp.
 *
 * The workspace is sized from the oriented aspect ratio, so a portrait capture gets a
 * tall workspace and a landscape capture keeps a usable minimum height. The image is
 * never cropped and never stretched.
 *
 * Gestures are decided by [AnnotationInteraction]; this composable only feeds it pointer
 * facts and renders the result, so two gestures can never compete.
 */
@Composable
fun AnnotationCanvas(
    frame: OriginalFrame?,
    slots: List<DraftSlot>,
    selectedSlotId: String?,
    menuSlotId: String?,
    onSelect: (String?) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, NormalizedRect) -> Unit,
    onCreate: (NormalizedRect) -> Unit,
    onDelete: (String) -> Unit,
    onOpenMenu: (String) -> Unit,
    onGestureStart: (String) -> Unit = {},
    onGestureEnd: () -> Unit = {},
    menuActions: SlotMenuActions? = null,
    workspaceMode: AnnotationWorkspaceMode = AnnotationWorkspaceMode.Aspect,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var pending by remember { mutableStateOf<NormalizedRect?>(null) }

    val currentSlots by rememberUpdatedState(slots)
    val currentSelected by rememberUpdatedState(selectedSlotId)
    val currentMenu by rememberUpdatedState(menuSlotId)
    // Keyed on the frame's shape, not its identity: a new frame of the same size and
    // rotation must not restart the gesture detectors.
    val geometry = remember(frame?.sourceWidth, frame?.sourceHeight, frame?.rotation) {
        frame?.let { CanvasGeometry.of(it) }
    }
    val fitted = remember(canvasSize, geometry) {
        fittedImageRect(
            available = Size(
                canvasSize.width.toFloat(),
                canvasSize.height.toFloat(),
            ),
            width = geometry?.orientedWidth ?: 0,
            height = geometry?.orientedHeight ?: 0,
        )
    }
    val handleRadius = with(LocalDensity.current) { HANDLE_RADIUS.toPx() }
    val textMeasurer = rememberTextMeasurer()
    val interaction = remember { AnnotationInteraction() }

    BoxWithConstraints(modifier) {
        val ratio = geometry?.let { it.orientedWidth.toFloat() / it.orientedHeight.toFloat() } ?: 1f
        val workspaceHeight = when (workspaceMode) {
            AnnotationWorkspaceMode.Aspect -> AnnotationWorkspace.heightFor(maxWidth, ratio)
            AnnotationWorkspaceMode.Fill ->
                if (maxHeight != Dp.Infinity) maxHeight else AnnotationWorkspace.heightFor(maxWidth, ratio)
        }

        Box(modifier = Modifier.fillMaxWidth().height(workspaceHeight)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(workspaceHeight)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(geometry, fitted, handleRadius) {
                        annotationPointerInput(
                            interaction = interaction,
                            geometry = { geometry },
                            fitted = { fitted },
                            slots = { currentSlots },
                            selected = { currentSelected },
                            handleRadius = handleRadius,
                            onAction = { action ->
                                when (action) {
                                    is AnnotationAction.Select -> onSelect(action.slotId)
                                    is AnnotationAction.OpenMenu -> onOpenMenu(action.slotId)
                                    is AnnotationAction.Delete -> onDelete(action.slotId)
                                    is AnnotationAction.Draw -> onCreate(action.rect)
                                    AnnotationAction.None -> Unit
                                }
                            },
                            onMove = onMove,
                            onResize = onResize,
                            onGestureStart = onGestureStart,
                            onGestureEnd = onGestureEnd,
                            onDrawing = { pending = it },
                        )
                    },
            ) {
                val geo = geometry ?: return@Canvas
                if (fitted.width <= 0f || fitted.height <= 0f) return@Canvas

                fun drawContent() {
                    val payload = frame?.payload as? BitmapFramePayload
                    if (payload != null) {
                        drawOrientedBitmap(
                            bitmap = payload.bitmap.asImageBitmap(),
                            sourceWidth = geo.sourceWidth,
                            sourceHeight = geo.sourceHeight,
                            rotation = geo.rotation,
                            fitted = fitted,
                        )
                    }

                    slots.forEach { slot ->
                        val bounds = geo.normalizedToCanvas(slot.rect, fitted)
                        val isSelected = slot.id == selectedSlotId
                        if (isSelected) {
                            drawRect(
                                color = SlotVisuals.SELECTED.copy(alpha = 0.25f),
                                topLeft = bounds.topLeft,
                                size = bounds.size,
                            )
                        }
                        drawRect(
                            color = if (isSelected) SlotVisuals.SELECTED else SlotVisuals.teamColor(slot.team),
                            topLeft = bounds.topLeft,
                            size = bounds.size,
                            style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.dp.toPx()),
                        )
                        val measured = textMeasurer.measure(
                            SlotVisuals.label(slot),
                            TextStyle(color = Color.White, fontSize = 11.sp),
                        )
                        drawText(
                            measured,
                            topLeft = Offset(bounds.left, max(bounds.top - measured.size.height, 0f)),
                        )
                    }

                    slots.firstOrNull { it.id == selectedSlotId }?.let { slot ->
                        val bounds = geo.normalizedToCanvas(slot.rect, fitted)
                        // The top-right corner carries the delete control, so it has no handle.
                        listOf(
                            bounds.topLeft,
                            Offset(bounds.left, bounds.bottom),
                            bounds.bottomRight,
                        ).forEach { corner ->
                            drawCircle(color = SlotVisuals.SELECTED, radius = handleRadius, center = corner)
                        }
                        drawDeleteControl(geo, slot, fitted)
                    }

                    pending?.let { rect ->
                        val bounds = geo.normalizedToCanvas(rect.left, rect.top, rect.right, rect.bottom, fitted)
                        drawRect(
                            color = SlotVisuals.SELECTED,
                            topLeft = bounds.topLeft,
                            size = bounds.size,
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }

                drawContent()
            }

            // The contextual property editor is anchored to the slot it belongs to.
            val menuSlot = currentSlots.firstOrNull { it.id == currentMenu }
            if (menuSlot != null && geometry != null && menuActions != null) {
                val bounds = geometry.normalizedToCanvas(menuSlot.rect, fitted)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(bounds.left.toInt(), bounds.top.toInt()) }
                        .size(0.dp),
                ) {
                    SlotContextMenu(slot = menuSlot, actions = menuActions)
                }
            }
        }
    }
}

/** The radius of the resize handles, in canvas pixels. */
private val HANDLE_RADIUS = 6.dp

/** The X control that deletes the selected slot; it sits on the top-right corner. */
private fun DrawScope.drawDeleteControl(
    geometry: CanvasGeometry,
    slot: DraftSlot,
    fitted: Rect,
) {
    val radius = AnnotationTargetResolver.DELETE_CONTROL_RADIUS_PX
    val center = geometry.deleteControlCenter(slot.rect, fitted)
    drawCircle(color = Color(0xFFD32F2F), radius = radius, center = center)
    val arm = radius * 0.5f
    drawLine(
        color = Color.White,
        start = Offset(center.x - arm, center.y - arm),
        end = Offset(center.x + arm, center.y + arm),
        strokeWidth = 2.dp.toPx(),
    )
    drawLine(
        color = Color.White,
        start = Offset(center.x + arm, center.y - arm),
        end = Offset(center.x - arm, center.y + arm),
        strokeWidth = 2.dp.toPx(),
    )
}

/**
 * The single pointer handler of the annotation canvas.
 *
 * It implements the interaction table of [AnnotationInteraction]: a tap selects, a drag on
 * a selected slot moves it, a long press on a slot opens its menu, a long press on empty
 * image space draws a new slot, and a drag on a handle resizes. The pointer is only
 * consumed once an annotation gesture is under way, so the page can still be scrolled by
 * dragging outside the image.
 */
private suspend fun PointerInputScope.annotationPointerInput(
    interaction: AnnotationInteraction,
    geometry: () -> CanvasGeometry?,
    fitted: () -> Rect,
    slots: () -> List<DraftSlot>,
    selected: () -> String?,
    handleRadius: Float,
    onAction: (AnnotationAction) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, NormalizedRect) -> Unit,
    onGestureStart: (String) -> Unit,
    onGestureEnd: () -> Unit,
    onDrawing: (NormalizedRect?) -> Unit,
) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(requireUnconsumed = false)
            val geo = geometry() ?: continue
            val imageRect = fitted()
            if (imageRect.width <= 0f || imageRect.height <= 0f) continue

            val startScreen = down.position
            val target = AnnotationTargetResolver.resolve(
                geometry = geo,
                fitted = imageRect,
                offset = startScreen,
                slots = slots(),
                selectedSlotId = selected(),
                handleRadiusPx = handleRadius,
            )
            val downResult = interaction.down(target, selected())
            var state = downResult.state
            if (downResult.action != AnnotationAction.None) onAction(downResult.action)

            if (state.phase == AnnotationInteraction.Phase.Idle) {
                // Outside the displayed image: this pointer belongs to the page.
                continue
            }

            // The canvas owns the gesture from the first touch, so the page cannot scroll
            // away while a slot is being moved, resized or drawn.
            down.consume()

            state.slotId?.let { onGestureStart(it) }
            var longPressFired = false
            var slopExceeded = false
            var lastNormalized = geo.canvasToNormalizedClamped(startScreen, imageRect)
            var drawnRect: NormalizedRect? = null
            val clock = TimeSource.Monotonic
            val startedAt = clock.markNow()

            while (true) {
                // The long press fires on its own timeout, without waiting for a movement.
                val elapsed = startedAt.elapsedNow().inWholeMilliseconds
                val remaining = interaction.longPressTimeoutMs - elapsed
                val event = if (!longPressFired && remaining > 0L) {
                    withTimeoutOrNull(remaining) { awaitPointerEvent() }
                } else {
                    awaitPointerEvent()
                }
                if (event == null) {
                    longPressFired = true
                    val result = interaction.longPress(state)
                    state = result.state
                    if (result.action != AnnotationAction.None) onAction(result.action)
                    continue
                }

                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUpIgnoreConsumed()) break

                val screenPosition = change.position
                val distance = (screenPosition - startScreen).getDistance()

                if (!slopExceeded && distance > interaction.touchSlopPx) {
                    slopExceeded = true
                    val result = interaction.drag(state, distance)
                    state = result.state
                    if (result.action != AnnotationAction.None) onAction(result.action)
                }

                val canvasPosition = screenPosition
                val normalized = geo.canvasToNormalizedClamped(canvasPosition, imageRect)

                when (state.phase) {
                    AnnotationInteraction.Phase.Moving -> {
                        val previous = lastNormalized
                        if (previous != null && normalized != null) {
                            val deltaX = normalized.x - previous.x
                            val deltaY = normalized.y - previous.y
                            if (deltaX != 0f || deltaY != 0f) {
                                state.slotId?.let { onMove(it, deltaX, deltaY) }
                            }
                        }
                        lastNormalized = normalized
                    }

                    AnnotationInteraction.Phase.Resizing -> {
                        val point = geo.canvasToNormalizedClamped(canvasPosition, imageRect)
                        val start = state.startRect
                        if (point != null && start != null) {
                            state.slotId?.let { slotId ->
                                state.corner?.let { corner ->
                                    AnnotationGeometry.resizedRect(start, corner, point)
                                        ?.let { onResize(slotId, it) }
                                }
                            }
                        }
                    }

                    AnnotationInteraction.Phase.Drawing -> {
                        val point = geo.canvasToNormalized(canvasPosition, imageRect)
                        val start = geo.canvasToNormalized(startScreen, imageRect)
                        if (point != null && start != null) {
                            drawnRect = NormalizedRect(
                                left = min(start.x, point.x),
                                top = min(start.y, point.y),
                                right = max(start.x, point.x),
                                bottom = max(start.y, point.y),
                            )
                            onDrawing(drawnRect)
                        }
                    }

                    else -> Unit
                }

                if (slopExceeded || state.phase == AnnotationInteraction.Phase.Drawing) {
                    change.consume()
                }
            }

            val upResult = interaction.up(state, drawnRect)
            if (upResult.action != AnnotationAction.None) onAction(upResult.action)
            onGestureEnd()
            onDrawing(null)
        }
    }
}
