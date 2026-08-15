package app.openstory.catalog.home

import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.ranking.AggregateRanking
import app.openstory.catalog.ranking.CatalogEntryWithStory
import app.openstory.catalog.ranking.RankedCatalogStory
import javax.inject.Inject

class CatalogHomeQuery @Inject constructor() {
    private val ranking = AggregateRanking()

    fun rank(homes: List<CatalogHomeSnapshot>): List<RankedCatalogStory> {
        val entries = homes
            .flatMap { home -> home.sections.flatMap { section -> section.items } }
            .distinctBy { entry -> entry.pluginId to entry.sourceId }
            .map { entry -> CatalogEntryWithStory(entry.storyId, entry) }
        return ranking.rank(entries)
    }
}
