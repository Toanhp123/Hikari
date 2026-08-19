package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.ranking.AggregateRanking
import app.openstory.catalog.ranking.CatalogEntryWithStory
import app.openstory.common.id.StoryId

internal fun projectSemanticDiscoverState(
    homes: List<CatalogHomeSnapshot>,
    selectedContentType: ContentType,
    loading: Boolean,
    refreshing: Boolean,
    refreshReport: DiscoverRefreshReport?,
): DiscoverUiState {
    val entriesByStory = selectedEntriesByStory(homes, selectedContentType)
    val popular = projectPopular(homes, selectedContentType)
        .mapNotNull { storyId -> entriesByStory[storyId]?.toPresentationItem() }
    val latestUpdates = projectLatest(homes, selectedContentType)
        .mapNotNull { storyId -> entriesByStory[storyId]?.toPresentationItem() }
    val topRated = projectTopRated(homes, selectedContentType)
        .mapNotNull { storyId -> entriesByStory[storyId]?.toPresentationItem() }

    return DiscoverUiState(
        selectedContentType = selectedContentType,
        mediaTypeOptions = defaultDiscoverMediaTypeOptions,
        popular = popular,
        latestUpdates = latestUpdates,
        topRated = topRated,
        loading = loading,
        refreshing = refreshing,
        refreshReport = refreshReport,
    )
}

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

private fun selectedEntriesByStory(
    homes: List<CatalogHomeSnapshot>,
    selectedContentType: ContentType,
): Map<StoryId, List<CatalogEntry>> = homes
    .asSequence()
    .flatMap { home -> home.sections.asSequence().flatMap { it.items.asSequence() } }
    .filter { entry -> entry.contentType == selectedContentType }
    .groupBy { entry -> entry.pluginId to entry.sourceId }
    .values
    .mapNotNull { duplicates -> duplicates.minWithOrNull(presentationOrder) }
    .groupBy(CatalogEntry::storyId)

private fun projectPopular(
    homes: List<CatalogHomeSnapshot>,
    selectedContentType: ContentType,
): List<StoryId> = semanticFeedContributions(homes, selectedContentType, CatalogFeedKind.POPULAR)
    .groupBy { contribution -> contribution.entry.storyId }
    .map { (storyId, contributions) ->
        val bestRank = contributions.minOf { contribution ->
            contribution.entry.popularityRank ?: (contribution.itemIndex + 1).toLong()
        }
        storyId to bestRank
    }
    .sortedWith(
        compareBy<Pair<StoryId, Long>> { it.second }
            .thenBy { it.first.value },
    )
    .take(MAX_POPULAR)
    .map(Pair<StoryId, Long>::first)

private fun projectLatest(
    homes: List<CatalogHomeSnapshot>,
    selectedContentType: ContentType,
): List<StoryId> = semanticFeedContributions(
    homes,
    selectedContentType,
    CatalogFeedKind.LATEST_UPDATES,
)
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
    .sortedWith(
        compareByDescending<Pair<StoryId, Long>> { it.second }
            .thenBy { it.first.value },
    )
    .take(MAX_LATEST)
    .map(Pair<StoryId, Long>::first)

private fun projectTopRated(
    homes: List<CatalogHomeSnapshot>,
    selectedContentType: ContentType,
): List<StoryId> {
    val entries = semanticFeedContributions(homes, selectedContentType, CatalogFeedKind.TOP_RATED)
        .map(FeedContribution::entry)
        .filter { entry -> entry.score != null }
        .groupBy { entry -> entry.pluginId to entry.sourceId }
        .values
        .mapNotNull { duplicates -> duplicates.minWithOrNull(presentationOrder) }
        .map { entry -> CatalogEntryWithStory(entry.storyId, entry) }

    return AggregateRanking()
        .rank(entries)
        .take(MAX_TOP_RATED)
        .map { ranked -> ranked.storyId }
}

private fun List<CatalogEntry>.toPresentationItem(): DiscoverStoryItem {
    val ordered = sortedWith(presentationOrder)
    val primary = ordered.first()
    val genres = ordered
        .firstOrNull { entry -> entry.genres.isNotEmpty() }
        ?.genres
        .orEmpty()
        .sorted()
        .take(MAX_PRESENTED_GENRES)
    val status = ordered.firstNotNullOfOrNull(CatalogEntry::publicationStatus)
    val latestUpdate = ordered
        .filter { entry -> entry.latestUpdate != null }
        .minWithOrNull(
            compareByDescending<CatalogEntry> { it.latestUpdate!!.atEpochMillis }
                .thenBy { it.pluginId.value }
                .thenBy { it.sourceId },
        )
        ?.latestUpdate
    val score = ordered
        .filter { entry -> entry.score != null }
        .minWithOrNull(
            compareByDescending<CatalogEntry> { entry -> entry.score!!.normalizedValue() }
                .thenBy { it.pluginId.value }
                .thenBy { it.sourceId },
        )
        ?.score

    return DiscoverStoryItem(
        storyId = primary.storyId,
        title = primary.title,
        coverUrl = primary.coverUrl,
        contentType = primary.contentType,
        score = score,
        genres = genres,
        publicationStatus = status,
        latestUpdate = latestUpdate,
    )
}

private fun Score.normalizedValue(): Double = value / scale

private val presentationOrder =
    compareByDescending<CatalogEntry> { !it.coverUrl.isNullOrBlank() }
        .thenByDescending { it.genres.isNotEmpty() }
        .thenByDescending { it.publicationStatus != null }
        .thenByDescending { it.score != null }
        .thenByDescending { it.latestUpdate != null }
        .thenBy { it.pluginId.value }
        .thenBy { it.sourceId }
        .thenBy { it.title }
        .thenBy { it.coverUrl.orEmpty() }
        .thenBy { it.genres.sorted().joinToString(separator = "\u0000") }
        .thenBy { it.publicationStatus?.name.orEmpty() }
        .thenByDescending { it.score?.normalizedValue() ?: Double.NEGATIVE_INFINITY }
        .thenByDescending { it.latestUpdate?.atEpochMillis ?: Long.MIN_VALUE }
        .thenBy { it.latestUpdate?.releaseLabel.orEmpty() }

private const val MAX_POPULAR = 5
private const val MAX_LATEST = 9
private const val MAX_TOP_RATED = 5
private const val MAX_PRESENTED_GENRES = 3
