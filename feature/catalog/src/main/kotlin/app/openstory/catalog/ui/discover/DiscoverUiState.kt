package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.ranking.RankedCatalogStory
import app.openstory.common.id.PluginId

data class DiscoverUiState(
    val catalogs: List<CatalogHomeSnapshot> = emptyList(),
    val rankedStories: List<RankedCatalogStory> = emptyList(),
    val featured: CatalogEntry? = null,
    val quickCategories: List<DiscoverQuickCategory> = emptyList(),
    val shelves: List<DiscoverShelf> = emptyList(),
    val selectedCatalogId: PluginId? = null,
    val selectedSourceId: String? = null,
    val refreshing: Boolean = false,
    val refreshReport: DiscoverRefreshReport? = null,
    val observationFailure: DiscoverUiFailure? = null,
    val refreshFailure: DiscoverUiFailure? = null,
) {
    val globalFailure: DiscoverUiFailure?
        get() = refreshFailure ?: observationFailure
}

data class DiscoverQuickCategory(
    val pluginId: PluginId,
    val sourceId: String,
    val label: String,
) {
    val key: String
        get() = "${pluginId.value}:$sourceId"
}

data class DiscoverShelf(
    val pluginId: PluginId?,
    val sourceId: String,
    val title: String,
    val entries: List<CatalogEntry>,
) {
    val key: String
        get() = pluginId?.let { "${it.value}:$sourceId" } ?: "combined:$sourceId"
}

data class DiscoverRefreshReport(
    val succeeded: Set<PluginId> = emptySet(),
    val failed: Map<PluginId, String> = emptyMap(),
    val refreshedAtEpochMillis: Map<PluginId, Long?> = emptyMap(),
)

data class DiscoverUiFailure(val code: String, val retryable: Boolean)

fun projectDiscoverState(
    catalogs: List<CatalogHomeSnapshot>,
    rankedStories: List<RankedCatalogStory>,
    selectedCatalogId: PluginId? = null,
    selectedSourceId: String? = null,
    refreshing: Boolean = false,
    refreshReport: DiscoverRefreshReport? = null,
): DiscoverUiState {
    val effectiveSelectedCatalogId = selectedCatalogId?.takeIf { selectedId ->
        catalogs.any { it.pluginId == selectedId }
    }
    val effectiveSelectedSourceId = selectedSourceId?.takeIf { sourceId ->
        catalogs.firstOrNull { it.pluginId == effectiveSelectedCatalogId }
            ?.sections
            ?.any { it.sourceId == sourceId } == true
    }
    val shelves = if (effectiveSelectedCatalogId == null) {
        val combinedEntries = rankedStories.mapNotNull { ranked ->
            ranked.contributions
                .asSequence()
                .map { it.entry }
                .minWithOrNull(catalogEntryPresentationOrder)
        }
        val combined = if (combinedEntries.isEmpty()) emptyList() else listOf(
            DiscoverShelf(null, "ranked", "Across catalogs", combinedEntries),
        )
        combined + catalogs.flatMap { catalog ->
            catalog.sections.map { section ->
                DiscoverShelf(catalog.pluginId, section.sourceId, section.title, section.items)
            }
        }
    } else {
        catalogs.firstOrNull { it.pluginId == effectiveSelectedCatalogId }
            ?.sections
            ?.filter { section -> effectiveSelectedSourceId == null || section.sourceId == effectiveSelectedSourceId }
            ?.map { section ->
                DiscoverShelf(effectiveSelectedCatalogId, section.sourceId, section.title, section.items)
            }
            .orEmpty()
    }
    return DiscoverUiState(
        catalogs = catalogs,
        rankedStories = rankedStories,
        featured = selectFeatured(rankedStories),
        quickCategories = catalogs.flatMap { catalog ->
            catalog.sections.map { section ->
                DiscoverQuickCategory(catalog.pluginId, section.sourceId, section.title)
            }
        }.distinctBy(DiscoverQuickCategory::key),
        shelves = shelves,
        selectedCatalogId = effectiveSelectedCatalogId,
        selectedSourceId = effectiveSelectedSourceId,
        refreshing = refreshing,
        refreshReport = refreshReport,
    )
}

fun selectFeatured(rankedStories: List<RankedCatalogStory>): CatalogEntry? = rankedStories
    .asSequence()
    .flatMap { ranked ->
        ranked.contributions.asSequence().map { contribution ->
            FeaturedCandidate(contribution.entry, ranked.orderingScore)
        }
    }
    .minWithOrNull(featuredCandidateOrder)
    ?.entry

private data class FeaturedCandidate(val entry: CatalogEntry, val score: Double)

private val featuredCandidateOrder =
    compareByDescending<FeaturedCandidate> { it.entry.hasUsableArtwork() }
        .thenByDescending { it.score }
        .thenBy { it.entry.pluginId.value }
        .thenBy { it.entry.sourceId }

private val catalogEntryPresentationOrder =
    compareByDescending<CatalogEntry> { it.hasUsableArtwork() }
        .thenByDescending { entry ->
            entry.score?.let { score -> score.value / score.scale } ?: Double.NEGATIVE_INFINITY
        }
        .thenBy { it.pluginId.value }
        .thenBy { it.sourceId }

private fun CatalogEntry.hasUsableArtwork(): Boolean = !coverUrl.isNullOrBlank()
