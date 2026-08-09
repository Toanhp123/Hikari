package app.openstory.home.domain

import app.openstory.common.AppError
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class SearchRequest(
    val query: String,
    val filterValues: Map<PluginId, Map<String, List<String>>> = emptyMap(),
)

data class SearchResultPage(
    val query: String = "",
    val results: List<SearchResultCard> = emptyList(),
    val filters: List<SearchCatalogFilters> = emptyList(),
    val failures: Map<PluginId, AppError> = emptyMap(),
    val error: AppError? = null,
    val searching: Boolean = false,
)

data class SearchResultCard(
    val storyId: StoryId,
    val title: String,
    val contentType: ContentType,
    val sources: List<SearchResultSource>,
)

data class SearchResultSource(
    val pluginId: PluginId,
    val pluginVersion: String,
    val sourceId: String,
    val title: String,
    val contentType: ContentType,
    val authors: Set<String>,
    val coverReference: String?,
    val score: Double?,
    val scoreScale: Double?,
)

data class SearchCatalogFilters(
    val pluginId: PluginId,
    val pluginVersion: String,
    val definitions: List<SearchFilterDefinition>,
)

sealed interface SearchFilterDefinition {
    val id: String
    val label: String
}

data class SearchOptionFilterDefinition(
    override val id: String,
    override val label: String,
    val kind: SearchOptionFilterKind,
    val options: List<SearchFilterOption>,
) : SearchFilterDefinition

enum class SearchOptionFilterKind {
    SELECT,
    MULTI_SELECT,
    SORT,
}

data class SearchFilterOption(
    val value: String,
    val label: String,
)

data class SearchRangeFilterDefinition(
    override val id: String,
    override val label: String,
    val minimum: Double,
    val maximum: Double,
    val step: Double,
) : SearchFilterDefinition

data class SearchTextFilterDefinition(
    override val id: String,
    override val label: String,
    val placeholder: String?,
) : SearchFilterDefinition
