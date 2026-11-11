package com.draftlens.feature.visionlab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotIssue
import com.draftlens.vision.annotation.SlotIssueSeverity
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.NormalizedRect

/**
 * Text alternative to the canvas: every slot's metadata and geometry can be edited
 * without gestures, so the annotation set stays reachable with a screen reader.
 */
@Composable
fun SlotEditor(state: VisionLabUiState, vm: VisionLabViewModel) {
    var confirmReset by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Manual slots", style = MaterialTheme.typography.titleMedium)
            Text(
                "A slot is a manually marked region where a ban/pick hero portrait is expected.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "These are manual annotations, not detections.",
                style = MaterialTheme.typography.bodySmall,
            )

            if (state.slots.isEmpty()) {
                Text("No slots yet. Draw one on the canvas or add one below.")
            }

            state.slots.forEach { slot ->
                val issues = state.slotIssues.filter { slot.id in it.slotIds }
                SlotCard(
                    slot = slot,
                    issues = issues,
                    selected = slot.id == state.selectedSlotId,
                    onSelect = { vm.selectSlot(slot.id) },
                    onDelete = { vm.deleteSlot(slot.id) },
                    vm = vm,
                )
            }

            AddSlotCard(vm = vm)

            OutlinedButton(
                onClick = { confirmReset = true },
                enabled = state.slots.isNotEmpty(),
            ) { Text("Reset annotations") }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset annotations?") },
            text = { Text("All manual slots are removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.resetSlots()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SlotCard(
    slot: DraftSlot,
    issues: List<SlotIssue>,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    vm: VisionLabViewModel,
) {
    // Keyed on the model values, so a slot moved on the canvas resyncs the text fields.
    var left by remember(slot.rect.left) { mutableStateOf(slot.rect.left.toString()) }
    var top by remember(slot.rect.top) { mutableStateOf(slot.rect.top.toString()) }
    var right by remember(slot.rect.right) { mutableStateOf(slot.rect.right.toString()) }
    var bottom by remember(slot.rect.bottom) { mutableStateOf(slot.rect.bottom.toString()) }
    var label by remember(slot.label) { mutableStateOf(slot.label ?: "") }
    var index by remember(slot.index) { mutableStateOf(slot.index.toString()) }
    var error by remember(slot.id) { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Slot ${slot.id.take(8)}", style = MaterialTheme.typography.titleSmall)
            Text(
                "${slot.type} / ${slot.team} / index ${slot.index}",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlotTeam.entries.forEach { team ->
                    FilterChip(
                        selected = slot.team == team,
                        onClick = { error = vm.updateSlotTeam(slot.id, team).exceptionOrNull()?.message },
                        label = { Text(team.name) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlotType.entries.forEach { type ->
                    FilterChip(
                        selected = slot.type == type,
                        onClick = { error = vm.updateSlotType(slot.id, type).exceptionOrNull()?.message },
                        label = { Text(type.name) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = index,
                    onValueChange = { index = it },
                    label = { Text("Index") },
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("left" to left, "top" to top, "right" to right, "bottom" to bottom)
                    .forEach { (name, value) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { newValue ->
                                when (name) {
                                    "left" -> left = newValue
                                    "top" -> top = newValue
                                    "right" -> right = newValue
                                    else -> bottom = newValue
                                }
                            },
                            label = { Text(name) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            issues.forEach { issue ->
                Text(
                    "${issue.severity}: ${issue.message}",
                    color = if (issue.severity == SlotIssueSeverity.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSelect) { Text(if (selected) "Selected" else "Select") }
                OutlinedButton(
                    onClick = {
                        val rect = parseRect(left, top, right, bottom)
                        if (rect == null) {
                            error = "Coordinates must be numbers with left < right and top < bottom."
                            return@OutlinedButton
                        }
                        val newIndex = index.toIntOrNull()
                        if (newIndex == null || newIndex < 1) {
                            error = "Index must be a number of at least 1."
                            return@OutlinedButton
                        }
                        val indexFailure = vm.updateSlotIndex(slot.id, newIndex).exceptionOrNull()?.message
                        if (indexFailure != null) {
                            error = indexFailure
                            return@OutlinedButton
                        }
                        val labelFailure = vm.updateSlotLabel(slot.id, label.takeIf { it.isNotBlank() })
                            .exceptionOrNull()?.message
                        if (labelFailure != null) {
                            error = labelFailure
                            return@OutlinedButton
                        }
                        error = vm.resizeSlot(slot.id, rect).exceptionOrNull()?.message
                    },
                ) { Text("Apply") }
                OutlinedButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun AddSlotCard(vm: VisionLabViewModel) {
    var team by remember { mutableStateOf(SlotTeam.ALLY) }
    var type by remember { mutableStateOf(SlotType.BAN) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Add slot", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlotTeam.entries.forEach { option ->
                    FilterChip(
                        selected = team == option,
                        onClick = { team = option },
                        label = { Text(option.name) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlotType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        label = { Text(option.name) },
                    )
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            AssistChip(
                onClick = {
                    val result = vm.addSlot(type, team, NormalizedRect(0.1f, 0.1f, 0.25f, 0.25f))
                    error = result.exceptionOrNull()?.message
                },
                label = { Text("Add at 0.10,0.10 – 0.25,0.25") },
            )
        }
    }
}

private fun parseRect(left: String, top: String, right: String, bottom: String): NormalizedRect? {
    val l = left.toFloatOrNull() ?: return null
    val t = top.toFloatOrNull() ?: return null
    val r = right.toFloatOrNull() ?: return null
    val b = bottom.toFloatOrNull() ?: return null
    if (l < 0f || t < 0f || r > 1f || b > 1f) return null
    return runCatching { NormalizedRect(l, t, r, b) }.getOrNull()
}
