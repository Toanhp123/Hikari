package app.openstory.work

import app.openstory.settings.background.BackgroundWorkPolicy
import app.openstory.settings.background.BackgroundWorkScheduleResult
import app.openstory.settings.background.BackgroundWorkScheduleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class WorkManagerBackgroundWorkScheduleAdapterTest {
    @Test
    fun identicalPolicyIsNotRegisteredTwice() = runTest {
        val registrar = RecordingPeriodicWorkRegistrar()
        val adapter = WorkManagerBackgroundWorkScheduleAdapter(registrar)
        val policy = policy()

        assertEquals(BackgroundWorkScheduleStatus.APPLIED, adapter.apply(policy).status)
        assertEquals(BackgroundWorkScheduleStatus.UNCHANGED, adapter.apply(policy).status)
        assertEquals(listOf(policy), registrar.applied)
    }

    @Test
    fun changedCadenceOrConstraintsUpdatesRegistration() = runTest {
        val registrar = RecordingPeriodicWorkRegistrar()
        val adapter = WorkManagerBackgroundWorkScheduleAdapter(registrar)

        adapter.apply(policy())
        adapter.apply(policy(cadenceHours = 12))
        adapter.apply(policy(cadenceHours = 12, unmetered = true))

        assertEquals(3, registrar.applied.size)
    }

    @Test
    fun failedRegistrationIsEligibleForTheNextAttempt() = runTest {
        val registrar = RecordingPeriodicWorkRegistrar(failFirstApply = true)
        val adapter = WorkManagerBackgroundWorkScheduleAdapter(registrar)
        val policy = policy()

        assertEquals(BackgroundWorkScheduleStatus.FAILED, adapter.apply(policy).status)
        assertEquals(BackgroundWorkScheduleStatus.APPLIED, adapter.apply(policy).status)
        assertEquals(2, registrar.applied.size)
    }

    private fun policy(
        cadenceHours: Int = 6,
        unmetered: Boolean = false,
    ) = BackgroundWorkPolicy(
        enabled = true,
        cadenceHours = cadenceHours,
        requireUnmeteredNetwork = unmetered,
        requireBatteryNotLow = true,
    )
}

private class RecordingPeriodicWorkRegistrar(
    private val failFirstApply: Boolean = false,
) : PeriodicWorkRegistrar {
    val applied = mutableListOf<BackgroundWorkPolicy>()

    override fun apply(policy: BackgroundWorkPolicy): BackgroundWorkScheduleResult {
        applied += policy
        return if (failFirstApply && applied.size == 1) {
            BackgroundWorkScheduleResult.failed()
        } else {
            BackgroundWorkScheduleResult.applied()
        }
    }

    override fun cancelPeriodicChapterChecks(): BackgroundWorkScheduleResult =
        BackgroundWorkScheduleResult.cancelled()
}
