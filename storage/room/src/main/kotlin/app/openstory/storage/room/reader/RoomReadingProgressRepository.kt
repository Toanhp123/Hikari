package app.openstory.storage.room.reader

import androidx.room.withTransaction
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.RoomStoryIdentityResolver
import app.openstory.storage.room.catalog.observeResolvedSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class RoomReadingProgressRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: ReadingProgressDao,
    private val identity: RoomStoryIdentityResolver,
) : ReadingProgressRepository {
    constructor(database: OpenStoryDatabase) : this(
        database,
        database.readingProgressDao(),
        RoomStoryIdentityResolver(database),
    )

    override fun observeAll(): Flow<List<ReadingProgress>> =
        dao.observeAll().map { progress -> progress.map(ReadingProgressEntity::toModel) }

    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<ReadingProgress>> =
        if (storyIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            identity.observeResolvedSet(storyIds).flatMapLatest { resolved ->
                dao.observeForStories(resolved.map(StoryId::value))
                    .map { progress -> progress.map(ReadingProgressEntity::toModel) }
            }
        }

    override fun observe(
        storyId: StoryId,
        chapterId: CanonicalChapterId,
    ): Flow<ReadingProgress?> = identity.observeResolved(storyId).flatMapLatest { resolved ->
        dao.observe(resolved.value, chapterId.value).map { it?.toModel() }
    }

    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? =
        dao.find(identity.resolve(storyId).value, chapterId.value)?.toModel()

    override suspend fun save(progress: ReadingProgress) {
        database.withTransaction {
            val resolved = identity.resolve(progress.storyId)
            dao.upsert(progress.copy(storyId = resolved).toEntity())
        }
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
