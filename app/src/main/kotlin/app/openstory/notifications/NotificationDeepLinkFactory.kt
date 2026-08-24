package app.openstory.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.openstory.MainActivity
import app.openstory.chapters.notification.ChapterNotificationCandidate
import app.openstory.chapters.notification.ChapterNotificationTargetSource
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId

data class ValidatedChapterNotificationTarget(
    val storyId: StoryId,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId?,
    val storyTitle: String,
    val chapterLabel: String,
    val languageTag: String?,
    val contentIntent: PendingIntent,
)

fun interface ChapterNotificationTargetValidator {
    suspend fun validate(
        candidate: ChapterNotificationCandidate,
        notificationId: Int,
    ): ValidatedChapterNotificationTarget?
}

class NotificationDeepLinkFactory(
    context: Context,
    private val targets: ChapterNotificationTargetSource,
) : ChapterNotificationTargetValidator {
    private val applicationContext = context.applicationContext

    override suspend fun validate(
        candidate: ChapterNotificationCandidate,
        notificationId: Int,
    ): ValidatedChapterNotificationTarget? {
        require(notificationId > 0)
        val target = targets.target(candidate.storyId, candidate.chapterId, candidate.releaseId) ?: return null
        val intent = Intent(applicationContext, MainActivity::class.java)
            .setAction(ACTION_OPEN_CHAPTER_NOTIFICATION)
            .putExtra(EXTRA_STORY_ID, target.storyId.value)
            .putExtra(EXTRA_CHAPTER_ID, target.chapterId.value)
            .apply { target.releaseId?.let { putExtra(EXTRA_RELEASE_ID, it.value) } }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return ValidatedChapterNotificationTarget(
            storyId = target.storyId,
            chapterId = target.chapterId,
            releaseId = target.releaseId,
            storyTitle = target.storyTitle,
            chapterLabel = target.chapterLabel,
            languageTag = target.languageTag,
            contentIntent = pendingIntent,
        )
    }

    companion object {
        const val ACTION_OPEN_CHAPTER_NOTIFICATION = "app.openstory.action.OPEN_CHAPTER_NOTIFICATION"
        const val EXTRA_STORY_ID = "app.openstory.extra.STORY_ID"
        const val EXTRA_CHAPTER_ID = "app.openstory.extra.CHAPTER_ID"
        const val EXTRA_RELEASE_ID = "app.openstory.extra.RELEASE_ID"
    }
}
