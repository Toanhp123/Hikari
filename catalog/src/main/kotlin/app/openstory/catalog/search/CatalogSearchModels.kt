package app.openstory.catalog.search

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class CatalogSearchSourceCard(
    val pluginId: PluginId,
    val sourceId: String,
    val title: String,
    val contentType: ContentType,
    val authors: Set<String>,
    val coverUrl: String?,
    val score: Score?,
)

data class CatalogSearchStory(
    val story: Story,
    val sources: List<CatalogSearchSourceCard>,
)

data class CatalogSearchFailure(val pluginId: PluginId, val code: String, val retryable: Boolean)

data class CatalogSearchResult(
    val stories: List<CatalogSearchStory>,
    val failures: List<CatalogSearchFailure>,
)

data class CatalogSearchRequest(
    val query: String,
    val filterValues: Map<PluginId, Map<String, List<String>>> = emptyMap(),
)
