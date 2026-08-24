package app.openstory.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.openstory.settings.background.BackgroundWorkPolicy
import app.openstory.settings.background.BackgroundWorkScheduleResult
import java.util.concurrent.TimeUnit

class WorkManagerPeriodicSyncScheduler(
    context: Context,
) : PeriodicWorkRegistrar {
    private val applicationContext = context.applicationContext

    override fun apply(policy: BackgroundWorkPolicy): BackgroundWorkScheduleResult =
        try {
            val workManager = WorkManager.getInstance(applicationContext)
            if (!policy.enabled) {
                workManager.cancelUniqueWork(WorkNames.LIBRARY_CHAPTER_PERIODIC)
                workManager.cancelUniqueWork(WorkNames.LIBRARY_CHAPTER_CONTINUATION)
                BackgroundWorkScheduleResult.cancelled()
            } else {
                val request = PeriodicWorkRequestBuilder<PeriodicChapterDispatchWorker>(
                    policy.cadenceHours.toLong(),
                    TimeUnit.HOURS,
                )
                    .setConstraints(WorkConstraintsFactory.periodic(policy))
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    WorkNames.LIBRARY_CHAPTER_PERIODIC,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request,
                )
                BackgroundWorkScheduleResult.applied()
            }
        } catch (_: RuntimeException) {
            BackgroundWorkScheduleResult.failed()
        }

    override fun cancelPeriodicChapterChecks(): BackgroundWorkScheduleResult = try {
        val workManager = WorkManager.getInstance(applicationContext)
        workManager.cancelUniqueWork(WorkNames.LIBRARY_CHAPTER_PERIODIC)
        workManager.cancelUniqueWork(WorkNames.LIBRARY_CHAPTER_CONTINUATION)
        BackgroundWorkScheduleResult.cancelled()
    } catch (_: RuntimeException) {
        BackgroundWorkScheduleResult.failed()
    }
}

interface PeriodicWorkRegistrar {
    fun apply(policy: BackgroundWorkPolicy): BackgroundWorkScheduleResult
    fun cancelPeriodicChapterChecks(): BackgroundWorkScheduleResult
}
