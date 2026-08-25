package app.openstory.work

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId

object WorkNames {
    const val CANONICAL_ENGINE_DRAIN = "canonical-engine-drain"
    const val CANONICAL_ENGINE_SAFETY = "canonical-engine-safety"
    const val LIBRARY_CHAPTER_PERIODIC = "library-chapter-periodic"
    const val LIBRARY_CHAPTER_CONTINUATION = "library-chapter-continuation"
    const val CHAPTER_NOTIFICATION_DRAIN = "chapter-notification-drain"

    fun libraryMapping(storyId: StoryId): String = "library-mapping:${storyId.value}"
    fun storyChapterSync(storyId: StoryId): String = "initial-chapter-sync:${storyId.value}"
    fun chapterDownload(releaseId: ChapterReleaseId): String = "chapter-download:${releaseId.value}"

    fun canonicalRetryWake(nextAttemptAtEpochMillis: Long): String {
        require(nextAttemptAtEpochMillis >= 0L)
        return "canonical-engine-retry-wake:$nextAttemptAtEpochMillis"
    }

    fun postMergeDerived(storyId: StoryId): String =
        "canonical-post-merge-derived:${storyId.value}"
}
