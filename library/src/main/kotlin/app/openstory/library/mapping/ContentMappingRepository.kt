package app.openstory.library.mapping

import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ContentMappingRepository {
    fun observe(storyId: StoryId): Flow<List<ContentMapping>>

    fun observeAll(): Flow<List<ContentMapping>>

    fun observeForStories(storyIds: Set<StoryId>): Flow<List<ContentMapping>> =
        observeAll().map { mappings -> mappings.filter { it.storyId in storyIds } }

    suspend fun compareAndWrite(
        mapping: ContentMapping,
        replaceableOrigins: Set<ContentMappingOrigin>,
    ): ContentMappingWriteResult

    suspend fun reject(rejection: ContentMappingRejection)

    suspend fun isRejected(
        storyId: StoryId,
        pluginId: PluginId,
        sourceStoryId: String,
        policyVersion: Int,
    ): Boolean
}
