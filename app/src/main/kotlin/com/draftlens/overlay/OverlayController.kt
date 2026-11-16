package com.draftlens.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.draftlens.capture.CaptureFreeze
import com.draftlens.capture.CapturePublishState
import com.draftlens.capture.CaptureStateHolder
import com.draftlens.capture.FreezeOutcome
import com.draftlens.capture.FloatingTapOutcome
import com.draftlens.capture.floatingTapFor
import com.draftlens.capture.publishStateBlocksPublication
import com.draftlens.capture.publishStateFor
import com.draftlens.R
import com.draftlens.datastore.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns the WindowManager side of the single circular floating control. Attached only while
 * a capture session is running (by [CaptureSessionService][com.draftlens.capture.CaptureSessionService]), always removed on teardown. Holds no
 * capture state of its own; the tap toggles the shared pause/freeze gate and the glyph follows
 * the same [CapturePublishState][com.draftlens.capture.CapturePublishState] the capture notification shows.
 * Rendering, touch handling and visibility are independent of the captured-frame pipeline:
 * hiding the captured window never removes or pauses the button.
 *
 * The window is exactly button-sized and non-focusable: touches outside the circle go to
 * the app underneath, and the control never takes keyboard focus from the game.
 */
class OverlayController(
    private val context: Context,
    private val preferences: AppPreferencesRepository,
    private val scope: CoroutineScope,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var view: FloatingControlView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val sizePx = (context.resources.displayMetrics.density * SIZE_DP).toInt()

    /** Current display size; updated on rotation so drag/snap math never uses stale metrics. */
    @Volatile
    private var screenWidthPx = 0

    @Volatile
    private var screenHeightPx = 0

    @Volatile
    private var currentNormalized = OverlayPosition.Normalized(x = 1f, y = DEFAULT_Y_FRACTION)

    val isAttached: Boolean get() = view != null

    /**
     * Generation counter guarding pending attaches: every show() and every detach() bumps it,
     * and attachView() refuses to run for a stale generation. Without this, a show() whose
     * DataStore read settles after a teardown would add an orphaned always-on-top window
     * nobody can dismiss (M1 review finding).
     */
    private val attachLock = Any()
    private var attachGeneration = 0

    /**
     * Adds the overlay after re-checking the user preference and the Android overlay
     * permission — capture start calls this unconditionally, so the gate lives here.
     * Main-thread attachment; the permission comes from the caller's toggle flow.
     */
    fun show(screenWidthPx: Int, screenHeightPx: Int) {
        if (view != null) {
            Log.i(TAG, "overlay already attached; skipping show()")
            return
        }
        val requestId = synchronized(attachLock) { ++attachGeneration }
        scope.launch {
            val enabled = runCatching { preferences.floatingControlEnabled.first() }.getOrElse { false }
            val canDraw = runCatching { android.provider.Settings.canDrawOverlays(context) }.getOrDefault(false)
            if (!OverlayEnablement.shouldAttachOverlay(enabled, canDraw, captureRunning = true)) {
                Log.i(TAG, "overlay not attached (enabled=$enabled canDrawOverlays=$canDraw)")
                return@launch
            }
            val persisted = runCatching { preferences.floatingPosition.first() }.getOrNull()
            val normalized = persisted ?: currentNormalized
            currentNormalized = normalized
            mainHandler.post { attachView(screenWidthPx, screenHeightPx, normalized, requestId) }
        }
    }

    private fun attachView(screenWidthPx: Int, screenHeightPx: Int, normalized: OverlayPosition.Normalized, requestId: Int) {
        synchronized(attachLock) {
            if (requestId != attachGeneration) {
                Log.i(TAG, "discarding stale overlay attach (generation $requestId, now $attachGeneration)")
                return
            }
            if (view != null) return // a configure change raced the show() coroutine
            this.screenWidthPx = screenWidthPx
            this.screenHeightPx = screenHeightPx

        val px = OverlayPosition.normalizedToPixels(normalized, screenWidthPx, screenHeightPx, sizePx)
        val button = FloatingControlView(
            context,
            onTap = { toggleFreeze() },
            onDragStart = { rawX, rawY -> beginDrag(rawX, rawY) },
            onDragMove = { rawX, rawY -> dragTo(rawX, rawY) },
            onDragEnd = { endDrag() },
        )
        button.setNotLive(publishStateBlocksPublication(currentPublishStateForOverlay()))

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = px.left
            y = px.top
        }

        try {
            windowManager.addView(button, params)
            view = button
            layoutParams = params
            Log.i(TAG, "overlay attached: sizePx=$sizePx x=${params.x} y=${params.y}")
        } catch (e: Exception) {
            // Missing overlay permission or a bad window state: capture and the in-app
            // Freeze/Resume keep working; never crash the capture session over the control.
            Log.w(TAG, "overlay attach failed; capture continues without the floating control", e)
            view = null
            layoutParams = null
        }
        }
    }

    /** Re-derives the pixel position from the normalized one after a resize/rotation. */
    fun onDisplayMetricsChanged(screenWidthPx: Int, screenHeightPx: Int) {
        this.screenWidthPx = screenWidthPx
        this.screenHeightPx = screenHeightPx
        val params = layoutParams ?: return
        val button = view ?: return
        val px = OverlayPosition.normalizedToPixels(currentNormalized, screenWidthPx, screenHeightPx, sizePx)
        params.x = px.left
        params.y = px.top
        try {
            windowManager.updateViewLayout(button, params)
        } catch (e: Exception) {
            Log.w(TAG, "overlay reposition failed; detaching", e)
            detach()
        }
    }

    fun setNotLive(notLive: Boolean) {
        mainHandler.post { view?.setNotLive(notLive) }
    }

    /** Removes the overlay. Safe from any thread and when not attached; invalidates pending attaches. */
    fun detach() {
        synchronized(attachLock) { attachGeneration++ } // invalidate any in-flight show()
        val detachBlock = Runnable {
            val button = view ?: return@Runnable
            try {
                windowManager.removeView(button)
                Log.i(TAG, "overlay detached")
            } catch (e: Exception) {
                Log.w(TAG, "overlay detach failed", e)
            }
            view = null
            layoutParams = null
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            detachBlock.run()
        } else {
            mainHandler.post(detachBlock)
        }
    }

    private var grabOffsetX = 0f
    private var grabOffsetY = 0f

    private fun beginDrag(rawX: Float, rawY: Float) {
        val params = layoutParams ?: return
        grabOffsetX = rawX - params.x
        grabOffsetY = rawY - params.y
    }

    // shortcut: clamps against the metrics snapshot from the last rotation signal, so a drag that
    // spans exactly the rotation event can briefly clamp to the old orientation; upgrade by reading
    // Metrics in dragTo() if MLBB-in-multi-window drags become real.
    private fun dragTo(rawX: Float, rawY: Float) {
        val params = layoutParams ?: return
        val button = view ?: return
        val clamped = OverlayPosition.clampToScreen(
            (rawX - grabOffsetX).toInt(),
            (rawY - grabOffsetY).toInt(),
            screenWidthPx,
            screenHeightPx,
            sizePx,
        )
        params.x = clamped.left
        params.y = clamped.top
        runCatching { windowManager.updateViewLayout(button, params) }
    }

    private fun endDrag() {
        val params = layoutParams ?: return
        val button = view ?: return
        val snapped = OverlayPosition.snapToNearestEdge(params.x, params.y, screenWidthPx, screenHeightPx, sizePx)
        params.x = snapped.left
        params.y = snapped.top
        runCatching { windowManager.updateViewLayout(button, params) }

        val normalized = OverlayPosition.pixelsToNormalized(snapped.left, snapped.top, screenWidthPx, screenHeightPx, sizePx)
        currentNormalized = normalized
        scope.launch {
            runCatching { preferences.setFloatingPosition(normalized) }
                .onFailure { Log.w(TAG, "failed to persist overlay position", it) }
        }
        Log.i(TAG, "overlay snapped to ${snapped.edge} (normalized ${normalized.x}, ${normalized.y})")
    }

    private fun currentPublishStateForOverlay(): CapturePublishState =
        publishStateFor(pinInPlace = CaptureStateHolder.frozenFrame.value != null, isPaused = CaptureStateHolder.state.value.isPaused)

    private fun toggleFreeze() {
        // Unified with the notification and in-app controls: resume if live publication is
        // already blocked (pin or pause), otherwise freeze (pin). Never creates a session.
        when (floatingTapFor(currentPublishStateForOverlay())) {
            FloatingTapOutcome.RESUME -> {
                CaptureFreeze.resumeLive()
                CaptureStateHolder.setPaused(false)
            }
            FloatingTapOutcome.FREEZE -> {
                val outcome = CaptureFreeze.freezeCurrentFrame()
                if (outcome == FreezeOutcome.NoFrame) {
                    showTransientMessage(context.getString(R.string.floating_control_no_frame_yet))
                }
            }
        }
        // The service collects the shared state and repaints the glyph through setNotLive().
    }

    private fun showTransientMessage(message: String) {
        mainHandler.post {
            android.widget.Toast.makeText(context.applicationContext, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val TAG = "OverlayController"
        const val SIZE_DP = 52 // within the 48–56dp visual range
        const val DEFAULT_Y_FRACTION = 0.18f // upper area, away from the central draft region
    }
}
