package com.draftlens.feature.visionlab

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.OriginalFrame

/**
 * The fullscreen annotation editor.
 *
 * The image gets the whole screen, in whichever orientation the device is in. A pinch
 * with two fingers zooms and pans; the single-finger gestures stay with the annotation
 * model, so editing and navigating never fight over the same pointer.
 */
@Composable
fun FullscreenAnnotationEditor(
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
    onGestureStart: (String) -> Unit,
    onGestureEnd: () -> Unit,
    menuActions: SlotMenuActions?,
    onDone: () -> Unit,
) {
    var transform by remember { mutableStateOf(AnnotationViewTransform()) }
    var showHint by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val current = transform
                        transform = current.copy(
                            scale = (current.scale * zoom).coerceIn(
                                AnnotationViewTransform.MIN_SCALE,
                                AnnotationViewTransform.MAX_SCALE,
                            ),
                            offset = current.offset + pan,
                        )
                    }
                },
        ) {
            AnnotationCanvas(
                frame = frame,
                slots = slots,
                selectedSlotId = selectedSlotId,
                menuSlotId = menuSlotId,
                onSelect = onSelect,
                onMove = onMove,
                onResize = onResize,
                onCreate = onCreate,
                onDelete = onDelete,
                onOpenMenu = onOpenMenu,
                onGestureStart = onGestureStart,
                onGestureEnd = onGestureEnd,
                menuActions = menuActions,
                viewTransform = transform,
                workspaceMode = AnnotationWorkspaceMode.Fill,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TextButton(onClick = { transform = AnnotationViewTransform() }) { Text("Fit to screen") }
            TextButton(onClick = { transform = transform.copy(scale = 1f) }) { Text("Reset zoom") }
            Button(onClick = onDone) { Text("Done") }
        }

        if (showHint) {
            AnnotationGestureHint(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp),
                onDismiss = { showHint = false },
            )
        }
    }
}
