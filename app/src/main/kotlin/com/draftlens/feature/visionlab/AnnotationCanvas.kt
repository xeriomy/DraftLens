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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.FloatPoint
import com.draftlens.vision.geometry.FrameTransform
import com.draftlens.vision.geometry.NormalizedPoint
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.android.BitmapFramePayload
import kotlin.math.abs
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
    val geometry = remember(frame) { frame?.let { CanvasGeometry.of(it) } }
    val fitted = remember(canvasSize, geometry) {
        fittedRect(canvasSize, geometry?.orientedWidth ?: 0, geometry?.orientedHeight ?: 0)
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
                        onSelect(currentSlots.lastOrNull { it.rect.containsPoint(point) }?.id)
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
                            selected != null && selected.rect.containsPoint(point) -> {
                                gesture = CanvasGesture.Move(selected.id, point)
                                onSelect(selected.id)
                            }
                            else -> {
                                gesture = CanvasGesture.Create(point, point)
                                pending = Rect(point.x, point.y, point.x, point.y)
                            }
                        }
                    },
                    onDrag = { change, _ ->
                        val geo = geometry ?: return@detectDragGestures
                        when (val current = gesture) {
                            is CanvasGesture.Move -> {
                                val point = geo.canvasToNormalized(change.position, fitted) ?: return@detectDragGestures
                                onMove(current.slotId, point.x - current.last.x, point.y - current.last.y)
                                gesture = current.copy(last = point)
                            }
                            is CanvasGesture.Resize -> {
                                val point = geo.canvasToNormalized(change.position, fitted) ?: return@detectDragGestures
                                resizeRect(current.start, current.handle, point)?.let { onResize(current.slotId, it) }
                            }
                            is CanvasGesture.Create -> {
                                val point = geo.canvasToNormalized(change.position, fitted) ?: return@detectDragGestures
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
            drawImage(
                image = payload.bitmap.asImageBitmap(),
                dstOffset = IntOffset(fitted.left.toInt(), fitted.top.toInt()),
                dstSize = IntSize(fitted.width.toInt(), fitted.height.toInt()),
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

private enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * Frame geometry for the canvas: the transform between source pixels and the oriented
 * image the user annotates.
 */
private class CanvasGeometry(
    val transform: FrameTransform,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val orientedWidth: Int,
    val orientedHeight: Int,
) {
    companion object {
        fun of(frame: OriginalFrame): CanvasGeometry {
            val swapsAxes =
                frame.rotation == FrameRotation.ROTATION_90 || frame.rotation == FrameRotation.ROTATION_270
            val orientedWidth = if (swapsAxes) frame.sourceHeight else frame.sourceWidth
            val orientedHeight = if (swapsAxes) frame.sourceWidth else frame.sourceHeight
            return CanvasGeometry(
                transform = FrameTransform.create(
                    sourceWidth = frame.sourceWidth,
                    sourceHeight = frame.sourceHeight,
                    rotation = frame.rotation,
                    crop = null,
                    processedWidth = orientedWidth,
                    processedHeight = orientedHeight,
                ),
                sourceWidth = frame.sourceWidth,
                sourceHeight = frame.sourceHeight,
                orientedWidth = orientedWidth,
                orientedHeight = orientedHeight,
            )
        }
    }

    fun normalizedToCanvas(rect: NormalizedRect, fitted: Rect): Rect =
        normalizedToCanvas(rect.left, rect.top, rect.right, rect.bottom, fitted)

    fun normalizedToCanvas(left: Float, top: Float, right: Float, bottom: Float, fitted: Rect): Rect {
        val topLeft = transform.sourceToOriented(FloatPoint(left * sourceWidth, top * sourceHeight))
        val bottomRight = transform.sourceToOriented(FloatPoint(right * sourceWidth, bottom * sourceHeight))
        val orientedLeft = min(topLeft.x, bottomRight.x)
        val orientedTop = min(topLeft.y, bottomRight.y)
        val orientedRight = max(topLeft.x, bottomRight.x)
        val orientedBottom = max(topLeft.y, bottomRight.y)
        return Rect(
            left = fitted.left + orientedLeft / orientedWidth * fitted.width,
            top = fitted.top + orientedTop / orientedHeight * fitted.height,
            right = fitted.left + orientedRight / orientedWidth * fitted.width,
            bottom = fitted.top + orientedBottom / orientedHeight * fitted.height,
        )
    }

    fun canvasToNormalized(offset: Offset, fitted: Rect): NormalizedPoint? {
        if (fitted.width <= 0f || fitted.height <= 0f) return null
        val oriented = FloatPoint(
            (offset.x - fitted.left) / fitted.width * orientedWidth,
            (offset.y - fitted.top) / fitted.height * orientedHeight,
        )
        val source = transform.orientedToSource(oriented)
        return NormalizedPoint(
            x = (source.x / sourceWidth).coerceIn(0f, 1f),
            y = (source.y / sourceHeight).coerceIn(0f, 1f),
        )
    }

    /** Corner handle under the pointer, if any. */
    fun handleAt(rect: NormalizedRect, offset: Offset, fitted: Rect, radius: Float): Corner? {
        val bounds = normalizedToCanvas(rect, fitted)
        val touch = radius * 2f
        val corners = listOf(
            Corner.TOP_LEFT to bounds.topLeft,
            Corner.TOP_RIGHT to Offset(bounds.right, bounds.top),
            Corner.BOTTOM_LEFT to Offset(bounds.left, bounds.bottom),
            Corner.BOTTOM_RIGHT to bounds.bottomRight,
        )
        return corners.firstOrNull { (_, corner) ->
            abs(corner.x - offset.x) <= touch && abs(corner.y - offset.y) <= touch
        }?.first
    }
}

private fun fittedRect(canvas: IntSize, orientedWidth: Int, orientedHeight: Int): Rect {
    if (canvas.width <= 0 || canvas.height <= 0 || orientedWidth <= 0 || orientedHeight <= 0) return Rect.Zero
    val scale = min(
        canvas.width.toFloat() / orientedWidth,
        canvas.height.toFloat() / orientedHeight,
    )
    val width = orientedWidth * scale
    val height = orientedHeight * scale
    return Rect(
        left = (canvas.width - width) / 2f,
        top = (canvas.height - height) / 2f,
        right = (canvas.width + width) / 2f,
        bottom = (canvas.height + height) / 2f,
    )
}

private fun NormalizedRect.containsPoint(point: NormalizedPoint): Boolean =
    point.x in left..right && point.y in top..bottom

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
