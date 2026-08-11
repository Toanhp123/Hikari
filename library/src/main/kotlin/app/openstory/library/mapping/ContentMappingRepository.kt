package app.openstory.library.mapping

import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow

interface ContentMappingRepository {
    fun observe(storyId: StoryId): Flow<List<ContentMapping>>

    fun observeAll(): Flow<List<ContentMapping>>

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
