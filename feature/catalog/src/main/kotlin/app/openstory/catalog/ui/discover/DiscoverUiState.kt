package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.RefreshState
import app.openstory.common.id.PluginId

enum class DiscoverNoContentReason {
    EMPTY_FEED,
    NO_ENABLED_PROVIDERS,
}

data class DiscoverContent(
    val selectedContentType: ContentType,
    val mediaTypeOptions: List<DiscoverMediaTypeOption>,
    val popular: List<DiscoverStoryItem>,
    val latestUpdates: List<DiscoverStoryItem>,
    val topRated: List<DiscoverStoryItem>,
    val noContentReason: DiscoverNoContentReason? = null,
) {
    val hasContent: Boolean
        get() = popular.isNotEmpty() || latestUpdates.isNotEmpty() || topRated.isNotEmpty()
}

data class DiscoverUiState(
    val content: ContentState<DiscoverContent> = ContentState.Pending,
    val refresh: RefreshState = RefreshState(),
    val refreshReport: DiscoverRefreshReport? = null,
    val observationIssue: CatalogUiFailure? = null,
)

data class DiscoverRefreshReport(
    val succeeded: Set<PluginId> = emptySet(),
    val failed: Map<PluginId, String> = emptyMap(),
    val refreshedAtEpochMillis: Map<PluginId, Long?> = emptyMap(),
)
