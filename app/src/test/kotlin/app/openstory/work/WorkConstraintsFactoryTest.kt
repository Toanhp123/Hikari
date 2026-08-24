package app.openstory.work

import androidx.work.NetworkType
import app.openstory.settings.background.BackgroundWorkPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkConstraintsFactoryTest {
    @Test
    fun mapsEverySchedulingPolicyFieldToWorkManagerConstraints() {
        val metered = WorkConstraintsFactory.periodic(
            policy(unmetered = false, batteryNotLow = false),
        )
        val guarded = WorkConstraintsFactory.periodic(
            policy(unmetered = true, batteryNotLow = true),
        )

        assertEquals(NetworkType.CONNECTED, metered.requiredNetworkType)
        assertFalse(metered.requiresBatteryNotLow())
        assertEquals(NetworkType.UNMETERED, guarded.requiredNetworkType)
        assertTrue(guarded.requiresBatteryNotLow())
    }

    @Test
    fun rejectsCadenceOutsideTypedSettingsContract() {
        assertFailsWith<IllegalArgumentException> {
            WorkConstraintsFactory.periodic(policy(cadenceHours = 2))
        }
    }

    private fun policy(
        cadenceHours: Int = 6,
        unmetered: Boolean = false,
        batteryNotLow: Boolean = true,
    ) = BackgroundWorkPolicy(
        enabled = true,
        cadenceHours = cadenceHours,
        requireUnmeteredNetwork = unmetered,
        requireBatteryNotLow = batteryNotLow,
    )
}
