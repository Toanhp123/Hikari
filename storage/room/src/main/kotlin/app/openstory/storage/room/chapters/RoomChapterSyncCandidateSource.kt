package app.openstory.storage.room.chapters

import app.openstory.chapters.sync.ChapterSyncCandidate
import app.openstory.chapters.sync.ChapterSyncCandidateSource
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase

class RoomChapterSyncCandidateSource(
    private val database: OpenStoryDatabase,
) : ChapterSyncCandidateSource {
    override suspend fun eligibleCandidates(): List<ChapterSyncCandidate> =
        database.libraryDao().chapterSyncCandidates().map { row ->
            ChapterSyncCandidate(
                storyId = StoryId(row.storyId),
                lastSuccessfulSyncAtEpochMillis = row.lastSuccessfulSyncAtEpochMillis,
            )
        }
}
