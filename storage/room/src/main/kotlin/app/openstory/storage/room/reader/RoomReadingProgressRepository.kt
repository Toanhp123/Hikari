package app.openstory.storage.room.reader

import androidx.room.withTransaction
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomReadingProgressRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: ReadingProgressDao,
) : ReadingProgressRepository {
    constructor(database: OpenStoryDatabase) : this(database, database.readingProgressDao())

    override fun observeAll(): Flow<List<ReadingProgress>> =
        dao.observeAll().map { progress -> progress.map(ReadingProgressEntity::toModel) }

    override fun observe(
        storyId: StoryId,
        chapterId: CanonicalChapterId,
    ): Flow<ReadingProgress?> = dao.observe(storyId.value, chapterId.value).map { it?.toModel() }

    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? =
        dao.find(storyId.value, chapterId.value)?.toModel()

    override suspend fun save(progress: ReadingProgress) {
        database.withTransaction { dao.upsert(progress.toEntity()) }
    }
}

private fun ReadingProgress.toEntity() = ReadingProgressEntity(
    storyId.value,
    canonicalChapterId.value,
    releaseId.value,
    contentFingerprint,
    position.blockId,
    position.characterOffset,
    position.fraction,
    completedAtEpochMillis,
    updatedAtEpochMillis,
)

private fun ReadingProgressEntity.toModel() = ReadingProgress(
    StoryId(storyId),
    CanonicalChapterId(canonicalChapterId),
    ChapterReleaseId(chapterReleaseId),
    contentFingerprint,
    app.openstory.reader.progress.ReadingPosition(blockId, characterOffset, fraction),
    completedAtEpochMillis,
    updatedAtEpochMillis,
)
