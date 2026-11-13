package com.draftlens.feature.visionlab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlens.vision.calibration.CalibrationRepository
import com.draftlens.vision.model.VisionFrameSource
import com.draftlens.vision.preprocessing.AndroidFramePreprocessor

/**
 * Vision Lab: inspect frames, place manual draft-slot annotations, and manage calibration
 * profiles. There is no recognizer in this sprint, so nothing here reports a detection.
 */
@Composable
fun VisionLabScreen(
    calibrationRepository: CalibrationRepository,
    source: VisionFrameSource,
) {
    val vm: VisionLabViewModel = viewModel {
        VisionLabViewModel(
            source = source,
            preprocessor = AndroidFramePreprocessor,
            calibrationRepository = calibrationRepository,
        )
    }
    val state by vm.uiState.collectAsState()

    if (state.fullscreenEditor) {
        FullscreenAnnotationEditor(
            frame = state.displayFrame,
            slots = state.slots,
            selectedSlotId = state.selectedSlotId,
            menuSlotId = state.menuSlotId,
            onSelect = { vm.selectSlot(it) },
            onMove = { id, dx, dy -> vm.moveSlot(id, dx, dy) },
            onResize = { id, rect -> vm.resizeSlot(id, rect) },
            onCreate = { rect -> vm.createSlot(rect) },
            onDelete = { vm.deleteSlot(it) },
            onOpenMenu = { vm.openSlotMenu(it) },
            onGestureStart = { vm.beginSlotGesture(it) },
            onGestureEnd = { vm.endSlotGesture() },
            menuActions = state.slotMenuActions(vm),
            onDone = { vm.closeFullscreenEditor() },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("DraftLens — Vision Lab (Sprint 1A)", style = MaterialTheme.typography.titleLarge)
        Text("Detection: not implemented. Manual slots are calibration data, not detections.")

        FramePreviewCard(
            state = state,
            onFreeze = vm::freeze,
            onResume = vm::resume,
            onProcess = vm::processFrame,
            onClear = vm::clear,
        )

        AnnotationCanvasCard(state = state, vm = vm)

        SlotEditor(state = state, vm = vm)

        ProcessingInspectorCard(state = state, onMaxDimensionChange = vm::setMaxProcessedDimension)

        ProfileControls(state = state, vm = vm)

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}

/** The actions the contextual property editor performs. */
private fun VisionLabUiState.slotMenuActions(vm: VisionLabViewModel) = SlotMenuActions(
    onTypeChange = { id, type -> vm.updateSlotType(id, type) },
    onTeamChange = { id, team -> vm.updateSlotTeam(id, team) },
    onIndexChange = { id, index -> vm.updateSlotIndex(id, index) },
    onLabelChange = { id, label -> vm.updateSlotLabel(id, label) },
    onCopy = { id -> vm.copySlot(id) },
    onPaste = { id -> vm.pasteSlot(id) },
    onDuplicate = { id -> vm.duplicateSlot(id) },
    onClose = { vm.closeSlotMenu() },
    hasClipboard = clipboardSlot != null,
)

@Composable
private fun FramePreviewCard(
    state: VisionLabUiState,
    onFreeze: () -> Unit,
    onResume: () -> Unit,
    onProcess: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Frame Preview", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (state.isFrozen) "FROZEN" else "LIVE",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.isFrozen) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Text("Source: ${state.sourceState}", style = MaterialTheme.typography.labelLarge)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF111621)),
                contentAlignment = Alignment.Center,
            ) {
                OrientedFrameImage(
                    frame = state.displayFrame,
                    modifier = Modifier.fillMaxSize(),
                    placeholder = {
                        Text(
                            "No bitmap frame. Start capture on the Debug Capture tab, or use a test fixture.",
                            color = Color.Gray,
                            modifier = Modifier.padding(12.dp),
                        )
                    },
                )
            }

            val frame = state.displayFrame
            Text("Original: ${frame?.sourceWidth ?: 0} × ${frame?.sourceHeight ?: 0}")
            Text("Display rotation: ${frame?.displayRotation ?: "—"}")
            Text("Orientation correction: ${frame?.rotation ?: "—"}")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = if (state.isFrozen) onResume else onFreeze) {
                    Text(if (state.isFrozen) "Resume" else "Freeze")
                }
                OutlinedButton(onClick = onProcess, enabled = frame != null) { Text("Process Frame") }
                OutlinedButton(
                    onClick = onClear,
                    enabled = state.isFrozen || state.processed != null,
                ) { Text("Clear") }
            }
            Text(
                if (state.isFrozen) {
                    "Resume returns to live frames."
                } else {
                    "Freeze locks the current frame before annotation."
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun AnnotationCanvasCard(state: VisionLabUiState, vm: VisionLabViewModel) {
    var showHint by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF181D26)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Annotation Canvas",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.openFullscreenEditor() }) {
                    Icon(
                        imageVector = ExpandIconVector,
                        contentDescription = "Fullscreen annotation",
                        tint = Color.White,
                    )
                }
            }
            if (showHint) {
                AnnotationGestureHint(onDismiss = { showHint = false })
            } else {
                TextButton(onClick = { showHint = true }) { Text("Show gesture help") }
            }
            AnnotationCanvas(
                frame = state.displayFrame,
                slots = state.slots,
                selectedSlotId = state.selectedSlotId,
                menuSlotId = state.menuSlotId,
                onSelect = { vm.selectSlot(it) },
                onMove = { id, dx, dy -> vm.moveSlot(id, dx, dy) },
                onResize = { id, rect -> vm.resizeSlot(id, rect) },
                onCreate = { rect -> vm.createSlot(rect) },
                onDelete = { vm.deleteSlot(it) },
                onOpenMenu = { vm.openSlotMenu(it) },
                onGestureStart = { vm.beginSlotGesture(it) },
                onGestureEnd = { vm.endSlotGesture() },
                menuActions = state.slotMenuActions(vm),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ProcessingInspectorCard(
    state: VisionLabUiState,
    onMaxDimensionChange: (Int) -> Unit,
) {
    var dimensionText by remember(state.maxProcessedDimension) {
        mutableStateOf(state.maxProcessedDimension.toString())
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Processing Inspector", style = MaterialTheme.typography.titleMedium)
            Text("Detection: not implemented")
            Text(
                "Process Frame resizes and prepares the frame only; no recognition exists yet.",
                style = MaterialTheme.typography.bodySmall,
            )

            val source = state.displayFrame
            val processed = state.processed
            Text("Source dimensions: ${source?.sourceWidth ?: 0} × ${source?.sourceHeight ?: 0}")
            Text("Output dimensions: ${processed?.payload?.width ?: 0} × ${processed?.payload?.height ?: 0}")
            Text("Rotation: ${processed?.rotation ?: source?.rotation ?: "—"}")
            Text("Crop: ${cropText(processed)}")
            Text("Scale: ${processed?.transform?.scale ?: "—"}")
            Text("Duration: ${processed?.processingDurationMs?.let { "$it ms" } ?: "—"}")
            Text("Status: ${state.processStatus}")
            Text("Pixels shown: ${if (state.isFrozen) "frozen" else "live"}")
            state.processError?.let { Text("Error: $it", color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(
                value = dimensionText,
                onValueChange = { text ->
                    dimensionText = text
                    text.toIntOrNull()?.let(onMaxDimensionChange)
                },
                label = { Text("Max processed dimension") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The crop of a processed frame, or "none" when the whole oriented frame was used. */
private fun cropText(processed: com.draftlens.vision.model.ProcessedFrame?): String {
    val crop = processed?.transform?.crop ?: return "none"
    if (crop.left == 0f && crop.top == 0f && crop.right == 1f && crop.bottom == 1f) return "none"
    return "${crop.left}..${crop.right} × ${crop.top}..${crop.bottom}"
}
