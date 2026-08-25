package app.openstory.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import app.openstory.R

class StoryNotificationBuilder(
    private val context: Context,
) {
    fun build(target: ValidatedChapterNotificationTarget) = NotificationCompat.Builder(
        context,
        NotificationChannelConfig.CHAPTER_UPDATES_CHANNEL_ID,
    )
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle("New chapter available")
        .setContentText(
            "${target.storyTitle.safeLabel(STORY_TITLE_MAX_LENGTH)} - " +
                target.chapterLabel.safeLabel(CHAPTER_LABEL_MAX_LENGTH),
        )
        .setContentIntent(target.contentIntent)
        .setAutoCancel(true)
        .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .build()

    private companion object {
        const val STORY_TITLE_MAX_LENGTH = 80
        const val CHAPTER_LABEL_MAX_LENGTH = 64
    }
}

private fun String.safeLabel(maxLength: Int): String = asSequence()
    .filterNot(Char::isISOControl)
    .joinToString(separator = "")
    .trim()
    .take(maxLength)
