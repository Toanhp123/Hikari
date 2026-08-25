package app.openstory.chapters.notification

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId

enum class ChapterChangeKind {
    CANONICAL_CHAPTER_CREATED,
    RELEASE_LINKED,
    CANONICAL_CHAPTER_RESTORED,
}

data class ChapterChangeFact(
    val eventKey: String,
    val storyId: StoryId,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId?,
    val kind: ChapterChangeKind,
    val chapterCommitFingerprint: String,
    val occurredAtEpochMillis: Long,
)
