package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.SourceKey
import app.openstory.common.id.StoryId

data class StoryMergeLineage(
    val mergeEventId: String,
    val survivorStoryId: StoryId,
    val retiredStoryId: StoryId,
    val reconciliationCaseId: String?,
    val survivorSourceKeysBefore: Set<SourceKey>,
    val retiredSourceKeysBefore: Set<SourceKey>,
    val mergedAtEpochMillis: Long,
) {
    init {
        require(mergeEventId.isNotBlank())
        require(survivorStoryId != retiredStoryId)
        require(mergedAtEpochMillis >= 0L)
    }

    fun historicalCaseKey(): ReconciliationCaseKey = ReconciliationCaseKey.of(survivorStoryId, retiredStoryId)

    fun oppositeSourceKeys(sourceKey: SourceKey): Set<SourceKey> = when (sourceKey) {
        in survivorSourceKeysBefore -> retiredSourceKeysBefore
        in retiredSourceKeysBefore -> survivorSourceKeysBefore
        else -> emptySet()
    }
}

interface StoryMergeLineageReader {
    suspend fun lineagesFor(storyId: StoryId): List<StoryMergeLineage>
}

object EmptyStoryMergeLineageReader : StoryMergeLineageReader {
    override suspend fun lineagesFor(storyId: StoryId): List<StoryMergeLineage> = emptyList()
}
