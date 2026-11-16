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
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.draftlens.MainActivity
import com.draftlens.R
import com.draftlens.models.CaptureStatus
import com.draftlens.vision.model.FrameRotation
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
    private val sampler = FrameSampler(
        FrameSampler.DEFAULT_INTERVAL_MS,
        timeSource = { SystemClock.elapsedRealtime() }
    )
    private val startupExecutor = CaptureStartupExecutor<MediaProjection>()
    private lateinit var pipeline: FramePipeline

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var displayListener: DisplayManager.DisplayListener? = null
    private var captureJob: Job? = null
    private var frameJob: Job? = null
    private var frozenJob: Job? = null
    private var foregroundPromoted = false
    /** Increments once per capture session; tags every structured log line. */
    private var sessionCounter = 0

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.i(TAG, "MediaProjection onStop received; session ended")
            terminateCaptureWithError("Screen capture stopped by system")
        }

        override fun onCapturedContentResize(width: Int, height: Int) {
            // The captured-content dimensions are authoritative; they are not the same as
            // the display-window metrics the session started with (that is the landscape bug).
            recreateDisplay(width, height, source = "captured-content-resize")
        }

        /**
         * API 34+: the window chosen in the chooser left or returned to the screen.
         * The projection stays valid: while hidden, images are acquired and closed but
         * never published, so DraftLens/shade/other apps can never replace the last
         * valid game frame. Returning to MLBB reopens publication unless a frame is pinned.
         */
        override fun onCapturedContentVisibilityChanged(isVisible: Boolean) {
            Log.i(TAG, "captured-content visibility changed: visible=$isVisible")
            if (CaptureStateHolder.state.value.status != CaptureStatus.CAPTURING) {
                Log.i(TAG, "ignoring visibility change: capture is not in CAPTURING state")
                return
            }
            pipeline.onContentVisibilityChanged(isVisible)
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
        if (intent == null) {
            Log.w(TAG, "Ignored null start intent; capture session is not restarted")
            return START_NOT_STICKY
        }
        return when (intent.action) {
            ACTION_START -> {
                if (CaptureStateHolder.state.value.isServiceStartBlocking) {
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

                if (data == null) {
                    cleanupAfterTermination("Missing screen capture consent data")
                    stopSelf()
                    return START_NOT_STICKY
                }

                CaptureStateHolder.setStatus(CaptureStatus.STARTING)
                if (!promoteToForegroundMediaProjection()) {
                    cleanupAfterTermination()
                    stopSelf()
                    return START_NOT_STICKY
                }
                startCapture(resultCode, data, intervalMs)
                START_NOT_STICKY
            }
            ACTION_STOP -> {
                cleanupAfterTermination(forceIdle = true)
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
            ACTION_FREEZE -> {
                // Never re-promotes, never restarts MediaProjection: no-op without an active pipeline.
                if (!CaptureFreeze.hasActivePipeline()) {
                    Log.i(TAG, "Freeze command ignored: no active capture session")
                    stopSelf() // do not linger for a stale command
                    return START_NOT_STICKY
                }
                when (val outcome = CaptureFreeze.freezeCurrentFrame()) {
                    is FreezeOutcome.Frozen ->
                        Log.i(TAG, "Freeze command honoured (${outcome.frame.width}x${outcome.frame.height})")
                    FreezeOutcome.AlreadyFrozen ->
                        Log.i(TAG, "Freeze command: a frame is already frozen; keeping it")
                    FreezeOutcome.NoFrame ->
                        Log.i(TAG, "Freeze command ignored: no frame captured yet")
                    FreezeOutcome.NoPipeline -> {
                        Log.i(TAG, "Freeze command ignored: no active capture session")
                        stopSelf() // do not linger for a stale command
                    }
                }
                START_NOT_STICKY
            }
            ACTION_RESUME_LIVE -> {
                CaptureFreeze.resumeLive()
                if (CaptureStateHolder.state.value.status != CaptureStatus.CAPTURING &&
                    CaptureStateHolder.state.value.status != CaptureStatus.PAUSED
                ) {
                    stopSelf() // stale action after capture stopped; nothing to resume into
                }
                START_NOT_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    private fun startCapture(resultCode: Int, data: Intent?, intervalMs: Long) {
        if (data == null) {
            cleanupAfterTermination("Missing screen capture consent data")
            stopSelf()
            return
        }

        sessionCounter += 1
        sampler.setIntervalMs(intervalMs)
        releaseCaptureResources(releaseForeground = false)

        captureJob = scope.launch {
            try {
                val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startupExecutor.execute(
                    promoteToForeground = {
                        if (!promoteToForegroundMediaProjection()) {
                            throw SecurityException("Foreground service promotion failed")
                        }
                    },
                    obtainProjection = {
                        mgr.getMediaProjection(resultCode, data)
                            ?: error("MediaProjectionManager returned null")
                    },
                    initializeProjection = { proj ->
                        projection = proj
                        proj.registerCallback(projectionCallback, null)

                        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                        val (captureWidth, captureHeight) = captureDimensions(wm)
                        val dpi = resources.configuration.densityDpi
                        val displayRotation = currentDisplayRotation()
                        Log.i(TAG, "session=$sessionCounter capture-start: dimensions=${captureWidth}x${captureHeight} dpi=$dpi rotation=$displayRotation source=windowMetrics")

                        pipeline.start(captureWidth, captureHeight, dpi, displayRotation)
                        CaptureFreeze.attachPipeline(pipeline)
                        createVirtualDisplay(proj, captureWidth, captureHeight, dpi)
                        registerDisplayListener()
                        CaptureStateHolder.beginCapture(captureWidth, captureHeight, dpi)
                        collectFrames()
                    }
                )
            } catch (e: SecurityException) {
                Log.e(TAG, "Capture session failed before or during projection setup", e)
                terminateCaptureWithError("Permission denied: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Capture session startup failed", e)
                terminateCaptureWithError("Failed to start capture: ${e.message}")
            }
        }
    }

    private fun captureDimensions(wm: WindowManager): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val point = Point()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getSize(point)
        return point.x to point.y
    }

    /** Device orientation at acquisition; recorded separately from pixel correction. */
    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): FrameRotation {
        return try {
            val display = (getSystemService(Context.DISPLAY_SERVICE) as DisplayManager)
                .getDisplay(Display.DEFAULT_DISPLAY)
            if (display == null) {
                Log.w(TAG, "Default display unavailable; assuming display rotation 0")
                FrameRotation.ROTATION_0
            } else {
                display.rotation.toFrameRotation()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Display rotation unavailable; assuming display rotation 0", e)
            FrameRotation.ROTATION_0
        }
    }

    private fun Int.toFrameRotation(): FrameRotation = when (this) {
        Surface.ROTATION_90 -> FrameRotation.ROTATION_90
        Surface.ROTATION_180 -> FrameRotation.ROTATION_180
        Surface.ROTATION_270 -> FrameRotation.ROTATION_270
        else -> FrameRotation.ROTATION_0
    }

    private fun promoteToForegroundMediaProjection(): Boolean {
        if (foregroundPromoted) {
            Log.i(TAG, "Foreground service already promoted with mediaProjection type (id=$NOTIFICATION_ID)")
            return true
        }

        createNotificationChannel()
        val notification = buildNotification()
        return try {
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
            foregroundPromoted = true
            Log.i(TAG, "Foreground service promoted with mediaProjection type (id=$NOTIFICATION_ID)")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Foreground promotion failed; getMediaProjection() will not be called", e)
            CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Foreground service promotion failed: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Foreground promotion failed; getMediaProjection() will not be called", e)
            CaptureStateHolder.setStatus(CaptureStatus.ERROR, "Foreground service promotion failed: ${e.message}")
            false
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
        frozenJob?.cancel()
        frozenJob = scope.launch {
            CaptureStateHolder.frozenFrame.collectLatest { frozen ->
                updateNotificationState(frozen != null)
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
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface,
            null,
            null
        ) ?: error("createVirtualDisplay() returned null")
        Log.i(TAG, "session=$sessionCounter virtual-display created: ${width}x${height} dpi=$densityDpi (auto-mirror of the default display)")
    }

    /**
     * Applies new captured-content dimensions to the VirtualDisplay/ImageReader pair.
     * [source] identifies which signal requested it ("captured-content-resize" — the
     * authoritative callback, or "display-listener" — the rotation/size fallback); both
     * funnel here so the handling is identical and idempotent.
     */
    private fun recreateDisplay(width: Int, height: Int, source: String) {
        val densityDpi = resources.configuration.densityDpi
        val display = virtualDisplay ?: run {
            Log.e(TAG, "session=$sessionCounter resize requested ($source) but VirtualDisplay is missing")
            failProjectionSession("VirtualDisplay disappeared during resize", null)
            return
        }
        if (CaptureStateHolder.state.value.status != CaptureStatus.CAPTURING) {
            Log.i(TAG, "session=$sessionCounter ignoring $source ${width}x${height}: capture is not in CAPTURING state")
            return
        }

        val displayRotation = currentDisplayRotation()
        val currentWidth = CaptureStateHolder.state.value.displayWidth
        val currentHeight = CaptureStateHolder.state.value.displayHeight
        Log.i(TAG, "session=$sessionCounter $source: ${currentWidth}x${currentHeight} -> ${width}x${height} dpi=$densityDpi rotation=$displayRotation")
        val change = DisplayChangePolicy.classify(
            currentWidth,
            currentHeight,
            width,
            height,
        )
        when (change) {
            is DisplayChange.RotationOnly -> {
                // The capture size is unchanged: only the rotation metadata needs updating.
                updateDisplayRotation(displayRotation)
                Log.i(TAG, "session=$sessionCounter rotation-only update: rotation=$displayRotation without resizing")
            }

            is DisplayChange.Resize -> {
                try {
                    val replacement = pipeline.prepareResize(width, height, densityDpi, displayRotation)
                    if (replacement == null) {
                        Log.i(TAG, "session=$sessionCounter resize no-op: dimensions already match")
                        return
                    }
                    // Detach first: the VirtualDisplay must not retain a released Surface.
                    Log.i(TAG, "session=$sessionCounter surface-detach from VirtualDisplay (previous generation)")
                    display.setSurface(null)
                    Log.i(TAG, "session=$sessionCounter releasing previous ImageReader/surface/thread")
                    replacement.closePrevious()
                    Log.i(TAG, "session=$sessionCounter surface-attach new reader generation to VirtualDisplay")
                    display.setSurface(replacement.surface)
                    display.resize(width, height, densityDpi)
                    CaptureStateHolder.updateDisplay(width, height, densityDpi)
                    Log.i(TAG, "session=$sessionCounter virtual-display resized: ${width}x${height} dpi=$densityDpi generation=${pipeline.peekGeneration()}")
                } catch (e: Exception) {
                    failProjectionSession("Failed to resize VirtualDisplay", e)
                }
            }
        }
    }

    /**
     * Records a new device orientation on the pipeline, independently of any resize.
     * The pixel correction stays [FrameRotation.ROTATION_0]: MediaProjection buffers are
     * already display-oriented, so the rotation is metadata, never a transform.
     */
    private fun updateDisplayRotation(rotation: FrameRotation) {
        pipeline.updateDisplayRotation(rotation)
    }

    /**
     * Watches the default display so an orientation change is picked up even when the
     * captured content keeps its size and MediaProjection reports no resize.
     *
     * Sizing itself is re-checked here as well: on API levels below the captured-content
     * callback (or whenever a rotation re-lays the display), the VirtualDisplay would
     * otherwise keep the session-start dimensions forever (the whole-screen landscape
     * bug). Both paths funnel into [recreateDisplay], which is idempotent.
     */
    private fun registerDisplayListener() {
        val manager = getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                if (displayId == Display.DEFAULT_DISPLAY) {
                    Log.i(TAG, "session=$sessionCounter default-display changed")
                    updateDisplayRotation(currentDisplayRotation())
                    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                    val (width, height) = captureDimensions(wm)
                    val current = CaptureStateHolder.state.value
                    if (width != current.displayWidth || height != current.displayHeight) {
                        Log.i(TAG, "session=$sessionCounter display metrics changed ${current.displayWidth}x${current.displayHeight} -> ${width}x${height}; resyncing capture size")
                        recreateDisplay(width, height, source = "display-listener")
                    }
                }
            }
        }
        try {
            manager.registerDisplayListener(listener, null)
            displayListener = listener
            Log.i(TAG, "session=$sessionCounter display listener registered")
        } catch (e: Exception) {
            Log.w(TAG, "session=$sessionCounter display listener registration failed", e)
        }
    }

    private fun unregisterDisplayListener() {
        val listener = displayListener ?: return
        displayListener = null
        try {
            (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                ?.unregisterDisplayListener(listener)
            Log.i(TAG, "session=$sessionCounter display listener unregistered")
        } catch (e: Exception) {
            Log.w(TAG, "session=$sessionCounter display listener unregister failed", e)
        }
    }

    private fun terminateCaptureWithError(errorMessage: String) {
        cleanupAfterTermination(errorMessage)
        stopSelf()
    }

    private fun failProjectionSession(message: String, error: Exception?) {
        Log.e(TAG, message, error)
        terminateCaptureWithError(error?.let { "$message: ${it.message}" } ?: message)
    }

    private fun cleanupAfterTermination(errorMessage: String? = null, forceIdle: Boolean = false) {
        CaptureStateHolder.terminateSession(errorMessage, forceIdle)
        releaseCaptureResources(releaseForeground = true)
    }

    private fun releaseCaptureResources(releaseForeground: Boolean) {
        Log.i(TAG, "session=$sessionCounter release: captureJob/frameJob/frozenJob cancelled=${captureJob != null || frameJob != null || frozenJob != null}")
        captureJob?.cancel()
        captureJob = null
        frameJob?.cancel()
        frameJob = null
        frozenJob?.cancel()
        frozenJob = null
        try { virtualDisplay?.release() } catch (e: Exception) { Log.w(TAG, "session=$sessionCounter VirtualDisplay release failed", e) }
        if (virtualDisplay != null) Log.i(TAG, "session=$sessionCounter virtual-display released")
        virtualDisplay = null
        unregisterDisplayListener()
        try {
            projection?.unregisterCallback(projectionCallback)
            projection?.stop()
            Log.i(TAG, "session=$sessionCounter media-projection released")
        } catch (e: Exception) {
            Log.w(TAG, "session=$sessionCounter MediaProjection stop failed", e)
        }
        projection = null
        if (::pipeline.isInitialized) {
            CaptureFreeze.detachPipeline(pipeline)
        }
        try { pipeline.stop() } catch (e: Exception) { Log.w(TAG, "session=$sessionCounter Pipeline stop failed", e) }
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
            foregroundPromoted = false
        }
    }

    fun stopCapture() {
        cleanupAfterTermination(forceIdle = true)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // MediaProjection tokens cannot survive process death. Do not recreate them from a null intent.
        Log.i(TAG, "Activity task removed; capture session will not be restarted with stale consent")
        cleanupAfterTermination()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        cleanupAfterTermination()
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

    /** Re-posts the ongoing notification with the current LIVE/FROZEN state and its action. */
    private fun updateNotificationState(frozen: Boolean) {
        if (!foregroundPromoted) {
            Log.d(TAG, "Skipping notification update: service is not promoted")
            return
        }
        if (!areNotificationsEnabled()) {
            Log.w(TAG, "Notifications are disabled; the capture actions are not reachable from the shade")
            return
        }
        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIFICATION_ID, buildNotification(frozen))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update the capture notification", e)
        }
    }

    /** Android 13+ runtime-granted; API 24+ areNotificationsEnabled when present, safe otherwise. */
    private fun areNotificationsEnabled(): Boolean = runCatching {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .areNotificationsEnabled()
    }.getOrDefault(true)

    private fun buildNotification(frozen: Boolean = CaptureStateHolder.frozenFrame.value != null): Notification {
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
        val freezeIntent = PendingIntent.getService(
            this, 2,
            Intent(this, CaptureSessionService::class.java).setAction(ACTION_FREEZE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val resumeCaptureIntent = PendingIntent.getService(
            this, 3,
            Intent(this, CaptureSessionService::class.java).setAction(ACTION_RESUME_LIVE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val subText = if (frozen) getString(R.string.capture_frozen_subtext) else getString(R.string.capture_live_subtext)
        val text = if (frozen) getString(R.string.capture_notification_frozen_text) else getString(R.string.capture_notification_text)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(text)
            .setSubText(subText)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .setShowWhen(false)
        if (frozen) {
            builder.addAction(android.R.drawable.ic_media_play, getString(R.string.capture_resume_capture_action), resumeCaptureIntent)
        } else {
            builder.addAction(android.R.drawable.ic_menu_camera, getString(R.string.capture_freeze_action), freezeIntent)
        }
        return builder
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.capture_stop_action), stopIntent)
            .build()
    }

    companion object {
        const val ACTION_START = "com.draftlens.action.START_CAPTURE"
        const val ACTION_STOP = "com.draftlens.action.STOP_CAPTURE"
        const val ACTION_PAUSE = "com.draftlens.action.PAUSE_CAPTURE"
        const val ACTION_RESUME = "com.draftlens.action.RESUME_CAPTURE"
        const val ACTION_FREEZE = "com.draftlens.action.FREEZE_CAPTURE"
        const val ACTION_RESUME_LIVE = "com.draftlens.action.RESUME_LIVE_CAPTURE"
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
