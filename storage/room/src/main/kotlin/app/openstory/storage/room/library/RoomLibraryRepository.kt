package app.openstory.storage.room.library

import androidx.room.withTransaction
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryStatus
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.RoomStoryIdentityResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomLibraryRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: LibraryDao,
    private val identity: RoomStoryIdentityResolver,
) : LibraryRepository {
    constructor(database: OpenStoryDatabase) : this(
        database,
        database.libraryDao(),
        RoomStoryIdentityResolver(database),
    )

    override fun observe(): Flow<List<LibraryEntry>> = dao.observe().map { entries ->
        entries.map(LibraryEntity::toModel)
    }

    override suspend fun add(
        storyId: StoryId,
        status: LibraryStatus,
        addedAt: Long,
    ): LibraryEntry = database.withTransaction {
        val resolved = identity.resolve(storyId)
        dao.find(resolved.value)?.toModel() ?: run {
            val entity = LibraryEntity(
                storyId = resolved.value,
                status = status.name,
                addedAtEpochMillis = addedAt,
                updatedAtEpochMillis = addedAt,
            )
            dao.insert(entity)
            requireNotNull(dao.find(resolved.value)) {
                "Library membership was not readable after insert: ${resolved.value}"
            }.toModel()
        }
    }

    override suspend fun remove(storyId: StoryId) {
        dao.delete(identity.resolve(storyId).value)
    }

    override suspend fun changeStatus(
        storyId: StoryId,
        status: LibraryStatus,
        updatedAt: Long,
    ): LibraryEntry? = database.withTransaction {
        val resolved = identity.resolve(storyId)
        val existing = dao.find(resolved.value) ?: return@withTransaction null
        if (existing.status == status.name) return@withTransaction existing.toModel()

        dao.updateStatus(
            storyId = resolved.value,
            status = status.name,
            updatedAtEpochMillis = updatedAt,
        )
        requireNotNull(dao.find(resolved.value)).toModel()
    }
}

private fun LibraryEntity.toModel(): LibraryEntry = LibraryEntry(
    storyId = StoryId(storyId),
    status = LibraryStatus.valueOf(status),
    addedAt = addedAtEpochMillis,
    updatedAt = updatedAtEpochMillis,
)
