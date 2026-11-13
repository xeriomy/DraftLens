package com.draftlens.feature.visionlab

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.geometry.NormalizedRect
import com.draftlens.vision.model.OriginalFrame

/**
 * The fullscreen annotation editor.
 *
 * The image gets the whole screen, in whichever orientation the device is in. There is no
 * zoom: the frame is fitted into the screen and the slot overlay stays aligned with it.
 * A compact control in the corner and the Android Back button leave the editor.
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
    var showHint by remember { mutableStateOf(true) }

    // Back leaves the editor first, not the Vision Lab.
    BackHandler(onBack = onDone)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
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
            workspaceMode = AnnotationWorkspaceMode.Fill,
            modifier = Modifier.fillMaxSize(),
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onDone) {
                Icon(
                    imageVector = CollapseIconVector,
                    contentDescription = "Leave fullscreen annotation",
                    tint = Color.White,
                )
            }
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
