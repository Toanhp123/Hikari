package app.openstory.notifications

import app.openstory.MainActivity
import app.openstory.chapters.notification.ChapterChangeFact
import app.openstory.chapters.notification.ChapterNotificationCandidate
import app.openstory.chapters.notification.ChapterNotificationContext
import app.openstory.chapters.notification.ChapterNotificationTargetSnapshot
import app.openstory.chapters.notification.ChapterNotificationTargetSource
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationDeepLinkFactoryTest {
    private val source = FactoryTargetSource()
    private val context = RuntimeEnvironment.getApplication()
    private val factory = NotificationDeepLinkFactory(context, source)

    @Test
    fun pendingIntentIsExplicitImmutableAndContainsOnlyStableIdentifiers() = runTest {
        val target = assertNotNull(
            factory.validate(
                ChapterNotificationCandidate(
                    StoryId("story:1"),
                    CanonicalChapterId("chapter:1"),
                    ChapterReleaseId("release:1"),
                    "en",
                ),
                notificationId = 41,
            ),
        )
        val intent = shadowOf(target.contentIntent).savedIntent
        assertEquals(MainActivity::class.java.name, intent.component?.className)
        assertEquals("story:1", intent.getStringExtra(NotificationDeepLinkFactory.EXTRA_STORY_ID))
        assertEquals("chapter:1", intent.getStringExtra(NotificationDeepLinkFactory.EXTRA_CHAPTER_ID))
        assertEquals("release:1", intent.getStringExtra(NotificationDeepLinkFactory.EXTRA_RELEASE_ID))
        assertEquals(3, intent.extras?.keySet()?.size)
    }

    @Test
    fun staleTargetDoesNotCreateAPendingIntent() = runTest {
        assertNull(
            factory.validate(
                ChapterNotificationCandidate(
                    StoryId("story:missing"),
                    CanonicalChapterId("chapter:1"),
                    null,
                    null,
                ),
                42,
            ),
        )
    }
}

private class FactoryTargetSource : ChapterNotificationTargetSource {
    override suspend fun context(fact: ChapterChangeFact): ChapterNotificationContext? = null
    override suspend fun resolveStory(storyId: StoryId): StoryId? = storyId.takeIf { it.value == "story:1" }

    override suspend fun target(
        storyId: StoryId,
        chapterId: CanonicalChapterId,
        releaseId: ChapterReleaseId?,
    ): ChapterNotificationTargetSnapshot? = if (storyId.value == "story:1") {
        ChapterNotificationTargetSnapshot(storyId, chapterId, releaseId, "Story", "Chapter 1", "en")
    } else {
        null
    }
}
