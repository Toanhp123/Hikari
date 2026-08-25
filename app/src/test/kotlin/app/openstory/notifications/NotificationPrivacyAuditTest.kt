package app.openstory.notifications

import android.app.PendingIntent
import android.content.Intent
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertFalse
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationPrivacyAuditTest {
    @Test
    fun notificationContainsOnlyBoundedDisplayMetadataAndStableNavigationExtras() {
        val context = RuntimeEnvironment.getApplication()
        val contentIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, app.openstory.MainActivity::class.java)
                .putExtra(NotificationDeepLinkFactory.EXTRA_STORY_ID, "story:1")
                .putExtra(NotificationDeepLinkFactory.EXTRA_CHAPTER_ID, "chapter:1"),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = StoryNotificationBuilder(context).build(
            ValidatedChapterNotificationTarget(
                StoryId("story:1"),
                CanonicalChapterId("chapter:1"),
                null,
                "A Safe Story",
                "Chapter 1",
                "en",
                contentIntent,
            ),
        )
        val rendered = buildString {
            append(shadowOf(notification).contentTitle)
            append(shadowOf(notification).contentText)
            append(notification.extras.keySet().sorted().joinToString())
            append(shadowOf(contentIntent).savedIntent.extras?.keySet()?.sorted()?.joinToString())
        }.lowercase()
        listOf("cookie", "token", "authorization", "chapter_body", "exception", "sql", "http")
            .forEach { forbidden -> assertFalse(forbidden in rendered) }
    }
}
