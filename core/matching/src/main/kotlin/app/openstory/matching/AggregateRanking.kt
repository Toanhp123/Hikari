package app.openstory.matching

import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.PluginId
import app.openstory.model.StoryId

data class CatalogRankContribution(
    val entry: CatalogEntry,
    val normalizedScore: Double?,
    val priorityWeight: Double,
)

data class RankedCatalogStory(
    val storyId: StoryId,
    val orderingScore: Double,
    val contributions: List<CatalogRankContribution>,
)

class AggregateRanking(
    private val catalogWeights: Map<PluginId, Double> = emptyMap(),
) {
    init {
        require(catalogWeights.values.all { weight -> weight.isFinite() && weight > 0.0 }) {
            "Catalog ranking weights must be finite and positive"
        }
    }

    fun rank(items: List<CatalogEntryWithStory>): List<RankedCatalogStory> = items
        .groupBy(CatalogEntryWithStory::storyId)
        .map { (storyId, storyItems) -> rankStory(storyId, storyItems) }
        .sortedWith(
            compareByDescending<RankedCatalogStory>(RankedCatalogStory::orderingScore)
                .thenBy { ranked -> ranked.storyId.value },
        )

    private fun rankStory(
        storyId: StoryId,
        items: List<CatalogEntryWithStory>,
    ): RankedCatalogStory {
        val contributions = items
            .map { item -> contribution(item.entry) }
            .sortedWith(
                compareBy<CatalogRankContribution> { contribution ->
                    contribution.entry.catalogPluginId.value
                }.thenBy { contribution -> contribution.entry.externalStoryId }
                    .thenBy { contribution -> contribution.entry.id.value },
            )
        val scored = contributions.filter { contribution -> contribution.normalizedScore != null }
        val totalWeight = scored.sumOf(CatalogRankContribution::priorityWeight)
        val weightedScore = scored.sumOf { contribution ->
            requireNotNull(contribution.normalizedScore) * contribution.priorityWeight
        }

        return RankedCatalogStory(
            storyId = storyId,
            orderingScore = if (totalWeight == 0.0) 0.0 else weightedScore / totalWeight,
            contributions = contributions,
        )
    }

    private fun contribution(entry: CatalogEntry): CatalogRankContribution = CatalogRankContribution(
        entry = entry,
        normalizedScore = normalizedScore(entry),
        priorityWeight = catalogWeights[entry.catalogPluginId] ?: DEFAULT_WEIGHT,
    )

    private fun normalizedScore(entry: CatalogEntry): Double? {
        val score = entry.score
        val scoreScale = entry.scoreScale

        return if (score != null && scoreScale != null) {
            score / scoreScale
        } else {
            null
        }
    }

    private companion object {
        const val DEFAULT_WEIGHT = 1.0
    }
}
