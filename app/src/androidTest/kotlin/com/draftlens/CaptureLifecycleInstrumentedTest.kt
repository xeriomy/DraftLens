package com.draftlens

import android.content.ComponentName
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.draftlens.capture.CaptureSessionService
import com.draftlens.permissions.ProjectionPermission
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation checks that can run once connected to an Android device or emulator.
 *
 * The physical capture path is not automatable here because it requires the system consent UI.
 * Run this class on the target device, then complete the manual capture checklist:
 * 1. Launch app, tap Start Capture, grant consent, verify notification appears.
 * 2. Verify preview updates (frameCount increments while the capture screen is visible).
 * 3. Rotate device: VirtualDisplay resizes without a new consent prompt.
 * 4. Tap notification Stop: service teardown, no leaked resources (adb dumpsys media_projection).
 * 5. Revoke consent mid-capture: expect ERROR state, notification removed, no crash.
 * 6. Run 10 min: no OOM, frame latency stable, memory flat (adb shell dumpsys meminfo).
 */
@RunWith(AndroidJUnit4::class)
class CaptureLifecycleInstrumentedTest {

    @Test fun serviceIsDeclaredInManifest() {
        val info = serviceInfo()
        assertEquals(CaptureSessionService::class.java.name, info.name)
        assertEquals(false, info.exported)
    }

    @Test fun foregroundServiceTypeIsMediaProjection() {
        val info = serviceInfo()
        assertTrue(
            "CaptureSessionService must declare mediaProjection foreground type",
            info.getForegroundServiceType() and ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION ==
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    @Test fun foregroundServiceManifestPermissionsAreGranted() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ctx.packageManager.checkPermission(Manifest.permission.FOREGROUND_SERVICE, ctx.packageName)
        )
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            ctx.packageManager.checkPermission(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION, ctx.packageName)
        )
    }

    @Test fun screenCaptureIntentCanBeCreated() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val intent = ProjectionPermission.createScreenCaptureIntent(ctx)
        assertNotNull(intent)
    }

    private fun serviceInfo(): ServiceInfo {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val component = ComponentName(ctx, CaptureSessionService::class.java)
        try {
            return ctx.packageManager.getServiceInfo(component, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            fail("CaptureSessionService not in manifest: ${e.message}")
            throw AssertionError(e)
        }
    }
}
