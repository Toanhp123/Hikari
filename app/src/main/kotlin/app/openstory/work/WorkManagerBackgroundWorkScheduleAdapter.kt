package app.openstory.work

import app.openstory.settings.background.BackgroundWorkPolicy
import app.openstory.settings.background.BackgroundWorkSchedulePort
import app.openstory.settings.background.BackgroundWorkScheduleResult

class WorkManagerBackgroundWorkScheduleAdapter(
    private val registrar: PeriodicWorkRegistrar,
) : BackgroundWorkSchedulePort {
    private var lastAppliedPolicy: BackgroundWorkPolicy? = null

    override suspend fun apply(policy: BackgroundWorkPolicy): BackgroundWorkScheduleResult {
        if (policy == lastAppliedPolicy) return BackgroundWorkScheduleResult.unchanged()
        val result = registrar.apply(policy)
        if (result.errorCode == null) lastAppliedPolicy = policy
        return result
    }

    override suspend fun cancelPeriodicChapterChecks(): BackgroundWorkScheduleResult {
        val result = registrar.cancelPeriodicChapterChecks()
        if (result.errorCode == null) lastAppliedPolicy = null
        return result
    }
}
