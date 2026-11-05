package com.draftlens

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.draftlens.feature.home.DebugCaptureScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Do not capture the capture window itself; omit this UI from MediaProjection output.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val app = application as DraftLensApp
        setContent {
            DebugCaptureScreen(
                preferences = app.preferences,
                activity = this
            )
        }
    }
}
