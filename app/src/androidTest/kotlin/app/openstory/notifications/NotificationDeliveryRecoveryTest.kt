package app.openstory.notifications

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.openstory.work.WorkNames
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationDeliveryRecoveryTest {
    @Test
    fun concurrentRecoveryWakesKeepOneDurableUniqueWorkChain() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val scheduler = WorkManagerNotificationDrainScheduler(context)
        scheduler.ensureRecoveryWork()
        scheduler.ensureRecoveryWork()

        val active = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(WorkNames.CHAPTER_NOTIFICATION_DRAIN)
            .get(10, TimeUnit.SECONDS)
            .count { it.state != WorkInfo.State.CANCELLED }
        assertEquals(1, active)
    }
}
