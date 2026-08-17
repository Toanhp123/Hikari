package app.openstory.catalog.ui.discover

import app.openstory.catalog.home.CatalogHomeQuery
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.ranking.RankedCatalogStory
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.common.id.PluginId
import javax.inject.Inject
import kotlinx.coroutines.withContext

internal data class DiscoverPreparedContent(
    val catalogs: List<CatalogHomeSnapshot>,
    val rankedStories: List<RankedCatalogStory>,
)

class DiscoverProjectionPipeline @Inject constructor(
    private val query: CatalogHomeQuery,
    dispatchers: AppDispatchers,
) {
    private val dispatcher = dispatchers.default

    internal suspend fun prepare(homes: List<CatalogHomeSnapshot>): DiscoverPreparedContent =
        withContext(dispatcher) {
            DiscoverPreparedContent(
                catalogs = homes,
                rankedStories = query.rank(homes),
            )
        }

    internal suspend fun project(
        content: DiscoverPreparedContent,
        selectedCatalogId: PluginId?,
        selectedSourceId: String?,
        refreshing: Boolean,
        refreshReport: DiscoverRefreshReport?,
    ): DiscoverUiState = withContext(dispatcher) {
        projectDiscoverState(
            catalogs = content.catalogs,
            rankedStories = content.rankedStories,
            selectedCatalogId = selectedCatalogId,
            selectedSourceId = selectedSourceId,
            refreshing = refreshing,
            refreshReport = refreshReport,
        )
    }
}
