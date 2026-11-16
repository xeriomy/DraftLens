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
import com.draftlens.capture.CaptureStateHolder
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

    // Notification permission: the capture notification carries the "Freeze current frame"
    // action, so without it the MLBB freeze workflow is unreachable.
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
        Text("DraftLens — Capture Debug (Sprint 0)", color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text("Detection: Not implemented in Sprint 0", color = Color.Gray)

        MobileLegendsCaptureGuide(
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

        FloatingControlCard(activity = activity, preferences = preferences)

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

/** Guidance for capturing Mobile Legends with the two system capture modes. */
@Composable
private fun MobileLegendsCaptureGuide(
    notificationsEnabled: Boolean,
    onEnableNotifications: () -> Unit,
) {
    val body = MaterialTheme.typography.bodySmall
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Capturing Mobile Legends: Bang Bang", style = MaterialTheme.typography.titleMedium)
            Text("1. Tap Start Capture.", style = body)
            Text(
                "2. When Android asks what to share, prefer Single app / app window and " +
                    "choose Mobile Legends: Bang Bang (Android 14+ partial capture). DraftLens then " +
                    "only sees the game: live frames while MLBB is on screen, and the last valid " +
                    "game frame whenever MLBB is hidden — never the shade, DraftLens or the system UI.",
                style = body,
            )
            Text(
                "   Not offered on your Android? Choose Entire screen / full device display. That " +
                    "fallback captures everything on the display — including the notification shade, " +
                    "system UI and other apps. DraftLens cannot promise notification-free frames on " +
                    "this path; the in-app guidance and freeze behavior stay the same.",
                style = body,
            )
            Text(
                "3. Open Mobile Legends and wait for the draft screen.",
                style = body,
            )
            Text(
                "4. While in the game, tap the circular DraftLens button (enable it in " +
                    "“Floating control” below) to freeze the game frame — tap again to resume. " +
                    "Long-press and drag to move it; it snaps to a screen edge.",
                style = body,
            )
            Text(
                "   Prefer not to grant the overlay permission? Capture still works: tap “Freeze " +
                    "current frame” on the capture notification (needs notifications enabled), or " +
                    "use Freeze/Resume inside DraftLens.",
                style = body,
            )
            Text(
                "5. Return to DraftLens. Vision Lab shows the pinned game frame. Resume there, or " +
                    "tap the circle again in the game.",
                style = body,
            )
            if (!notificationsEnabled) {
                Text(
                    "Notifications are disabled: the “Freeze current frame” notification action is not reachable, but the floating control and in-app Freeze/Resume still work.",
                    style = body,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onEnableNotifications) { Text("Enable notifications") }
            }
        }
    }
}

/** The one Sprint 1C overlay: a circular freeze/resume button, opt-in via the toggle. */
@Composable
private fun FloatingControlCard(
    activity: Activity,
    preferences: AppPreferencesRepository,
) {
    val body = MaterialTheme.typography.bodySmall
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(activity)) }
    var pendingEnable by remember { mutableStateOf(false) }
    val floatingEnabled by preferences.floatingControlEnabled.collectAsState(initial = false)
    val frozen by CaptureStateHolder.frozenFrame.collectAsState()
    val scope = rememberCoroutineScope()

    // Returning from the Android overlay-permission settings screen completes a pending enable.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        overlayGranted = Settings.canDrawOverlays(activity)
        if (pendingEnable && overlayGranted) {
            pendingEnable = false
            scope.launch {
                preferences.setFloatingControlEnabled(true)
                CaptureController.refreshOverlay(activity)
            }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Floating control", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "One circular button shown while capture is running. Tap toggles Freeze/Resume; " +
                            "long-press and drag to move it — it snaps to a screen edge. " +
                            "Green = live, orange = frozen. It never opens a panel.",
                        style = body,
                    )
                }
                Switch(
                    checked = floatingEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (Settings.canDrawOverlays(activity)) {
                                scope.launch {
                                    preferences.setFloatingControlEnabled(true)
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
                                preferences.setFloatingControlEnabled(false)
                                CaptureController.refreshOverlay(activity)
                            }
                        }
                    },
                )
            }
            when {
                !overlayGranted -> Text(
                    "Android has not allowed drawing over other apps. Capture and the in-app " +
                        "Freeze/Resume keep working; grant the permission here and the button " +
                        "appears during capture.",
                    style = body,
                    color = MaterialTheme.colorScheme.error,
                )

                floatingEnabled -> Text(
                    "Shown while capture is running. State: ${if (frozen != null) "FROZEN (orange)" else "LIVE (green)"}.",
                    style = body,
                )

                else -> Text("Hidden. Toggle on to show it during capture.", style = body)
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
