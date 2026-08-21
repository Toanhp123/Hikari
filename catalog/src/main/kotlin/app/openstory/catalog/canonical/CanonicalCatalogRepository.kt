package app.openstory.catalog.canonical

import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CanonicalCatalogRepository {
    fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?>
    fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>>

    fun observeReadyStories(storyIds: Set<StoryId>): Flow<List<CanonicalStoryState.Ready>> =
        observeReadyStories().map { states -> states.filter { it.story.id in storyIds } }

    suspend fun state(storyId: StoryId): CanonicalStoryState?
    suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord>
    suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration?
    suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference

    suspend fun setSourcePreference(preference: CanonicalSourcePreference)

    suspend fun persistCandidate(
        candidate: CanonicalGeneration,
        expectedActiveGenerationId: String?,
    ): Boolean

    suspend fun markHealth(storyId: StoryId, health: CanonicalHealth)
    suspend fun cleanupObsoleteGenerations(storyId: StoryId)
}
