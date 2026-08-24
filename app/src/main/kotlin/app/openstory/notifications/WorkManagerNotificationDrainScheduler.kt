package app.openstory.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.openstory.chapters.notification.NotificationDrainScheduler
import app.openstory.work.WorkNames

class WorkManagerNotificationDrainScheduler(
    context: Context,
) : NotificationDrainScheduler {
    private val applicationContext = context.applicationContext

    override suspend fun schedule() {
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            WorkNames.CHAPTER_NOTIFICATION_DRAIN,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NotificationDeliveryWorker>().build(),
        )
    }

    suspend fun ensureRecoveryWork() = schedule()
}
