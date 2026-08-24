package app.openstory.notifications

import android.app.NotificationManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationChannelConfigTest {
    @Test
    fun createsTheStableChapterChannel() {
        val context = RuntimeEnvironment.getApplication()
        NotificationChannelConfig.create(context)
        val channel = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(NotificationChannelConfig.CHAPTER_UPDATES_CHANNEL_ID)
        assertEquals("Chapter updates", channel.name.toString())
        assertTrue(NotificationChannelConfig.isEnabled(context))
    }
}
