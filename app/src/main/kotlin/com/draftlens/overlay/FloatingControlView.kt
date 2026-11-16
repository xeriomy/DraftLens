package com.draftlens.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.HapticFeedbackConstants
import android.widget.ImageView
import android.widget.FrameLayout
import com.draftlens.R

/**
 * The one and only floating UI for Sprint 1C: a circular freeze/resume button.
 *
 * Gesture contract (deliberate, so game taps near a draft slot are never eaten):
 * - TAP (down+up inside the button, short): toggle freeze.
 * - Long-press (400 ms hold) arms drag mode; moving only after the hold drags.
 * - Moving before the long-press fires cancels the gesture entirely (no tap, no drag).
 */
class FloatingControlView(
    context: Context,
    private val onTap: () -> Unit,
    private val onDragStart: (rawX: Float, rawY: Float) -> Unit,
    private val onDragMove: (rawX: Float, rawY: Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {

    private val glyph: ImageView
    private val longPressTimeoutMs = 400L
    private val touchSlopPx = ViewConfiguration.get(context).scaledTouchSlop
    private val dragRunnable = Runnable {
        if (isPressed && !gestureCancelled && !dragging) {
            dragging = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onDragStart(downRawX, downRawY)
        }
    }

    private var downRawX = 0f
    private var downRawY = 0f
    private var downTimeMs = 0L
    private var dragging = false
    private var gestureCancelled = false

    private val liveColor = Color.parseColor("#148F5A")
    private val frozenColor = Color.parseColor("#E8890C")
    private val liveStroke = Color.WHITE

    init {
        val sizePx = dp(52)
        layoutParams = LayoutParams(sizePx, sizePx)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(liveColor)
            setStroke(dp(2).toInt(), liveStroke)
        }
        elevation = dp(6).toFloat()
        contentDescription = context.getString(R.string.floating_control_live_description)

        glyph = ImageView(context).apply {
            setImageResource(R.drawable.ic_floating_live)
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        addView(glyph)

        isClickable = true
        isFocusable = false
    }

    fun setFrozen(frozen: Boolean) {
        glyph.setImageResource(if (frozen) R.drawable.ic_floating_frozen else R.drawable.ic_floating_live)
        val bg = background as GradientDrawable
        bg.setColor(if (frozen) frozenColor else liveColor)
        contentDescription = context.getString(
            if (frozen) R.string.floating_control_frozen_description else R.string.floating_control_live_description,
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                downTimeMs = SystemClock.elapsedRealtime()
                dragging = false
                gestureCancelled = false
                postDelayed(dragRunnable, longPressTimeoutMs)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    onDragMove(event.rawX, event.rawY)
                    return true
                }
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (dx * dx + dy * dy > touchSlopPx * touchSlopPx) {
                    gestureCancelled = true
                    removeCallbacks(dragRunnable)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(dragRunnable)
                if (dragging) {
                    onDragEnd()
                } else if (!gestureCancelled && SystemClock.elapsedRealtime() - downTimeMs < longPressTimeoutMs + TAP_GRACE_MS) {
                    onTap()
                }
                dragging = false
                gestureCancelled = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(dragRunnable)
                if (dragging) onDragEnd()
                dragging = false
                gestureCancelled = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAP_GRACE_MS = 200L
    }
}
