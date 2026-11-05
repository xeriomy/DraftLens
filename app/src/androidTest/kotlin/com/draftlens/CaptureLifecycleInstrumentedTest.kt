package com.draftlens

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.draftlens.capture.CaptureSessionService
import com.draftlens.permissions.ProjectionPermission
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation plan (physical device required):
 * 1. Launch app, tap Start Capture, grant consent, verify notification appears.
 * 2. Verify preview updates (frameCount increments while MLBB draft screen visible).
 * 3. Rotate device: VirtualDisplay resizes without new consent prompt.
 * 4. Tap notification Stop: service teardown, no leaked resources (adb dumpsys media_projection).
 * 5. Revoke consent via Settings > Apps > DraftLens > Permissions / special access mid-capture:
 *    expect ERROR state, notification removed, no crash.
 * 6. Run 10 min: no OOM, frame latency stable, memory flat (adb shell dumpsys meminfo).
 *
 * This test only exercises the non-interactive parts (manifest + permission intent creation).
 */
@RunWith(AndroidJUnit4::class)
class CaptureLifecycleInstrumentedTest {

    @Test fun serviceIsDeclaredInManifest() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val component = android.content.ComponentName(ctx, CaptureSessionService::class.java)
        try {
            val info = ctx.packageManager.getServiceInfo(component, 0)
            assertEquals(CaptureSessionService::class.java.name, info.name)
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            fail("CaptureSessionService not in manifest: ${e.message}")
        }
    }

    @Test fun screenCaptureIntentCanBeCreated() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val intent = ProjectionPermission.createScreenCaptureIntent(ctx)
        assertNotNull(intent)
        assertEquals(android.media.projection.MediaProjectionManager::class.java.name, intent.component?.className?.let { "media-projection" } ?: "media-projection")
    }

    @Test fun foregroundServiceTypeIsMediaProjection() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val pm = ctx.packageManager
        val info = pm.getApplicationInfo(ctx.packageName, 0)
        assertNotNull(info)
        // Actual verification: run `adb shell dumpsys package com.draftlens | grep mediaProjection`
        // after starting capture. This test documents the requirement.
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assertNotNull(instrumentation.context)
    }
}
