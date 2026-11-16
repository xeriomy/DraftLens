package com.draftlens

import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.draftlens.datastore.AppPreferencesRepository
import com.draftlens.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compile-only in CI (no device farm): real WindowManager behavior needs a device with the
 * overlay appop granted. To execute on a physical device or emulator:
 *
 *   adb shell appops set --uid $(adb shell pm list package -U com.draftlens.debug | cut -d: -f3 | cut -d\; -f2) SYSTEM_ALERT_WINDOW allow
 *   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.draftlens.FloatingControlOverlayInstrumentedTest
 *
 * What can never be asserted here: whether the overlay appears inside a captured image —
 * that is a physical-device finding, recorded in docs/TESTING_PHYSICAL_DEVICE.md.
 */
@RunWith(AndroidJUnit4::class)
class FloatingControlOverlayInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val preferences = AppPreferencesRepository(context)
    private var controller: OverlayController? = null

    @After fun tearDown() {
        controller?.detach()
        controller = null
        runBlocking { preferences.setFloatingControlEnabled(false) }
    }

    @Test fun overlayPermissionIsDeclaredInTheManifest() {
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions?.toSet().orEmpty()
        assertTrue(
            "SYSTEM_ALERT_WINDOW must be declared; granting happens at runtime",
            requested.contains(android.Manifest.permission.SYSTEM_ALERT_WINDOW),
        )
    }

    @Test fun overlayAttachesWhenEnabledAndPermittedAndDetachesCleanly() {
        assumeTrue(
            "overlay appop not granted on this device; see class comment",
            Settings.canDrawOverlays(context),
        )
        runBlocking { preferences.setFloatingControlEnabled(true) }

        val created = OverlayController(context, preferences, scope)
        controller = created
        created.show(1080, 2400)

        assertTrue("overlay did not attach within 5s", waitUntil { created.isAttached })
        created.detach()
        assertFalse(created.isAttached)
    }

    private fun waitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return true
            SystemClock.sleep(100)
        }
        return condition()
    }
}
