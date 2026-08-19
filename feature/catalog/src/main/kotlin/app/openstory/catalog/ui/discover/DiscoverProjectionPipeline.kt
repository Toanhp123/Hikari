package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.common.dispatchers.AppDispatchers
import javax.inject.Inject
import kotlinx.coroutines.withContext

internal data class DiscoverPreparedContent(
    val homes: List<CatalogHomeSnapshot>,
)

class DiscoverProjectionPipeline @Inject constructor(
    dispatchers: AppDispatchers,
) {
    private val dispatcher = dispatchers.default

    internal suspend fun prepare(homes: List<CatalogHomeSnapshot>): DiscoverPreparedContent =
        withContext(dispatcher) {
            DiscoverPreparedContent(homes = homes)
        }

    internal suspend fun project(
        content: DiscoverPreparedContent,
        selectedContentType: ContentType,
        loading: Boolean,
        refreshing: Boolean,
        refreshReport: DiscoverRefreshReport?,
    ): DiscoverUiState = withContext(dispatcher) {
        projectSemanticDiscoverState(
            homes = content.homes,
            selectedContentType = selectedContentType,
            loading = loading,
            refreshing = refreshing,
            refreshReport = refreshReport,
        )
    }
}
