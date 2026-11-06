package com.draftlens.permissions

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.content.Context

/** Thin wrappers so the consent flow is testable and never bypassed. */
object ProjectionPermission {

    fun createScreenCaptureIntent(context: Context): Intent {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mgr.createScreenCaptureIntent()
    }

    /** Android 14+ also requires the caller to be foreground; the launcher guarantees that. */
    fun isResultOk(resultCode: Int): Boolean = resultCode == Activity.RESULT_OK && resultCode != 0
}
