package com.draftlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.draftlens.feature.home.DebugCaptureScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
