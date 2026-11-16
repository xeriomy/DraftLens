package com.draftlens.feature.home

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.draftlens.capture.CaptureController
import com.draftlens.datastore.AppPreferencesRepository
import com.draftlens.models.CaptureStatus
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.Manifest
import android.os.Build
import androidx.compose.material3.Switch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import com.draftlens.vision.model.android.BitmapFramePayload

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

    // Notification permission: the capture notification keeps a Freeze action, but the
    // floating control and in-app Freeze/Resume work without it.
    var notificationsEnabled by remember { mutableStateOf(areCaptureNotificationsEnabled(activity)) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsEnabled = granted || areCaptureNotificationsEnabled(activity) }

    val requestStartCapture = {
        vm.onPermissionRequested()
        launcher.launch(com.draftlens.permissions.ProjectionPermission.createScreenCaptureIntent(activity))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("DraftLens — Capture", color = Color.White, style = MaterialTheme.typography.titleLarge)

        PermissionsCard(
            activity = activity,
            preferences = preferences,
            notificationsEnabled = notificationsEnabled,
            onEnableNotifications = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    activity.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Status: ${state.status}")
                Text("Permission: $perm")
                Text("Resolution: ${state.displayWidth} × ${state.displayHeight} @ ${state.densityDpi} dpi")
                Text("Frames: ${state.frameCount}")
                Text("Last frame: ${state.lastFrameWallClockMs?.let { java.text.SimpleDateFormat("HH:mm:ss.SSS").format(it) } ?: "—"}")
                Text("Frame processing: ${state.lastFrameProcessingLatencyMs?.let { "$it ms" } ?: "—"}  (acquisition → Bitmap, not detection)")
                state.lastError?.let { Text("Error: $it", color = Color.Red) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { requestStartCapture() },
                enabled = !state.isStartBlocking
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

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181D26))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Live preview", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(Color(0xFF111621)),
                    contentAlignment = Alignment.Center
                ) {
                    val payload = frame?.frame?.payload as? BitmapFramePayload
                    if (payload != null) {
                        Image(
                            bitmap = payload.bitmap.asImageBitmap(),
                            contentDescription = "Latest captured frame",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            "No frame yet. Start capture to see live preview.",
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

/**
 * The only two permissions DraftLens needs beyond the screen-capture consent: notifications
 * (the capture notification carries Stop + Pause) and the overlay permission (the floating
 * freeze button). Both show clear granted/denied status and a grant/open-settings action.
 */
@Composable
private fun PermissionsCard(
    activity: Activity,
    preferences: AppPreferencesRepository,
    notificationsEnabled: Boolean,
    onEnableNotifications: () -> Unit,
) {
    val body = MaterialTheme.typography.bodySmall
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(activity)) }
    var pendingEnable by remember { mutableStateOf(false) }
    val floatingEnabledFlow = remember(preferences) {
        preferences.floatingControlEnabled.catch { emit(false) }
    }
    val floatingEnabled by floatingEnabledFlow.collectAsState(initial = false)
    val scope = rememberCoroutineScope()

    // Returning from the Android overlay-permission settings screen resolves a pending enable exactly once.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val granted = Settings.canDrawOverlays(activity)
        overlayGranted = granted
        if (pendingEnable) {
            pendingEnable = false
            if (granted) {
                scope.launch {
                    runCatching { preferences.setFloatingControlEnabled(true) }
                        .onFailure { android.util.Log.w("DraftLens", "failed to persist floating control enable", it) }
                    CaptureController.refreshOverlay(activity)
                }
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // --- Notifications ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Needed for the capture notification’s Stop and Pause actions while you are in the game.",
                        style = body,
                    )
                    Text(
                        if (notificationsEnabled) "Status: Allowed" else "Status: Not allowed",
                        style = body,
                        color = if (notificationsEnabled) Color(0xFF7DDCA0) else MaterialTheme.colorScheme.error,
                    )
                }
                if (!notificationsEnabled) {
                    TextButton(onClick = onEnableNotifications) { Text("Enable") }
                }
            }

            // --- Floating overlay button ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating freeze button", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Needed to freeze/resume the game frame from a small button while in MLBB. " +
                            "Capture works fully without it.",
                        style = body,
                    )
                    Text(
                        if (overlayGranted) "Status: Allowed" else "Status: Not allowed",
                        style = body,
                        color = if (overlayGranted) Color(0xFF7DDCA0) else MaterialTheme.colorScheme.error,
                    )
                }
                Switch(
                    checked = floatingEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (Settings.canDrawOverlays(activity)) {
                                scope.launch {
                                    runCatching { preferences.setFloatingControlEnabled(true) }
                                        .onFailure { android.util.Log.w("DraftLens", "failed to persist floating control enable", it) }
                                    CaptureController.refreshOverlay(activity)
                                }
                            } else {
                                // The capture keeps working without the button; enable it once granted.
                                pendingEnable = true
                                activity.startActivity(
                                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                        .setData(Uri.parse("package:${activity.packageName}")),
                                )
                            }
                        } else {
                            scope.launch {
                                runCatching { preferences.setFloatingControlEnabled(false) }
                                    .onFailure { android.util.Log.w("DraftLens", "failed to persist floating control disable", it) }
                                CaptureController.refreshOverlay(activity)
                            }
                        }
                    },
                )
            }
            if (!overlayGranted) {
                TextButton(onClick = {
                    pendingEnable = true
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            .setData(Uri.parse("package:${activity.packageName}")),
                    )
                }) { Text("Open Android overlay settings") }
            }
        }
    }
}

private fun areCaptureNotificationsEnabled(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        return false
    }
    return try {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    } catch (e: Exception) {
        true
    }
}
