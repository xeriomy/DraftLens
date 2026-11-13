package com.draftlens.feature.visionlab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import com.draftlens.vision.annotation.SlotTeam
import com.draftlens.vision.annotation.SlotType

/**
 * The contextual property editor of the selected slot.
 *
 * It opens next to the slot and never occupies a permanent part of the canvas. A dropdown
 * positions itself inside the window, so it is never clipped off-screen in either
 * orientation.
 */
@Composable
fun SlotContextMenu(
    slot: DraftSlot,
    actions: SlotMenuActions,
) {
    var indexText by remember(slot.id, slot.index) { mutableStateOf(slot.index.toString()) }
    var labelText by remember(slot.id, slot.label) { mutableStateOf(slot.label ?: "") }

    DropdownMenu(
        expanded = true,
        onDismissRequest = actions.onClose,
    ) {
        Column(
            modifier = Modifier
                .width(250.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Slot ${slot.id.take(8)}", style = MaterialTheme.typography.titleSmall)

            Text("Type", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlotType.entries.forEach { type ->
                    FilterChip(
                        selected = slot.type == type,
                        onClick = { actions.onTypeChange(slot.id, type) },
                        label = { Text(type.name) },
                    )
                }
            }

            Text("Team", style = MaterialTheme.typography.labelSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SlotTeam.entries.forEach { team ->
                    FilterChip(
                        selected = slot.team == team,
                        onClick = { actions.onTeamChange(slot.id, team) },
                        label = { Text(team.name) },
                    )
                }
            }

            OutlinedTextField(
                value = indexText,
                onValueChange = { indexText = it },
                label = { Text("Index") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = labelText,
                onValueChange = { labelText = it },
                label = { Text("Label (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = {
                    val index = indexText.toIntOrNull()
                    if (index != null && index >= 1) actions.onIndexChange(slot.id, index)
                    actions.onLabelChange(slot.id, labelText.takeIf { it.isNotBlank() })
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apply") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = {
                    actions.onCopy(slot.id)
                    actions.onClose()
                }) { Text("Copy") }
                TextButton(
                    onClick = {
                        actions.onPaste(slot.id)
                        actions.onClose()
                    },
                    enabled = actions.hasClipboard,
                ) { Text("Paste") }
                TextButton(onClick = {
                    actions.onDuplicate(slot.id)
                    actions.onClose()
                }) { Text("Duplicate") }
            }
        }
    }
}
