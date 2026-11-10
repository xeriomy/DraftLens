package com.draftlens.feature.visionlab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.NormalizedPoint
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.android.BitmapFramePayload
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive canvas over the oriented frame.
 *
 * The canvas draws the oriented, uncropped frame. Slot rectangles are stored in
 * normalized *source* coordinates, so every pointer position is mapped through the
 * frame transform instead of being guessed from screen dp.
 */
@Composable
fun AnnotationCanvas(
    frame: OriginalFrame?,
    slots: List<DraftSlot>,
    selectedSlotId: String?,
    onSelect: (String?) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onResize: (String, NormalizedRect) -> Unit,
    onCreate: (NormalizedRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var gesture by remember { mutableStateOf<CanvasGesture?>(null) }
    var pending by remember { mutableStateOf<Rect?>(null) }

    val currentSlots by rememberUpdatedState(slots)
    val currentSelected by rememberUpdatedState(selectedSlotId)
    // Keyed on the frame's shape, not its identity: a new frame of the same size and
    // rotation must not restart the gesture detectors.
    val geometry = remember(frame?.sourceWidth, frame?.sourceHeight, frame?.rotation) {
        frame?.let { CanvasGeometry.of(it) }
    }
    val fitted = remember(canvasSize, geometry) {
        fittedImageRect(
            available = Size(canvasSize.width.toFloat(), canvasSize.height.toFloat()),
            width = geometry?.orientedWidth ?: 0,
            height = geometry?.orientedHeight ?: 0,
        )
    }
    val handleRadius = with(LocalDensity.current) { HANDLE_RADIUS.toPx() }
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .onSizeChanged { canvasSize = it }
            .pointerInput(geometry, fitted) {
                detectTapGestures(
                    onTap = { offset ->
                        val geo = geometry ?: return@detectTapGestures
                        val point = geo.canvasToNormalized(offset, fitted) ?: return@detectTapGestures
                        onSelect(AnnotationHitTest.slotAt(point, currentSlots)?.id)
                    },
                )
            }
            .pointerInput(geometry, fitted, handleRadius) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val geo = geometry ?: return@detectDragGestures
                        val point = geo.canvasToNormalized(offset, fitted) ?: return@detectDragGestures
                        val selected = currentSlots.firstOrNull { it.id == currentSelected }
                        val handle = selected?.let { geo.handleAt(it.rect, offset, fitted, handleRadius) }
                        when {
                            selected != null && handle != null ->
                                gesture = CanvasGesture.Resize(selected.id, handle, selected.rect)
                            else -> {
                                val hit = AnnotationHitTest.slotAt(point, currentSlots)
                                if (hit != null) {
                                    gesture = CanvasGesture.Move(hit.id, point)
                                    onSelect(hit.id)
                                } else {
                                    gesture = CanvasGesture.Create(point, point)
                                    pending = Rect(point.x, point.y, point.x, point.y)
                                }
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        val geo = geometry ?: return@detectDragGestures
                        when (val current = gesture) {
                            is CanvasGesture.Move -> {
                                // Clamped so a drag that leaves the image keeps following the pointer.
                                val point = geo.canvasToNormalizedClamped(change.position, fitted)
                                    ?: return@detectDragGestures
                                onMove(current.slotId, point.x - current.last.x, point.y - current.last.y)
                                gesture = current.copy(last = point)
                            }
                            is CanvasGesture.Resize -> {
                                val point = geo.canvasToNormalizedClamped(change.position, fitted)
                                    ?: return@detectDragGestures
                                resizeRect(current.start, current.handle, point)?.let { onResize(current.slotId, it) }
                            }
                            is CanvasGesture.Create -> {
                                val point = geo.canvasToNormalized(change.position, fitted)
                                    ?: return@detectDragGestures
                                gesture = current.copy(current = point)
                                pending = Rect(
                                    min(current.start.x, point.x),
                                    min(current.start.y, point.y),
                                    max(current.start.x, point.x),
                                    max(current.start.y, point.y),
                                )
                            }
                            null -> Unit
                        }
                    },
                    onDragEnd = {
                        val current = gesture
                        if (current is CanvasGesture.Create) {
                            pending?.toNormalizedRect()?.let(onCreate)
                        }
                        gesture = null
                        pending = null
                    },
                    onDragCancel = {
                        gesture = null
                        pending = null
                    },
                )
            },
    ) {
        val geo = geometry ?: return@Canvas
        if (fitted.width <= 0f || fitted.height <= 0f) return@Canvas

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
                drawRect(color = Color(0x33FF9800), topLeft = bounds.topLeft, size = bounds.size)
            }
            drawRect(
                color = if (isSelected) Color(0xFFFF9800) else Color(0xFF4CAF50),
                topLeft = bounds.topLeft,
                size = bounds.size,
                style = Stroke(width = 1.dp.toPx()),
            )
            val measured = textMeasurer.measure(
                overlayLabel(slot),
                TextStyle(color = Color.White, fontSize = 11.sp),
            )
            drawText(measured, topLeft = Offset(bounds.left, max(bounds.top - measured.size.height, 0f)))
        }

        slots.firstOrNull { it.id == selectedSlotId }?.let { slot ->
            val bounds = geo.normalizedToCanvas(slot.rect, fitted)
            listOf(
                bounds.topLeft,
                Offset(bounds.right, bounds.top),
                Offset(bounds.left, bounds.bottom),
                bounds.bottomRight,
            ).forEach { corner ->
                drawCircle(color = Color(0xFFFF9800), radius = handleRadius, center = corner)
            }
        }

        pending?.let { rect ->
            val bounds = geo.normalizedToCanvas(rect.left, rect.top, rect.right, rect.bottom, fitted)
            drawRect(
                color = Color(0xFF2196F3),
                topLeft = bounds.topLeft,
                size = bounds.size,
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}

/** Overlay label such as `A-B1`, `E-P2` or `G-B1`. */
private fun overlayLabel(slot: DraftSlot): String {
    val team = when (slot.team) {
        SlotTeam.ALLY -> "A"
        SlotTeam.ENEMY -> "E"
        SlotTeam.GLOBAL -> "G"
    }
    val type = when (slot.type) {
        SlotType.BAN -> "B"
        SlotType.PICK -> "P"
    }
    return "$team-$type${slot.index}"
}

private val HANDLE_RADIUS = 6.dp

private sealed interface CanvasGesture {
    data class Move(val slotId: String, val last: NormalizedPoint) : CanvasGesture
    data class Resize(val slotId: String, val handle: Corner, val start: NormalizedRect) : CanvasGesture
    data class Create(val start: NormalizedPoint, val current: NormalizedPoint) : CanvasGesture
}

/** Minimum normalized size the store accepts; smaller drags are discarded. */
private const val MIN_SIZE = 0.01f

private fun Rect.toNormalizedRect(): NormalizedRect? {
    val left = left.coerceIn(0f, 1f)
    val top = top.coerceIn(0f, 1f)
    val right = right.coerceIn(0f, 1f)
    val bottom = bottom.coerceIn(0f, 1f)
    if (right - left < MIN_SIZE || bottom - top < MIN_SIZE) return null
    return NormalizedRect(left, top, right, bottom)
}

private fun resizeRect(start: NormalizedRect, handle: Corner, point: NormalizedPoint): NormalizedRect? {
    val x = point.x.coerceIn(0f, 1f)
    val y = point.y.coerceIn(0f, 1f)
    val left: Float
    val top: Float
    val right: Float
    val bottom: Float
    when (handle) {
        Corner.TOP_LEFT -> {
            left = x; top = y; right = start.right; bottom = start.bottom
        }
        Corner.TOP_RIGHT -> {
            left = start.left; top = y; right = x; bottom = start.bottom
        }
        Corner.BOTTOM_LEFT -> {
            left = x; top = start.top; right = start.right; bottom = y
        }
        Corner.BOTTOM_RIGHT -> {
            left = start.left; top = start.top; right = x; bottom = y
        }
    }
    if (right - left < MIN_SIZE || bottom - top < MIN_SIZE) return null
    return NormalizedRect(left, top, right, bottom)
}
