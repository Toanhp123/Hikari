package app.openstory.home.domain

import app.openstory.matching.CatalogStoryResolver
import app.openstory.model.CanonicalStory
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryId
import app.openstory.model.CatalogSnapshotItem
import app.openstory.model.StoryId
import app.openstory.plugin.api.Page
import app.openstory.plugin.api.catalog.CatalogCard
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.host.HostedPlugin

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
            page.page.items.asSequence().mapIndexed { index, card ->
                SearchSourceCandidate(
                    hosted = page.hosted,
                    card = card,
                    sourcePosition = index,
                )
            }
        }
        .sortedWith(
            compareBy<SearchSourceCandidate> { candidate -> candidate.hosted.id.value }
                .thenBy(SearchSourceCandidate::sourcePosition),
        )
        .toList()

    private fun resolveSource(
        candidate: SearchSourceCandidate,
        order: Int,
        workingCandidates: MutableList<CanonicalStory>,
    ): ResolvedSearchSource {
        val resolution = resolver.resolve(
            pluginId = candidate.hosted.id,
            source = candidate.card.toSnapshotItem(),
            candidates = workingCandidates,
        )
        workingCandidates.attachSearchEntry(
            storyId = resolution.storyId,
            card = candidate.card,
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
        card: CatalogCard,
        entry: CatalogEntry,
    ) {
        val index = indexOfFirst { candidate -> candidate.id == storyId }
        if (index < 0) {
            add(
                CanonicalStory(
                    id = storyId,
                    contentType = card.contentType,
                    preferredTitle = card.title,
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
    val hosted: HostedPlugin<CatalogPlugin>,
    val page: Page<CatalogCard>,
)

private data class SearchSourceCandidate(
    val hosted: HostedPlugin<CatalogPlugin>,
    val card: CatalogCard,
    val sourcePosition: Int,
)

private data class ResolvedSearchSource(
    val storyId: StoryId,
    val order: Int,
    val source: SearchResultSource,
)

private fun CatalogCard.toSnapshotItem(): CatalogSnapshotItem = CatalogSnapshotItem(
    sourceId = sourceId,
    title = title,
    contentType = contentType,
    authors = authors,
    coverReference = image?.url,
    score = score?.value,
    scoreScale = score?.scale,
)

private fun SearchSourceCandidate.toCatalogEntry(): CatalogEntry = CatalogEntry(
    id = CatalogEntryId("search:${hosted.id.value}:${card.sourceId}"),
    catalogPluginId = hosted.id,
    externalStoryId = card.sourceId,
    sourceUrl = null,
    title = card.title,
    aliases = emptySet(),
    authors = card.authors.toSet(),
    description = null,
    genres = emptySet(),
    contentType = card.contentType,
    languageTags = emptySet(),
    coverReference = card.image?.url,
    publicationStatus = null,
    score = card.score?.value,
    scoreScale = card.score?.scale,
    popularityRank = null,
    pluginVersion = hosted.version,
    fetchedAtEpochMillis = 0L,
)

private fun SearchSourceCandidate.toResultSource(): SearchResultSource = SearchResultSource(
    pluginId = hosted.id,
    pluginVersion = hosted.version,
    sourceId = card.sourceId,
    title = card.title,
    contentType = card.contentType,
    authors = card.authors.toSet(),
    coverReference = card.image?.url,
    score = card.score?.value,
    scoreScale = card.score?.scale,
)
