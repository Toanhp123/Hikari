package app.openstory.catalog.ui.story

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class StoryUiState(
    val storyId: StoryId,
    val story: StoryUiModel? = null,
    val selectedSource: StorySourceIdentity? = null,
    val refreshing: Boolean = false,
    val failure: StoryRefreshFailure? = null,
)

data class StoryUiModel(
    val storyId: StoryId,
    val preferredTitle: String,
    val contentType: ContentType,
    val aliases: Set<String>,
    val sources: List<CatalogEntry>,
)

data class StorySourceIdentity(
    val pluginId: PluginId,
    val sourceId: String,
)

data class StoryRefreshFailure(
    val code: String,
    val retryable: Boolean,
)
