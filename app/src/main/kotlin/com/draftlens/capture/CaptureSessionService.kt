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
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
 *  1. Activity obtains consent only after the user grants the system dialog.
 *  2. This service promotes itself to a typed foreground service before getMediaProjection().
 *  3. Resources are released on user stop, process death, or projection revocation.
 */
class CaptureSessionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sampler = FrameSampler(FrameSampler.DEFAULT_INTERVAL_MS)
    private val startupExecutor = CaptureStartupExecutor<MediaProjection>()
    private lateinit var pipeline: FramePipeline

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var captureJob: Job? = null
    private var frameJob: Job? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection onStop received; session ended")
            CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Screen capture stopped by system")
            releaseCaptureResources(releaseForeground = true)
            stopSelf()
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            recreateDisplay(width, height, resources.displayMetrics.densityDpi)
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
                if (CaptureStateHolder.state.value.isStartBlocking) {
                    Log.i(TAG, "Ignoring duplicate START while capture session is ${CaptureStateHolder.state.value.status}")
                    return START_NOT_STICKY
                }

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(EXTRA_DATA)
                }
                val intervalMs = intent.getLongExtra(EXTRA_INTERVAL_MS, FrameSampler.DEFAULT_INTERVAL_MS)

                CaptureStateHolder.setStatus(CaptureStatus.STARTING)
                startCapture(resultCode, data, intervalMs)
                START_STICKY
            }
            ACTION_STOP -> {
                releaseCaptureResources(releaseForeground = true)
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

    private fun startCapture(resultCode: Int, data: Intent?, intervalMs: Long) {
        if (data == null) {
            CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Missing screen capture consent data")
            stopSelf()
            return
        }

        sampler.setIntervalMs(intervalMs)
        releaseCaptureResources(releaseForeground = false)

        captureJob = scope.launch {
            try {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startupExecutor.execute(
                    promoteToForeground = {
                        promoteToForegroundMediaProjection()
                    },
                    obtainProjection = {
                        mgr.getMediaProjection(resultCode, data)
                            ?: error("MediaProjectionManager returned null")
                    },
                    initializeProjection = { proj ->
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
                    }
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Capture session failed before or during projection setup", e)
                CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Permission denied: ${e.message}")
                releaseCaptureResources(releaseForeground = true)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Capture session startup failed", e)
                CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Failed to start capture: ${e.message}")
                releaseCaptureResources(releaseForeground = true)
                stopSelf()
            }
        }
    }

    private fun promoteToForegroundMediaProjection() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            Log.i(TAG, "Foreground service promoted with mediaProjection type (id=$NOTIFICATION_ID)")
        } catch (e: SecurityException) {
            Log.e(TAG, "Foreground promotion failed; getMediaProjection() will not be called", e)
            throw e
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

    private fun createVirtualDisplay(
        proj: MediaProjection,
        width: Int,
        height: Int,
        densityDpi: Int
    ) {
        val surface = pipeline.surface() ?: error("Frame surface was not created")
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
        ) ?: error("createVirtualDisplay() returned null")
        Log.i(TAG, "VirtualDisplay created (${width}x${height}, ${densityDpi}dpi)")
    }

    private fun recreateDisplay(width: Int, height: Int, densityDpi: Int) {
        val display = virtualDisplay ?: run {
            Log.e(TAG, "Resize requested but VirtualDisplay is missing")
            failProjectionSession("VirtualDisplay disappeared during resize", null)
            return
        }
        if (CaptureStateHolder.state.value.status != CaptureStatus.CAPTURING) return
        if (width == CaptureStateHolder.state.value.displayWidth &&
            height == CaptureStateHolder.state.value.displayHeight
        ) {
            return
        }

        try {
            val replacement = pipeline.prepareResize(width, height, densityDpi) ?: return
            // Caller owns cleanup after detach; VirtualDisplay must not retain a released Surface.
            display.setSurface(null)
            replacement.closePrevious()
            display.setSurface(replacement.surface)
            display.resize(width, height, densityDpi)
            CaptureStateHolder.updateDisplay(width, height, densityDpi)
            Log.i(TAG, "VirtualDisplay resized (${width}x${height}, ${densityDpi}dpi)")
        } catch (e: Exception) {
            failProjectionSession("Failed to resize VirtualDisplay", e)
        }
    }

    private fun failProjectionSession(message: String, error: Exception?) {
        Log.e(TAG, message, error)
        CaptureStateHolder.setStatus(CaptureStatus.ERROR, error?.let { "$message: ${it.message}" } ?: message)
        releaseCaptureResources(releaseForeground = true)
        stopSelf()
    }

    private fun releaseCaptureResources(releaseForeground: Boolean) {
        captureJob?.cancel()
        captureJob = null
        frameJob?.cancel()
        frameJob = null
        try { virtualDisplay?.release() } catch (e: Exception) { Log.w(TAG, "VirtualDisplay release failed", e) }
        virtualDisplay = null
        try {
            projection?.unregisterCallback(projectionCallback)
            projection?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "MediaProjection stop failed", e)
        }
        projection = null
        try { pipeline.stop() } catch (e: Exception) { Log.w(TAG, "Pipeline stop failed", e) }
        if (releaseForeground) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stop foreground failed", e)
            }
        }
    }

    fun stopCapture() {
        releaseCaptureResources(releaseForeground = true)
        CaptureStateHolder.reset()
        stopSelf()
    }

    override fun onDestroy() {
        releaseCaptureResources(releaseForeground = true)
        if (CaptureStateHolder.state.value.status != CaptureStatus.ERROR) {
            CaptureStateHolder.setStatus(CaptureStatus.IDLE)
        }
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
        private const val TAG = "CaptureSessionService"

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
