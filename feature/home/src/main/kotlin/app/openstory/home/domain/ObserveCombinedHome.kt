package app.openstory.home.domain

import app.openstory.database.repository.CatalogRepository
import app.openstory.home.model.HomeCatalog
import app.openstory.home.model.HomeCatalogCard
import app.openstory.home.model.HomeCatalogSection
import app.openstory.home.model.HomeCombinedCard
import app.openstory.home.model.HomeCombinedSource
import app.openstory.home.model.HomeSectionMembership
import app.openstory.home.model.HomeUiModel
import app.openstory.matching.AggregateRanking
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.CatalogHomeSnapshot
import app.openstory.model.PluginId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class ObserveCombinedHome(
    private val repository: CatalogRepository,
    private val ranking: AggregateRanking,
    private val enabledCatalogIds: suspend () -> Set<PluginId>,
) {
    operator fun invoke(): Flow<HomeUiModel> = flow {
        val visibleCatalogIds = enabledCatalogIds()
        repository.observeCatalogHomes().collect { snapshots ->
            emit(
                toHomeUiModel(
                    snapshots.filter { snapshot -> snapshot.pluginId in visibleCatalogIds },
                ),
            )
        }
    }

    fun catalog(pluginId: PluginId): Flow<HomeCatalog?> = repository.observeCatalogHome(pluginId)
        .map { snapshot -> snapshot?.toCatalog() }

    private fun toHomeUiModel(snapshots: List<CatalogHomeSnapshot>): HomeUiModel {
        val orderedSnapshots = snapshots.sortedBy { snapshot -> snapshot.pluginId.value }
        val sourceItems = uniqueSourceItems(orderedSnapshots)
        val memberships = sectionMemberships(orderedSnapshots)
        val combined = ranking.rank(sourceItems).map { ranked ->
            HomeCombinedCard(
                storyId = ranked.storyId,
                orderingScore = ranked.orderingScore,
                sources = ranked.contributions.map { contribution ->
                    contribution.entry.toCombinedSource(
                        normalizedScore = contribution.normalizedScore,
                        priorityWeight = contribution.priorityWeight,
                        sections = memberships[sourceKey(contribution.entry)].orEmpty(),
                    )
                },
            )
        }

        return HomeUiModel(
            combined = combined,
            catalogs = orderedSnapshots.map { snapshot -> snapshot.toCatalog() },
        )
    }

    private fun uniqueSourceItems(
        snapshots: List<CatalogHomeSnapshot>,
    ): List<CatalogEntryWithStory> = snapshots
        .asSequence()
        .flatMap { snapshot -> snapshot.sections.asSequence() }
        .flatMap { section -> section.items.asSequence() }
        .distinctBy { item -> sourceKey(item.entry) }
        .toList()

    private fun sectionMemberships(
        snapshots: List<CatalogHomeSnapshot>,
    ): Map<SourceKey, List<HomeSectionMembership>> = buildMap {
        snapshots.forEach { snapshot ->
            snapshot.sections.forEachIndexed { sectionPosition, section ->
                section.items.forEachIndexed { itemPosition, item ->
                    val key = sourceKey(item.entry)
                    val membership = HomeSectionMembership(
                        sourceId = section.sourceId,
                        title = section.title,
                        sectionPosition = sectionPosition,
                        itemPosition = itemPosition,
                    )
                    put(key, get(key).orEmpty() + membership)
                }
            }
        }
    }

    private fun CatalogHomeSnapshot.toCatalog(): HomeCatalog = HomeCatalog(
        pluginId = pluginId,
        pluginVersion = pluginVersion,
        refreshedAtEpochMillis = refreshedAtEpochMillis,
        sections = sections.map { section ->
            HomeCatalogSection(
                sourceId = section.sourceId,
                title = section.title,
                items = section.items.map { item ->
                    val entry = item.entry
                    HomeCatalogCard(
                        storyId = item.storyId,
                        pluginId = entry.catalogPluginId,
                        pluginVersion = entry.pluginVersion,
                        sourceId = entry.externalStoryId,
                        title = entry.title,
                        contentType = entry.contentType,
                        authors = entry.authors,
                        coverReference = entry.coverReference,
                        score = entry.score,
                        scoreScale = entry.scoreScale,
                        fetchedAtEpochMillis = entry.fetchedAtEpochMillis,
                    )
                },
            )
        },
    )

    private fun CatalogEntry.toCombinedSource(
        normalizedScore: Double?,
        priorityWeight: Double,
        sections: List<HomeSectionMembership>,
    ): HomeCombinedSource = HomeCombinedSource(
        pluginId = catalogPluginId,
        pluginVersion = pluginVersion,
        sourceId = externalStoryId,
        title = title,
        contentType = contentType,
        authors = authors,
        coverReference = coverReference,
        score = score,
        scoreScale = scoreScale,
        normalizedScore = normalizedScore,
        priorityWeight = priorityWeight,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
        sections = sections,
    )

    private fun sourceKey(entry: CatalogEntry): SourceKey = SourceKey(
        pluginId = entry.catalogPluginId,
        sourceId = entry.externalStoryId,
    )

    private data class SourceKey(
        val pluginId: PluginId,
        val sourceId: String,
    )
}
