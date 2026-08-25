package app.openstory.navigation

import android.content.Intent
import app.openstory.chapters.notification.ChapterChangeFact
import app.openstory.chapters.notification.ChapterNotificationContext
import app.openstory.chapters.notification.ChapterNotificationTargetSnapshot
import app.openstory.chapters.notification.ChapterNotificationTargetSource
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.notifications.NotificationDeepLinkFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationDeepLinkRoutingTest {
    private val source = FakeNotificationTargetSource()
    private val parser = DefaultNotificationIntentParser(source)

    @Test
    fun validChapterTargetRoutesToReader() = runTest {
        val route = parser.route(intent("story:old", "chapter:1", "release:1"))
        assertEquals(AppRoute.Reader("story:current", "chapter:1", "release:1"), route)
    }

    @Test
    fun stableStoryOnlyTargetRoutesToStory() = runTest {
        assertEquals(AppRoute.Story("story:current"), parser.route(intent("story:old")))
    }

    @Test
    fun malformedMissingAndForgedTargetsAreRejected() = runTest {
        assertNull(parser.route(intent(" ")))
        assertNull(parser.route(intent("story:missing")))
        assertNull(parser.route(intent("story:old", "chapter:missing", "release:1")))
        assertNull(parser.route(intent("story:old", "chapter:1", "release:forged")))
    }

    private fun intent(storyId: String, chapterId: String? = null, releaseId: String? = null) =
        Intent(NotificationDeepLinkFactory.ACTION_OPEN_CHAPTER_NOTIFICATION)
            .putExtra(NotificationDeepLinkFactory.EXTRA_STORY_ID, storyId)
            .apply { chapterId?.let { putExtra(NotificationDeepLinkFactory.EXTRA_CHAPTER_ID, it) } }
            .apply { releaseId?.let { putExtra(NotificationDeepLinkFactory.EXTRA_RELEASE_ID, it) } }
}

private class FakeNotificationTargetSource : ChapterNotificationTargetSource {
    override suspend fun context(fact: ChapterChangeFact): ChapterNotificationContext? = null

    override suspend fun resolveStory(storyId: StoryId): StoryId? = when (storyId.value) {
        "story:old", "story:current" -> StoryId("story:current")
        else -> null
    }

    override suspend fun target(
        storyId: StoryId,
        chapterId: CanonicalChapterId,
        releaseId: ChapterReleaseId?,
    ): ChapterNotificationTargetSnapshot? {
        if (storyId.value != "story:current" || chapterId.value != "chapter:1") return null
        if (releaseId != null && releaseId.value != "release:1") return null
        return ChapterNotificationTargetSnapshot(
            storyId,
            chapterId,
            releaseId,
            "Story",
            "Chapter 1",
            "en",
        )
    }
}
