package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.ContentType
import app.openstory.common.id.PluginId

data class DiscoverUiState(
    val selectedContentType: ContentType = ContentType.MANGA,
    val mediaTypeOptions: List<DiscoverMediaTypeOption> = defaultDiscoverMediaTypeOptions,
    val popular: List<DiscoverStoryItem> = emptyList(),
    val latestUpdates: List<DiscoverStoryItem> = emptyList(),
    val topRated: List<DiscoverStoryItem> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val refreshReport: DiscoverRefreshReport? = null,
    val observationFailure: DiscoverUiFailure? = null,
    val refreshFailure: DiscoverUiFailure? = null,
) {
    val globalFailure: DiscoverUiFailure?
        get() = refreshFailure ?: observationFailure

    val hasContent: Boolean
        get() = popular.isNotEmpty() || latestUpdates.isNotEmpty() || topRated.isNotEmpty()
}

data class DiscoverRefreshReport(
    val succeeded: Set<PluginId> = emptySet(),
    val failed: Map<PluginId, String> = emptyMap(),
    val refreshedAtEpochMillis: Map<PluginId, Long?> = emptyMap(),
)

data class DiscoverUiFailure(val code: String, val retryable: Boolean)
