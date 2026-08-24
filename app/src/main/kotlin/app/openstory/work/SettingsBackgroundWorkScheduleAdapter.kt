package app.openstory.work

import app.openstory.settings.background.BackgroundWorkPolicy
import app.openstory.settings.background.BackgroundWorkSchedulePort

class SettingsBackgroundWorkScheduleAdapter(
    private val scheduler: WorkManagerPeriodicSyncScheduler,
) : BackgroundWorkSchedulePort {
    override suspend fun apply(policy: BackgroundWorkPolicy) {
        scheduler.apply(policy)
    }
}
