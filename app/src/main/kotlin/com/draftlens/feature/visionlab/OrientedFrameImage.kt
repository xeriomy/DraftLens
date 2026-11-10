package com.draftlens.feature.visionlab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.draftlens.vision.model.FrameRotation
import com.draftlens.vision.model.OriginalFrame
import com.draftlens.vision.model.android.BitmapFramePayload
import com.draftlens.vision.model.degrees
import kotlin.math.min

/**
 * Draws a captured bitmap in its oriented (upright) form inside [fitted].
 *
 * The overlay in [AnnotationCanvas] is mapped into oriented space, so the pixels have to
 * be turned the same way; otherwise the two disagree for a non-zero correction.
 */
fun DrawScope.drawOrientedBitmap(
    bitmap: ImageBitmap,
    sourceWidth: Int,
    sourceHeight: Int,
    rotation: FrameRotation,
    fitted: Rect,
) {
    if (fitted.width <= 0f || fitted.height <= 0f || sourceWidth <= 0 || sourceHeight <= 0) return
    val center = fitted.center
    withTransform({ rotate(rotation.degrees.toFloat(), pivot = center) }) {
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(
                (center.x - sourceWidth / 2f).toInt(),
                (center.y - sourceHeight / 2f).toInt(),
            ),
            dstSize = IntSize(sourceWidth, sourceHeight),
        )
    }
}

/** Fits an image of [width] x [height] into [available], centred, keeping the aspect ratio. */
fun fittedImageRect(available: Size, width: Int, height: Int): Rect {
    if (available.width <= 0f || available.height <= 0f || width <= 0 || height <= 0) return Rect.Zero
    val scale = min(available.width / width, available.height / height)
    val fittedWidth = width * scale
    val fittedHeight = height * scale
    return Rect(
        left = (available.width - fittedWidth) / 2f,
        top = (available.height - fittedHeight) / 2f,
        right = (available.width + fittedWidth) / 2f,
        bottom = (available.height + fittedHeight) / 2f,
    )
}

/**
 * The frame preview, drawn upright. A frame without a bitmap payload shows a neutral
 * placeholder instead of failing.
 */
@Composable
fun OrientedFrameImage(
    frame: OriginalFrame?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = {},
) {
    val payload = frame?.payload as? BitmapFramePayload
    if (frame == null || payload == null) {
        placeholder()
        return
    }
    var available by remember { mutableStateOf(Size.Zero) }
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { available = Size(it.width.toFloat(), it.height.toFloat()) },
    ) {
        val swapsAxes = frame.rotation == FrameRotation.ROTATION_90 || frame.rotation == FrameRotation.ROTATION_270
        val orientedWidth = if (swapsAxes) frame.sourceHeight else frame.sourceWidth
        val orientedHeight = if (swapsAxes) frame.sourceWidth else frame.sourceHeight
        drawOrientedBitmap(
            bitmap = payload.bitmap.asImageBitmap(),
            sourceWidth = frame.sourceWidth,
            sourceHeight = frame.sourceHeight,
            rotation = frame.rotation,
            fitted = fittedImageRect(available, orientedWidth, orientedHeight),
        )
    }
}
