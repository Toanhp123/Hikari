package app.openstory.home.domain

import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.SourceItem
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.matching.CatalogStoryResolver
import app.openstory.model.CanonicalStory
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryId
import app.openstory.model.CatalogSnapshotItem
import app.openstory.model.StoryId

internal class SearchCanonicalizer(
    private val resolver: CatalogStoryResolver,
) {
    fun canonicalize(
        pages: List<SearchCatalogPage>,
        initialCandidates: List<CanonicalStory>,
    ): List<SearchResultCard> {
        val workingCandidates = initialCandidates.toMutableList()
        val resolvedSources = sourceCandidates(pages)
            .mapIndexed { order, candidate ->
                resolveSource(candidate, order, workingCandidates)
            }

        return resolvedSources.toCards(workingCandidates)
    }

    private fun sourceCandidates(pages: List<SearchCatalogPage>): List<SearchSourceCandidate> = pages
        .asSequence()
        .flatMap { page ->
            page.page.items.asSequence().mapIndexed { index, item ->
                SearchSourceCandidate(
                    source = page.source,
                    item = item,
                    sourcePosition = index,
                )
            }
        }
        .sortedWith(
            compareBy<SearchSourceCandidate> { candidate -> candidate.source.pluginId.value }
                .thenBy(SearchSourceCandidate::sourcePosition),
        )
        .toList()

    private fun resolveSource(
        candidate: SearchSourceCandidate,
        order: Int,
        workingCandidates: MutableList<CanonicalStory>,
    ): ResolvedSearchSource {
        val resolution = resolver.resolve(
            pluginId = candidate.source.pluginId,
            source = candidate.item.toSnapshotItem(),
            candidates = workingCandidates,
        )
        workingCandidates.attachSearchEntry(
            storyId = resolution.storyId,
            item = candidate.item,
            entry = candidate.toCatalogEntry(),
        )
        return ResolvedSearchSource(
            storyId = resolution.storyId,
            order = order,
            source = candidate.toResultSource(),
        )
    }

    private fun MutableList<CanonicalStory>.attachSearchEntry(
        storyId: StoryId,
        item: SourceItem,
        entry: CatalogEntry,
    ) {
        val index = indexOfFirst { candidate -> candidate.id == storyId }
        if (index < 0) {
            add(
                CanonicalStory(
                    id = storyId,
                    contentType = item.contentType.toModel(),
                    preferredTitle = item.title,
                    aliases = emptySet(),
                    catalogEntries = listOf(entry),
                ),
            )
            return
        }

        val current = get(index)
        val sourceAlreadyKnown = current.catalogEntries.any { existing ->
            existing.catalogPluginId == entry.catalogPluginId &&
                existing.externalStoryId == entry.externalStoryId
        }
        if (!sourceAlreadyKnown) {
            set(index, current.copy(catalogEntries = current.catalogEntries + entry))
        }
    }

    private fun List<ResolvedSearchSource>.toCards(
        candidates: List<CanonicalStory>,
    ): List<SearchResultCard> = groupBy(ResolvedSearchSource::storyId)
        .map { (storyId, sources) ->
            val canonical = candidates.firstOrNull { candidate -> candidate.id == storyId }
            val firstSource = sources.first().source
            SearchResultCard(
                storyId = storyId,
                title = canonical?.preferredTitle ?: firstSource.title,
                contentType = canonical?.contentType ?: firstSource.contentType,
                sources = sources
                    .sortedBy { source -> source.source.pluginId.value }
                    .map(ResolvedSearchSource::source),
            ) to sources.minOf(ResolvedSearchSource::order)
        }
        .sortedWith(
            compareBy<Pair<SearchResultCard, Int>> { (_, order) -> order }
                .thenBy { (card, _) -> card.storyId.value },
        )
        .map { (card, _) -> card }
}

internal data class SearchCatalogPage(
    val source: CatalogSource,
    val page: SourceSearchPage,
)

private data class SearchSourceCandidate(
    val source: CatalogSource,
    val item: SourceItem,
    val sourcePosition: Int,
)

private data class ResolvedSearchSource(
    val storyId: StoryId,
    val order: Int,
    val source: SearchResultSource,
)

private fun SourceItem.toSnapshotItem(): CatalogSnapshotItem = CatalogSnapshotItem(
    sourceId = sourceId,
    title = title,
    contentType = contentType.toModel(),
    authors = authors.toList(),
    coverReference = coverUrl,
    score = scoreValue,
    scoreScale = scoreScale,
)

private fun SearchSourceCandidate.toCatalogEntry(): CatalogEntry = CatalogEntry(
    id = CatalogEntryId("search:${source.pluginId.value}:${item.sourceId}"),
    catalogPluginId = source.pluginId,
    externalStoryId = item.sourceId,
    sourceUrl = null,
    title = item.title,
    aliases = emptySet(),
    authors = item.authors,
    description = null,
    genres = emptySet(),
    contentType = item.contentType.toModel(),
    languageTags = emptySet(),
    coverReference = item.coverUrl,
    publicationStatus = null,
    score = item.scoreValue,
    scoreScale = item.scoreScale,
    popularityRank = null,
    pluginVersion = source.version,
    fetchedAtEpochMillis = 0L,
)

private fun SearchSourceCandidate.toResultSource(): SearchResultSource = SearchResultSource(
    pluginId = source.pluginId,
    pluginVersion = source.version,
    sourceId = item.sourceId,
    title = item.title,
    contentType = item.contentType.toModel(),
    authors = item.authors,
    coverReference = item.coverUrl,
    score = item.scoreValue,
    scoreScale = item.scoreScale,
)
