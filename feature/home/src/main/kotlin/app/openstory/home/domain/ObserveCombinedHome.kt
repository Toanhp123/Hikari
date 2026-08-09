package app.openstory.home.domain

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.ranking.AggregateRanking
import app.openstory.catalog.ranking.CatalogEntryWithStory
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.id.PluginId
import app.openstory.home.model.HomeCatalog
import app.openstory.home.model.HomeCatalogCard
import app.openstory.home.model.HomeCatalogSection
import app.openstory.home.model.HomeCombinedCard
import app.openstory.home.model.HomeCombinedSource
import app.openstory.home.model.HomeSectionMembership
import app.openstory.home.model.HomeUiModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveCombinedHome(
    private val repository: CatalogRepository,
    private val ranking: AggregateRanking = AggregateRanking(),
    private val enabledCatalogIds: suspend () -> Set<PluginId>,
    private val mapper: CatalogSnapshotMapper = CatalogSnapshotMapper(),
) {
    operator fun invoke(): Flow<HomeUiModel> = repository.observeHomes().map { snapshots ->
        toHomeUiModel(snapshots.filter { it.pluginId in enabledCatalogIds() })
    }

    fun catalog(pluginId: PluginId): Flow<HomeCatalog?> = repository.observeHomes().map { homes ->
        homes.firstOrNull { it.pluginId == pluginId }?.let(mapper::map)
    }

    private fun toHomeUiModel(snapshots: List<CatalogHomeSnapshot>): HomeUiModel {
        val ordered = snapshots.sortedBy { it.pluginId.value }
        val memberships = buildMap<Pair<PluginId, String>, List<HomeSectionMembership>> {
            ordered.forEach { snapshot ->
                snapshot.sections.forEachIndexed { sectionPosition, section ->
                    section.items.forEachIndexed { itemPosition, entry ->
                        val key = entry.pluginId to entry.sourceId
                        val membership = HomeSectionMembership(section.sourceId, section.title, sectionPosition, itemPosition)
                        put(key, get(key).orEmpty() + membership)
                    }
                }
            }
        }
        val entries = ordered.flatMap { it.sections.flatMap { section -> section.items } }
            .distinctBy { it.pluginId to it.sourceId }
            .map { CatalogEntryWithStory(it.storyId, it) }
        val combined = ranking.rank(entries).map { ranked ->
            HomeCombinedCard(
                ranked.storyId,
                ranked.orderingScore,
                ranked.contributions.map { contribution ->
                    contribution.entry.toCombinedSource(
                        contribution.normalizedScore,
                        contribution.priorityWeight,
                        memberships[contribution.entry.pluginId to contribution.entry.sourceId].orEmpty(),
                    )
                },
            )
        }
        return HomeUiModel(combined, ordered.map(mapper::map))
    }

    private fun CatalogEntry.toCombinedSource(
        normalizedScore: Double?,
        priorityWeight: Double,
        sections: List<HomeSectionMembership>,
    ) = HomeCombinedSource(
        pluginId = pluginId,
        sourceId = sourceId,
        title = title,
        contentType = contentType,
        authors = authors,
        coverReference = coverUrl,
        score = score?.value,
        scoreScale = score?.scale,
        normalizedScore = normalizedScore,
        priorityWeight = priorityWeight,
        sections = sections,
    )
}
