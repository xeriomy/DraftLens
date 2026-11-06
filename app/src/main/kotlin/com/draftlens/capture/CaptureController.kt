package com.draftlens.capture

import android.content.Context
import android.content.Intent
import android.os.Build

/** Starts/stops the foreground service with the required Android 14+ ordering:
 *  startForegroundService() then the service foregrounds itself before projection use. */
object CaptureController {
    fun start(context: Context, resultCode: Int, data: Intent, intervalMs: Long) {
        val intent = CaptureSessionService.startIntent(context, resultCode, data, intervalMs)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stop(context: Context) = context.startService(CaptureSessionService.stopIntent(context))
    fun pause(context: Context) = context.startService(CaptureSessionService.pauseIntent(context))
    fun resume(context: Context) = context.startService(CaptureSessionService.resumeIntent(context))
}
