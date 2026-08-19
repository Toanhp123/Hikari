package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.ranking.AggregateRanking
import app.openstory.catalog.ranking.CatalogEntryWithStory
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.common.id.PluginId
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
        legacySelectedCatalogId: PluginId? = null,
        legacySelectedSourceId: String? = null,
    ): DiscoverUiState = withContext(dispatcher) {
        val semantic = projectSemanticDiscoverState(
            homes = content.homes,
            selectedContentType = selectedContentType,
            loading = loading,
            refreshing = refreshing,
            refreshReport = refreshReport,
        )

        // Task 7 removes these source-centric fields. Keeping them populated here makes
        // Tasks 4-6 safe to apply independently while the old DiscoverScreen still exists.
        val rankedStories = rankForLegacyPresentation(content.homes)
        val legacy = projectDiscoverState(
            catalogs = content.homes,
            rankedStories = rankedStories,
            selectedCatalogId = legacySelectedCatalogId,
            selectedSourceId = legacySelectedSourceId,
            refreshing = refreshing,
            refreshReport = refreshReport,
        )

        semantic.copy(
            catalogs = legacy.catalogs,
            rankedStories = legacy.rankedStories,
            featured = legacy.featured,
            quickCategories = legacy.quickCategories,
            shelves = legacy.shelves,
            selectedCatalogId = legacy.selectedCatalogId,
            selectedSourceId = legacy.selectedSourceId,
        )
    }
}

private fun rankForLegacyPresentation(homes: List<CatalogHomeSnapshot>) = AggregateRanking().rank(
    homes
        .flatMap { home -> home.sections.flatMap { section -> section.items } }
        .distinctBy { entry -> entry.pluginId to entry.sourceId }
        .map { entry -> CatalogEntryWithStory(entry.storyId, entry) },
)
