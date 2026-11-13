package com.draftlens.feature.visionlab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import com.draftlens.vision.calibration.CalibrationLoadResult
import com.draftlens.vision.calibration.CalibrationProfile

/**
 * Calibration profile controls. A profile never changes the slots silently: applying one
 * is an explicit action that is refused when the frame does not match the profile.
 */
@Composable
fun ProfileControls(state: VisionLabUiState, vm: VisionLabViewModel) {
    var label by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<CalibrationProfile?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Calibration profiles", style = MaterialTheme.typography.titleMedium)
            Text(
                "A calibration profile saves and restores normalized slot coordinates for matching screen layouts.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!state.isFrozen) {
                Text(
                    "Freeze a frame before saving, updating or applying a profile.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            when (val calibration = state.calibration) {
                is CalibrationLoadResult.Success -> {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Profile label") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            vm.saveProfile(label)
                            label = ""
                        },
                        enabled = state.canWriteProfiles,
                    ) { Text("Save current annotations") }

                    if (calibration.profiles.isEmpty()) {
                        Text("No profiles yet.")
                    }
                    calibration.profiles.forEach { profile ->
                        ProfileRow(
                            profile = profile,
                            onApply = { vm.applyProfile(profile.profileId) },
                            onUpdate = { vm.updateProfile(profile.profileId, label.ifBlank { profile.label ?: "" }) },
                            onDelete = { pendingDelete = profile },
                        )
                    }
                }

                is CalibrationLoadResult.UnsupportedSchema -> {
                    Text(calibration.message, color = MaterialTheme.colorScheme.error)
                    Text("Saving and updating are disabled until this data is deleted.")
                    OutlinedButton(onClick = { confirmDeleteAll = true }) {
                        Text("Delete all calibration data")
                    }
                }

                is CalibrationLoadResult.Corrupt -> {
                    Text(calibration.message, color = MaterialTheme.colorScheme.error)
                    Text("The stored data cannot be read, so it is left untouched.")
                    OutlinedButton(onClick = { confirmDeleteAll = true }) {
                        Text("Delete all calibration data")
                    }
                }
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete profile?") },
            text = { Text("\"${profile.label ?: profile.profileId}\" is removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteProfile(profile.profileId)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all calibration data?") },
            text = { Text("Every profile is removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAllProfiles()
                    confirmDeleteAll = false
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    profile: CalibrationProfile,
    onApply: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(profile.label ?: profile.profileId, style = MaterialTheme.typography.titleSmall)
        Text(
            "ratio ${profile.targetAspectRatio} · ${profile.expectedOrientation} · ${profile.slots.size} slots",
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onApply) { Text("Apply") }
            OutlinedButton(onClick = onUpdate) { Text("Update") }
            OutlinedButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
