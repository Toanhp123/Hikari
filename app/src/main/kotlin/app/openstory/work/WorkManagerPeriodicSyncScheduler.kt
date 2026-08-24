package app.openstory.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.openstory.settings.background.BackgroundWorkPolicy
import java.util.concurrent.TimeUnit

class WorkManagerPeriodicSyncScheduler(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    fun apply(policy: BackgroundWorkPolicy) {
        try {
            val workManager = WorkManager.getInstance(applicationContext)
            if (!policy.enabled) {
                workManager.cancelUniqueWork(WorkNames.LIBRARY_CHAPTER_PERIODIC)
                workManager.cancelUniqueWork(WorkNames.LIBRARY_CHAPTER_CONTINUATION)
                return
            }

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(
                    if (policy.requireUnmeteredNetwork) NetworkType.UNMETERED else NetworkType.CONNECTED,
                )
                .setRequiresBatteryNotLow(policy.requireBatteryNotLow)
                .build()
            val request = PeriodicWorkRequestBuilder<PeriodicChapterDispatchWorker>(
                policy.cadenceHours.toLong(),
                TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WorkNames.LIBRARY_CHAPTER_PERIODIC,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } catch (_: RuntimeException) {
            // Persisted policy remains authoritative; a later emission or startup can register work.
        }
    }
}
