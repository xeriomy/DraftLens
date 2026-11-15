package com.draftlens.feature.visionlab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.draftlens.vision.annotation.DraftSlot
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType
import com.draftlens.vision.geometry.NormalizedRect

/**
 * The manual slot list.
 *
 * Slots are grouped by team and type, every row is keyed by the slot's own id, and the
 * selection is shared with the canvas: tapping a row selects that rectangle, and
 * selecting a rectangle highlights its row.
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
                "Long-press and drag on empty image space to create one.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::undo, enabled = state.canUndo) { Text("Undo") }
                OutlinedButton(onClick = vm::redo, enabled = state.canRedo) { Text("Redo") }
            }

            if (state.slots.isEmpty()) {
                Text("No slots yet.")
            } else {
                SlotGroups(state = state, vm = vm)
            }

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

/** The groups the slots are listed in. */
private val slotGroups = listOf(
    SlotGroup("ALLY · BAN", SlotTeam.ALLY, SlotType.BAN),
    SlotGroup("ENEMY · BAN", SlotTeam.ENEMY, SlotType.BAN),
    SlotGroup("ALLY · PICK", SlotTeam.ALLY, SlotType.PICK),
    SlotGroup("ENEMY · PICK", SlotTeam.ENEMY, SlotType.PICK),
    SlotGroup("GLOBAL", SlotTeam.GLOBAL, null),
)

private data class SlotGroup(val title: String, val team: SlotTeam, val type: SlotType?)

@Composable
private fun SlotGroups(state: VisionLabUiState, vm: VisionLabViewModel) {
    slotGroups.forEach { group ->
        val slots = state.slots.filter { slot ->
            slot.team == group.team && (group.type == null || slot.type == group.type)
        }
        if (slots.isEmpty()) return@forEach
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(group.title, style = MaterialTheme.typography.labelLarge)
            slots.forEach { slot ->
                // The slot's own id is the identity: the list never relies on positions.
                key(slot.id) {
                    SlotListItem(
                        slot = slot,
                        selected = slot.id == state.selectedSlotId,
                        issues = state.slotIssues.filter { slot.id in it.slotIds },
                        onSelect = { vm.selectSlot(slot.id) },
                        onDelete = { vm.deleteSlot(slot.id) },
                        vm = vm,
                    )
                }
            }
        }
    }
}

@Composable
private fun SlotListItem(
    slot: DraftSlot,
    selected: Boolean,
    issues: List<com.draftlens.vision.annotation.SlotIssue>,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    vm: VisionLabViewModel,
) {
    var showGeometry by remember(slot.id) { mutableStateOf(false) }
    var left by remember(slot.rect.left) { mutableStateOf(slot.rect.left.toString()) }
    var top by remember(slot.rect.top) { mutableStateOf(slot.rect.top.toString()) }
    var right by remember(slot.rect.right) { mutableStateOf(slot.rect.right.toString()) }
    var bottom by remember(slot.rect.bottom) { mutableStateOf(slot.rect.bottom.toString()) }
    var error by remember(slot.id) { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(SlotVisuals.teamColor(slot.team)),
                )
                Text(
                    "${slot.type} · ${slot.team} · #${slot.index}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                )
                TextButton(onClick = onSelect) { Text(if (selected) "Selected" else "Select") }
                TextButton(onClick = onDelete) { Text("Delete") }
            }

            slot.label?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "id ${slot.id.take(8)} · " +
                    "(${slot.rect.left}, ${slot.rect.top}) – (${slot.rect.right}, ${slot.rect.bottom})",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )

            issues.forEach { issue ->
                Text(
                    "${issue.severity}: ${issue.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (issue.severity == com.draftlens.vision.annotation.SlotIssueSeverity.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                )
            }

            TextButton(onClick = { showGeometry = !showGeometry }) {
                Text(if (showGeometry) "Hide geometry" else "Edit geometry")
            }

            if (showGeometry) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "left" to left,
                        "top" to top,
                        "right" to right,
                        "bottom" to bottom,
                    ).forEach { (name, value) ->
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
                OutlinedButton(
                    onClick = {
                        val rect = parseRect(left, top, right, bottom)
                        if (rect == null) {
                            error = "Coordinates must be numbers with left < right and top < bottom."
                            return@OutlinedButton
                        }
                        error = vm.resizeSlot(slot.id, rect).exceptionOrNull()?.message
                    },
                ) { Text("Apply geometry") }
            }
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
