package app.openstory.catalog.repository

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class CatalogSearchSummaryMutation(
    val pluginId: PluginId,
    val pluginVersion: String,
    val resolvedAtEpochMillis: Long,
    val stories: List<Story>,
    val entries: List<CatalogEntry>,
) {
    init {
        require(pluginVersion.isNotBlank())
        require(resolvedAtEpochMillis >= 0L)
        require(entries.all { it.pluginId == pluginId })
        val storyIds = stories.mapTo(hashSetOf(), Story::id)
        require(entries.all { it.storyId in storyIds })
    }
}

data class CatalogSearchSummaryCommitResult(
    val sourceStoryIds: Map<SourceKey, StoryId>,
    val changes: List<CatalogCommitChange> = emptyList(),
)
