package com.draftlens.feature.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlens.capture.CaptureController
import com.draftlens.datastore.AppPreferencesRepository
import com.draftlens.models.CaptureStatus

@Composable
fun DebugCaptureScreen(
    preferences: AppPreferencesRepository,
    activity: Activity
) {
    val vm: HomeViewModel = viewModel { HomeViewModel(preferences) }
    val state by vm.uiState.collectAsState()
    val frame by vm.latestFrame.collectAsState()
    val perm by vm.permissionStatus.collectAsState()
    val interval by vm.intervalMs.collectAsState()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            vm.onPermissionGranted()
            CaptureController.start(activity, result.resultCode, result.data!!, interval)
        } else {
            vm.onPermissionDenied()
        }
    }

    val consentIntent = remember {
        com.draftlens.permissions.ProjectionPermission.createScreenCaptureIntent(activity)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("DraftLens — Capture Debug (Sprint 0)", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text("Detection: Not implemented in Sprint 0", color = Color.Gray)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status: ${state.status}")
                Text("Permission: $perm")
                Text("Resolution: ${state.displayWidth} × ${state.displayHeight} @ ${state.densityDpi} dpi")
                Text("Frames: ${state.frameCount}")
                Text("Last frame: ${state.lastFrameTimestampMs?.let { java.text.SimpleDateFormat("HH:mm:ss.SSS").format(it) } ?: "—"}")
                Text("Latency: ${state.lastLatencyMs?.let { "$it ms" } ?: "—"}")
                state.lastError?.let { Text("Error: $it", color = Color.Red) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    vm.onPermissionRequested()
                    launcher.launch(consentIntent)
                },
                enabled = state.status != CaptureStatus.CAPTURING && state.status != CaptureStatus.PAUSED
            ) { Text("Start Capture") }

            Button(
                onClick = {
                    CaptureController.stop(activity)
                    vm.onCaptureStopped()
                },
                enabled = state.isCapturing
            ) { Text("Stop Capture") }

            OutlinedButton(
                onClick = {
                    if (state.isPaused) {
                        CaptureController.resume(activity)
                    } else {
                        CaptureController.pause(activity)
                    }
                },
                enabled = state.isCapturing
            ) { Text(if (state.isPaused) "Resume" else "Pause") }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sampling interval: $interval ms (${1000.0 / interval} FPS, max 3 FPS)", color = Color.White)
            Slider(
                value = interval.toFloat(),
                onValueChange = { vm.setIntervalMs(it.toLong()) },
                valueRange = 334f..2000f,
                steps = 0,
                enabled = !state.isCapturing
            )
        }

        frame?.let { f ->
            if (!f.bitmap.isRecycled) {
                Image(
                    bitmap = f.bitmap.asImageBitmap(),
                    contentDescription = "Latest captured frame",
                    modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f),
                    contentScale = ContentScale.FillBounds
                )
            }
        } ?: Text(
            "No frame yet. Start capture to see live preview.",
            color = Color.Gray
        )
    }
}
