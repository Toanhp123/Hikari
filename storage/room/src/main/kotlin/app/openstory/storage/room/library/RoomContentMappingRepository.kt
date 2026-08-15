package app.openstory.storage.room.library

import androidx.room.withTransaction
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingRejection
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.library.mapping.ContentMappingWriteResult
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class RoomContentMappingRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: LibraryDao,
) : ContentMappingRepository {
    constructor(database: OpenStoryDatabase) : this(database, database.libraryDao())

    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> =
        dao.observeMappings(storyId.value).map { mappings -> mappings.map(ContentMappingEntity::toModel) }

    override fun observeAll(): Flow<List<ContentMapping>> =
        dao.observeMappings().map { mappings -> mappings.map(ContentMappingEntity::toModel) }

    override fun observeForStories(storyIds: Set<StoryId>): Flow<List<ContentMapping>> =
        if (storyIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            dao.observeMappings(storyIds.map(StoryId::value))
                .map { mappings -> mappings.map(ContentMappingEntity::toModel) }
        }

    override suspend fun compareAndWrite(
        mapping: ContentMapping,
        replaceableOrigins: Set<ContentMappingOrigin>,
    ): ContentMappingWriteResult = database.withTransaction {
        val existing = dao.findMapping(mapping.storyId.value, mapping.pluginId.value)
        when {
            existing == null -> {
                dao.insertMapping(mapping.toEntity())
                ContentMappingWriteResult.Written(mapping, changed = true)
            }
            existing.sameMapping(mapping) -> ContentMappingWriteResult.Written(
                mapping = existing.toModel(),
                changed = false,
            )
            ContentMappingOrigin.valueOf(existing.origin) !in replaceableOrigins ->
                ContentMappingWriteResult.Protected(existing.toModel())
            else -> {
                dao.updateMapping(mapping.toEntity())
                ContentMappingWriteResult.Written(mapping, changed = true)
            }
        }
    }

    override suspend fun reject(rejection: ContentMappingRejection) {
        dao.upsertRejection(rejection.toEntity())
    }

    override suspend fun isRejected(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
        policyVersion: Int,
    ): Boolean = dao.isRejected(storyId.value, pluginId.value, sourceStoryId, policyVersion)
}

private fun ContentMappingEntity.sameMapping(mapping: ContentMapping): Boolean =
    storyId == mapping.storyId.value &&
        pluginId == mapping.pluginId.value &&
        sourceStoryId == mapping.sourceStoryId &&
        origin == mapping.origin.name &&
        policyVersion == mapping.policyVersion

private fun ContentMapping.toEntity() = ContentMappingEntity(
    storyId = storyId.value,
    pluginId = pluginId.value,
    sourceStoryId = sourceStoryId,
    origin = origin.name,
    policyVersion = policyVersion,
    updatedAtEpochMillis = updatedAt,
)

private fun ContentMappingEntity.toModel() = ContentMapping(
    storyId = StoryId(storyId),
    pluginId = PluginId(pluginId),
    sourceStoryId = sourceStoryId,
    origin = ContentMappingOrigin.valueOf(origin),
    policyVersion = policyVersion,
    updatedAt = updatedAtEpochMillis,
)

private fun ContentMappingRejection.toEntity() = ContentMappingRejectionEntity(
    storyId = storyId.value,
    pluginId = pluginId.value,
    sourceStoryId = sourceStoryId,
    policyVersion = policyVersion,
    rejectedAtEpochMillis = rejectedAt,
)
