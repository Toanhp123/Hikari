package app.openstory.notifications

import android.Manifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotificationPermissionGateTest {
    @Test
    @Config(sdk = [32])
    fun preRuntimePermissionDevicesAreGrantedWithoutLaunchingUi() = runTest {
        val gate = NotificationPermissionGate(RuntimeEnvironment.getApplication())
        assertEquals(PermissionRequestOutcome.GRANTED, gate.requestPermission())
    }

    @Test
    @Config(sdk = [35])
    fun deniedRuntimePermissionIsReportedWithoutBeingRequestedByStatusCheck() {
        val application = RuntimeEnvironment.getApplication()
        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(NotificationPermissionGate(application).status().permissionGranted)
    }
}
