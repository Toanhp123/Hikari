package app.openstory.navigation

import android.content.Intent
import app.openstory.chapters.notification.ChapterNotificationTargetSource
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.notifications.NotificationDeepLinkFactory

fun interface NotificationIntentParser {
    suspend fun route(intent: Intent): AppRoute?
}

class DefaultNotificationIntentParser(
    private val targets: ChapterNotificationTargetSource,
) : NotificationIntentParser {
    @Suppress("ReturnCount")
    override suspend fun route(intent: Intent): AppRoute? {
        if (intent.action != NotificationDeepLinkFactory.ACTION_OPEN_CHAPTER_NOTIFICATION) return null
        val storyId = parseId(intent.getStringExtra(NotificationDeepLinkFactory.EXTRA_STORY_ID), ::StoryId)
            ?: return null
        val resolvedStory = targets.resolveStory(storyId) ?: return null
        val chapterRaw = intent.getStringExtra(NotificationDeepLinkFactory.EXTRA_CHAPTER_ID)
        if (chapterRaw == null) return AppRoute.Story(resolvedStory.value)
        val chapterId = parseId(chapterRaw, ::CanonicalChapterId) ?: return null
        val releaseRaw = intent.getStringExtra(NotificationDeepLinkFactory.EXTRA_RELEASE_ID)
        val releaseId = releaseRaw?.let { parseId(it, ::ChapterReleaseId) ?: return null }
        val target = targets.target(resolvedStory, chapterId, releaseId)
            ?: return null
        return AppRoute.Reader(
            storyId = target.storyId.value,
            chapterId = target.chapterId.value,
            releaseId = target.releaseId?.value,
        )
    }

    private inline fun <T> parseId(value: String?, factory: (String) -> T): T? =
        value?.let { raw -> runCatching { factory(raw) }.getOrNull() }
}
