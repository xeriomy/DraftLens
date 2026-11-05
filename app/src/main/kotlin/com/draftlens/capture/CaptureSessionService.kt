package com.draftlens.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.draftlens.MainActivity
import com.draftlens.R
import com.draftlens.models.CaptureStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Owns MediaProjection + VirtualDisplay + FramePipeline.
 * Contract:
 *  - Service foregrounds itself before getMediaProjection() (Android 14+).
 *  - Consent is only accepted through a result issued after an Activity consent dialog.
 *  - Resources are released on user stop, process death, or projection revocation.
 */
class CaptureSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sampler = FrameSampler(FrameSampler.DEFAULT_INTERVAL_MS)
    private lateinit var pipeline: FramePipeline

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureJob: Job? = null
    private var frameJob: Job? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Screen capture stopped by system")
            teardown()
        }

        override fun onCapturedContentResize(width: Int, height: Int, densityDpi: Int) {
            recreateDisplay(width, height, densityDpi)
        }
    }

    override fun onCreate() {
        super.onCreate()
        pipeline = FramePipeline(sampler) {
            CaptureStateHolder.state.value.status == CaptureStatus.CAPTURING
        }
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(EXTRA_DATA)
                }
                val intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, FrameSampler.DEFAULT_INTERVAL_MS)
                startForegroundServiceInternal()
                startCapture(resultCode, data, intervalMs)
                START_STICKY
            }
            ACTION_STOP -> {
                CaptureStateHolder.setStatus(CaptureStatus.STOPPING)
                teardown()
                CaptureStateHolder.reset()
                stopSelf()
                START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                CaptureStateHolder.setPaused(true)
                START_NOT_STICKY
            }
            ACTION_RESUME -> {
                sampler.reset()
                CaptureStateHolder.setPaused(false)
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    private fun startForegroundServiceInternal() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCapture(resultCode: Int, data: Intent?, intervalMs: Long) {
        if (data == null) {
            CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Missing screen capture consent data")
            return
        }
        if (CaptureStateHolder.state.value.status == CaptureStatus.CAPTURING) return
        CaptureStateHolder.setStatus(CaptureStatus.STARTING)
        teardown()

        sampler.setIntervalMs(intervalMs)
        captureJob = scope.launch {
            try {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val proj = mgr.getMediaProjection(resultCode, data)
                projection = proj
                proj.registerCallback(projectionCallback, null)

                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                val size = Point()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getSize(size)
                val dpi = resources.displayMetrics.densityDpi

                pipeline.start(size.x, size.y, dpi)
                createVirtualDisplay(proj, size.x, size.y, dpi)
                CaptureStateHolder.beginCapture(size.x, size.y, dpi)
                collectFrames()
            } catch (e: SecurityException) {
                CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Permission denied: ${e.message}")
                teardown()
            } catch (e: Exception) {
                CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Failed to start capture: ${e.message}")
                teardown()
            }
        }
    }

    private fun collectFrames() {
        frameJob?.cancel()
        frameJob = scope.launch {
            pipeline.latestFrame.collectLatest { frame ->
                if (frame != null && CaptureStateHolder.state.value.status == CaptureStatus.CAPTURING) {
                    CaptureStateHolder.recordFrame(frame)
                }
            }
        }
    }

    private fun createVirtualDisplay(proj: MediaProjection, width: Int, height: Int, densityDpi: Int) {
        val surface = pipeline.surface()
        if (surface == null) {
            CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Frame surface unavailable")
            teardown()
            return
        }
        virtualDisplay?.release()
        virtualDisplay = proj.createVirtualDisplay(
            "DraftLensCapture-${Calendar.getInstance().timeInMillis}",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            surface,
            null,
            null
        )
    }

    private fun recreateDisplay(width: Int, height: Int, densityDpi: Int) {
        if (CaptureStateHolder.state.value.status != CaptureStatus.CAPTURING) return
        if (width == CaptureStateHolder.state.value.displayWidth &&
            height == CaptureStateHolder.state.value.displayHeight
        ) {
            return
        }
        pipeline.resize(width, height, densityDpi)
        projection?.let { createVirtualDisplay(it, width, height, densityDpi) }
        CaptureStateHolder.updateDisplay(width, height, densityDpi)
    }

    private fun teardown() {
        captureJob?.cancel()
        captureJob = null
        frameJob?.cancel()
        frameJob = null
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try {
            projection?.unregisterCallback(projectionCallback)
            projection?.stop()
        } catch (_: Exception) {}
        projection = null
        try { pipeline.stop() } catch (_: Exception) {}
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (_: Exception) {}
    }

    fun stopCapture() {
        CaptureStateHolder.setStatus(CaptureStatus.STOPPING)
        teardown()
        CaptureStateHolder.reset()
        stopSelf()
    }

    override fun onDestroy() {
        teardown()
        CaptureStateHolder.setStatus(CaptureStatus.IDLE)
        super.onDestroy()
    }

    // ---- Notification ----
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.capture_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.capture_notification_text) }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, CaptureSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(tapIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.capture_stop_action), stopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ACTION_START = "com.draftlens.action.START_CAPTURE"
        const val ACTION_STOP = "com.draftlens.action.STOP_CAPTURE"
        const val ACTION_PAUSE = "com.draftlens.action.PAUSE_CAPTURE"
        const val ACTION_RESUME = "com.draftlens.action.RESUME_CAPTURE"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"
        const val EXTRA_INTERVAL_MS = "extra_interval_ms"
        private const val CHANNEL_ID = "draftlens_capture"
        private const val NOTIFICATION_ID = 1001

        fun startIntent(context: Context, resultCode: Int, data: Intent, intervalMs: Long): Intent =
            Intent(context, CaptureSessionService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
                .putExtra(EXTRA_INTERVAL_MS, intervalMs)

        fun stopIntent(context: Context): Intent =
            Intent(context, CaptureSessionService::class.java).setAction(ACTION_STOP)

        fun pauseIntent(context: Context): Intent =
            Intent(context, CaptureSessionService::class.java).setAction(ACTION_PAUSE)

        fun resumeIntent(context: Context): Intent =
            Intent(context, CaptureSessionService::class.java).setAction(ACTION_RESUME)
    }
}
