package app.openstory.storage.room.chapters

import app.openstory.chapters.notification.ChapterChangeFact
import app.openstory.chapters.notification.ChapterNotificationContext
import app.openstory.chapters.notification.ChapterNotificationRelease
import app.openstory.chapters.notification.ChapterNotificationTargetSnapshot
import app.openstory.chapters.notification.ChapterNotificationTargetSource
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.RoomStoryIdentityResolver

class RoomChapterNotificationTargetSource(
    private val database: OpenStoryDatabase,
) : ChapterNotificationTargetSource {
    private val chapters = database.chapterDao()
    private val catalog = database.catalogDao()
    private val progress = database.readingProgressDao()
    private val identity = RoomStoryIdentityResolver(database)

    @Suppress("ReturnCount")
    override suspend fun context(fact: ChapterChangeFact): ChapterNotificationContext? {
        val resolvedStoryId = identity.resolve(fact.storyId)
        val chapter = chapters.findChapter(fact.chapterId.value) ?: return null
        if (chapter.storyId != resolvedStoryId.value) return null
        val releases = chapters.releasesForChapter(chapter.canonicalChapterId)
        return ChapterNotificationContext(
            storyId = resolvedStoryId,
            chapterId = CanonicalChapterId(chapter.canonicalChapterId),
            chapterTombstoned = chapter.tombstoned,
            chapterRead = progress.find(resolvedStoryId.value, chapter.canonicalChapterId)
                ?.completedAtEpochMillis != null,
            releases = releases.map {
                ChapterNotificationRelease(ChapterReleaseId(it.chapterReleaseId), it.languageTag)
            },
        )
    }

    override suspend fun resolveStory(storyId: StoryId): StoryId? {
        val resolved = identity.resolve(storyId)
        return resolved.takeIf { catalog.findStory(it.value) != null }
    }

    @Suppress("ReturnCount")
    override suspend fun target(
        storyId: StoryId,
        chapterId: CanonicalChapterId,
        releaseId: ChapterReleaseId?,
    ): ChapterNotificationTargetSnapshot? {
        val resolved = resolveStory(storyId) ?: return null
        val chapter = chapters.findChapter(chapterId.value) ?: return null
        if (chapter.storyId != resolved.value || chapter.tombstoned) return null
        val releases = chapters.releasesForChapter(chapterId.value)
        val release = releaseId?.let { id -> releases.singleOrNull { it.chapterReleaseId == id.value } }
        if (releaseId != null && release == null) return null
        val title = catalog.entriesForStory(resolved.value).firstOrNull()?.title?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        return ChapterNotificationTargetSnapshot(
            storyId = resolved,
            chapterId = chapterId,
            releaseId = releaseId,
            storyTitle = title,
            chapterLabel = chapter.displayLabel,
            languageTag = release?.languageTag,
        )
    }
}
