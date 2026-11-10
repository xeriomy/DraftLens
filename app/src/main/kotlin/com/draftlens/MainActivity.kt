package com.draftlens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.draftlens.capture.CaptureVisionFrameSource
import com.draftlens.feature.home.DebugCaptureScreen
import com.draftlens.feature.visionlab.VisionLabScreen

/**
 * Two destinations: the capture debug screen and the Vision Lab.
 *
 * Capture lives in a foreground service, so switching tabs never stops it. The vision
 * source is remembered here so it keeps observing capture while the other tab is open.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as DraftLensApp
        setContent {
            val scope = rememberCoroutineScope()
            val visionSource = remember { CaptureVisionFrameSource.fromHolder(scope) }
            var selectedTab by rememberSaveable { mutableIntStateOf(0) }

            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Debug Capture") },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Vision Lab") },
                    )
                }
                when (selectedTab) {
                    0 -> DebugCaptureScreen(preferences = app.preferences, activity = this@MainActivity)
                    else -> VisionLabScreen(
                        calibrationRepository = app.calibrationProfiles,
                        source = visionSource,
                    )
                }
            }
        }
    }
}
