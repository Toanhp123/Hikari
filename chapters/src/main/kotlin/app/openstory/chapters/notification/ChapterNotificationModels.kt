package app.openstory.chapters.notification

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId

sealed interface ChapterNotificationDecision {
    data class Publish(val candidate: ChapterNotificationCandidate) : ChapterNotificationDecision
    data class Consume(val reasonCode: String) : ChapterNotificationDecision
}

data class ChapterNotificationCandidate(
    val storyId: StoryId,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId?,
    val languageTag: String?,
)

data class ChapterNotificationPolicy(
    val notifyNewCanonicalChapters: Boolean,
    val notifyPreferredLanguageReleases: Boolean,
    val contentLanguageOrder: List<String>,
)

data class ChapterNotificationContext(
    val storyId: StoryId,
    val chapterId: CanonicalChapterId,
    val chapterTombstoned: Boolean,
    val chapterRead: Boolean,
    val releases: List<ChapterNotificationRelease>,
)

data class ChapterNotificationRelease(
    val releaseId: ChapterReleaseId,
    val languageTag: String,
)

data class ChapterNotificationTargetSnapshot(
    val storyId: StoryId,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId?,
    val storyTitle: String,
    val chapterLabel: String,
    val languageTag: String?,
)

interface ChapterNotificationTargetSource {
    suspend fun context(fact: ChapterChangeFact): ChapterNotificationContext?
    suspend fun resolveStory(storyId: StoryId): StoryId?
    suspend fun target(
        storyId: StoryId,
        chapterId: CanonicalChapterId,
        releaseId: ChapterReleaseId?,
    ): ChapterNotificationTargetSnapshot?
}
