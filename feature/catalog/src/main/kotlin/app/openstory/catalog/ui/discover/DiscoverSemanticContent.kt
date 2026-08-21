package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.ranking.AggregateRanking
import app.openstory.catalog.ranking.CatalogEntryWithStory
import app.openstory.common.id.StoryId

internal data class DiscoverSemanticContent(
    val selectedContentType: ContentType,
    val popular: List<DiscoverStoryItem>,
    val latestUpdates: List<DiscoverStoryItem>,
    val topRated: List<DiscoverStoryItem>,
    val sourceEmpty: Boolean,
) {
    fun toUiState(
        loading: Boolean,
        refreshing: Boolean,
        refreshReport: DiscoverRefreshReport?,
    ) = DiscoverUiState(
        selectedContentType = selectedContentType,
        mediaTypeOptions = defaultDiscoverMediaTypeOptions,
        popular = popular,
        latestUpdates = latestUpdates,
        topRated = topRated,
        loading = loading && sourceEmpty,
        refreshing = refreshing,
        refreshReport = refreshReport,
    )

    companion object {
        fun empty(contentType: ContentType) = DiscoverSemanticContent(
            selectedContentType = contentType,
            popular = emptyList(),
            latestUpdates = emptyList(),
            topRated = emptyList(),
            sourceEmpty = true,
        )
    }
}

internal fun projectSemanticDiscoverContent(
    homes: List<CatalogHomeSnapshot>,
    projections: List<CatalogStoryProjection>,
    selectedContentType: ContentType,
): DiscoverSemanticContent {
    val projectionByStory = projections
        .asSequence()
        .filter { it.contentType == selectedContentType }
        .associateBy(CatalogStoryProjection::storyId)
    val popular = projectPopular(homes, selectedContentType)
        .mapNotNull { projectionByStory[it]?.toDiscoverItem() }
    val latestUpdates = projectLatest(homes, selectedContentType)
        .mapNotNull { projectionByStory[it]?.toDiscoverItem() }
    val topRated = projectTopRated(homes, selectedContentType)
        .mapNotNull { projectionByStory[it]?.toDiscoverItem() }

    return DiscoverSemanticContent(
        selectedContentType = selectedContentType,
        popular = popular,
        latestUpdates = latestUpdates,
        topRated = topRated,
        sourceEmpty = homes.isEmpty(),
    )
}

internal fun projectSemanticDiscoverState(
    homes: List<CatalogHomeSnapshot>,
    projections: List<CatalogStoryProjection>,
    selectedContentType: ContentType,
    loading: Boolean,
    refreshing: Boolean,
    refreshReport: DiscoverRefreshReport?,
): DiscoverUiState = projectSemanticDiscoverContent(
    homes = homes,
    projections = projections,
    selectedContentType = selectedContentType,
).toUiState(
    loading = loading,
    refreshing = refreshing,
    refreshReport = refreshReport,
)

private data class FeedContribution(
    val entry: CatalogEntry,
    val itemIndex: Int,
)

private fun semanticFeedContributions(
    homes: List<CatalogHomeSnapshot>,
    selectedContentType: ContentType,
    feedKind: CatalogFeedKind,
): List<FeedContribution> = homes
    .asSequence()
    .flatMap { home ->
        home.sections.asSequence()
            .filter { section -> section.kind == feedKind }
            .flatMap { section ->
                section.items.asSequence().mapIndexedNotNull { index, entry ->
                    entry.takeIf { it.contentType == selectedContentType }
                        ?.let { FeedContribution(it, index) }
                }
            }
    }
    .toList()

private fun projectPopular(
    homes: List<CatalogHomeSnapshot>,
    selectedContentType: ContentType,
): List<StoryId> = semanticFeedContributions(homes, selectedContentType, CatalogFeedKind.POPULAR)
    .groupBy { it.entry.storyId }
    .map { (storyId, contributions) ->
        val bestRank = contributions.minOf { contribution ->
            contribution.entry.popularityRank ?: (contribution.itemIndex + 1).toLong()
        }
        storyId to bestRank
    }
    .sortedWith(compareBy<Pair<StoryId, Long>> { it.second }.thenBy { it.first.value })
    .take(MAX_POPULAR)
    .map(Pair<StoryId, Long>::first)

private fun projectLatest(
    homes: List<CatalogHomeSnapshot>,
    selectedContentType: ContentType,
): List<StoryId> = semanticFeedContributions(homes, selectedContentType, CatalogFeedKind.LATEST_UPDATES)
    .mapNotNull { contribution ->
        contribution.entry.latestUpdate?.let { update ->
            Triple(contribution.entry.storyId, update.atEpochMillis, contribution.entry)
        }
    }
    .groupBy { it.first }
    .map { (storyId, candidates) ->
        val newest = candidates.minWithOrNull(
            compareByDescending<Triple<StoryId, Long, CatalogEntry>> { it.second }
                .thenBy { it.third.pluginId.value }
                .thenBy { it.third.sourceId },
        )
        storyId to checkNotNull(newest).second
    }
    .sortedWith(compareByDescending<Pair<StoryId, Long>> { it.second }.thenBy { it.first.value })
    .take(MAX_LATEST)
    .map(Pair<StoryId, Long>::first)

private fun projectTopRated(
    homes: List<CatalogHomeSnapshot>,
    selectedContentType: ContentType,
): List<StoryId> {
    val entries = semanticFeedContributions(homes, selectedContentType, CatalogFeedKind.TOP_RATED)
        .map(FeedContribution::entry)
        .filter { it.score != null }
        .groupBy { it.pluginId to it.sourceId }
        .values
        .mapNotNull { duplicates ->
            duplicates.minWithOrNull(compareBy<CatalogEntry> { it.pluginId.value }.thenBy { it.sourceId })
        }
        .map { CatalogEntryWithStory(it.storyId, it) }

    return AggregateRanking().rank(entries)
        .take(MAX_TOP_RATED)
        .map { it.storyId }
}

private fun CatalogStoryProjection.toDiscoverItem(): DiscoverStoryItem = DiscoverStoryItem(
    storyId = storyId,
    title = title,
    coverUrl = coverUrl,
    contentType = contentType,
    score = score?.let { Score(it.normalizedValue * PRESENTATION_SCORE_SCALE, PRESENTATION_SCORE_SCALE) },
    genres = emptyList(),
    publicationStatus = publicationStatus,
    latestUpdate = latestUpdate,
)

private const val MAX_POPULAR = 5
private const val MAX_LATEST = 9
private const val MAX_TOP_RATED = 5
private const val PRESENTATION_SCORE_SCALE = 10.0
