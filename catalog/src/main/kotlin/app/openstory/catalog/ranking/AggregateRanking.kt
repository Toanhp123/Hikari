package app.openstory.catalog.ranking

import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class CatalogEntryWithStory(val storyId: StoryId, val entry: CatalogEntry)
data class CatalogRankContribution(val entry: CatalogEntry, val normalizedScore: Double?, val priorityWeight: Double)
data class RankedCatalogStory(val storyId: StoryId, val orderingScore: Double, val contributions: List<CatalogRankContribution>)

class AggregateRanking(private val catalogWeights: Map<PluginId, Double> = emptyMap()) {
    init { require(catalogWeights.values.all { it.isFinite() && it > 0.0 }) }

    fun rank(items: List<CatalogEntryWithStory>): List<RankedCatalogStory> = items.groupBy { it.storyId }
        .map { (id, grouped) ->
            val contributions = grouped.map { item ->
                CatalogRankContribution(
                    item.entry,
                    item.entry.score?.let { score -> score.value / score.scale },
                    catalogWeights[item.entry.pluginId] ?: 1.0,
                )
            }.sortedWith(compareBy<CatalogRankContribution> { it.entry.pluginId.value }.thenBy { it.entry.sourceId })
            val scored = contributions.filter { it.normalizedScore != null }
            val weight = scored.sumOf { it.priorityWeight }
            RankedCatalogStory(id, if (weight == 0.0) 0.0 else scored.sumOf { it.normalizedScore!! * it.priorityWeight } / weight, contributions)
        }.sortedWith(compareByDescending<RankedCatalogStory> { it.orderingScore }.thenBy { it.storyId.value })
}
