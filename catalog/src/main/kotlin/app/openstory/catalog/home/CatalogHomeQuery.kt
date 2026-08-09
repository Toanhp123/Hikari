package app.openstory.catalog.home

import app.openstory.catalog.ranking.AggregateRanking
import app.openstory.catalog.ranking.CatalogEntryWithStory
import app.openstory.catalog.ranking.RankedCatalogStory
import app.openstory.catalog.repository.CatalogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CatalogHomeQuery(
    repository: CatalogRepository,
    private val ranking: AggregateRanking = AggregateRanking(),
) {
    val rankedStories: Flow<List<RankedCatalogStory>> = repository.observeHomes()
        .map { homes ->
            val entries = homes
                .flatMap { home -> home.sections.flatMap { section -> section.items } }
                .distinctBy { entry -> entry.pluginId to entry.sourceId }
                .map { entry -> CatalogEntryWithStory(entry.storyId, entry) }
            ranking.rank(entries)
        }
}
