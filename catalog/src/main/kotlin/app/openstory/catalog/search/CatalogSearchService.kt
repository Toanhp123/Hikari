package app.openstory.catalog.search

import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.home.toModel
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.matching.CatalogMatchIndex
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.matching.StoryResolution
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.projection.toProjection
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.repository.CatalogSearchSummaryMutation
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceFailure
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceItem
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class CatalogSearchService @Inject constructor(
    private val sources: CatalogSourceRegistry,
    private val repository: CatalogRepository,
    private val matcher: StoryMatcher,
    private val clock: Clock,
    private val bootstrap: CanonicalBootstrapUseCase,
    private val filterCache: CatalogFilterCache,
) {
    suspend fun filters(): List<CatalogSearchFilterGroup> = supervisorScope {
        val enabledSources = sources.enabled().sortedBy { it.pluginId.value }
        val enabledKeys = enabledSources
            .map { source -> CatalogFilterCacheKey(source.pluginId, source.version) }
            .toSet()
        filterCache.retainEnabled(enabledKeys)
        enabledSources.map { source ->
            async {
                val key = CatalogFilterCacheKey(source.pluginId, source.version)
                filterCache.get(key) ?: loadFilterGroup(source)?.also { group ->
                    filterCache.put(key, group)
                } ?: CatalogSearchFilterGroup(source.pluginId, emptyList())
            }
        }.awaitAll()
    }

    private suspend fun loadFilterGroup(source: CatalogSource): CatalogSearchFilterGroup? = try {
        when (val result = source.filters()) {
            is CatalogSourceResult.Success -> CatalogSearchFilterGroup(source.pluginId, result.value)
            is CatalogSourceResult.Failure -> null
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    suspend fun search(request: CatalogSearchRequest): CatalogSearchResult {
        var matchIndex = CatalogMatchIndex(matcher, repository.matchSnapshot().candidates)
        val cardsByStory = linkedMapOf<StoryId, MutableList<CatalogSearchSourceCard>>()
        val failures = mutableListOf<CatalogSearchFailure>()
        val fetched = supervisorScope {
            sources.enabled().sortedBy { it.pluginId.value }
                .map { source -> async { source to fetch(source, request) } }
                .awaitAll()
        }
        fetched.forEach { (source, result) ->
            when (result) {
                is CatalogSourceResult.Failure -> failures += result.toFailure(source.pluginId)
                is CatalogSourceResult.Success -> {
                    val attempt = runCatchingProjection(source, result.value, matchIndex)
                    if (attempt == null) {
                        failures += CatalogSearchFailure(source.pluginId, INVALID_SOURCE_CODE, retryable = false)
                    } else {
                        when (val commit = repository.commitSearchSummaries(attempt.mutation)) {
                            is Outcome.Failure -> failures += CatalogSearchFailure(
                                source.pluginId,
                                commit.error.code,
                                commit.error.retryable,
                            )
                            is Outcome.Success -> {
                                matchIndex = CatalogMatchIndex(matcher, repository.matchSnapshot().candidates)
                                attempt.cards.forEach { (key, card) ->
                                    val durableStoryId = commit.value.sourceStoryIds[key]
                                        ?: error("Search commit omitted durable owner for $key")
                                    cardsByStory.getOrPut(durableStoryId) { mutableListOf() } += card
                                }
                            }
                        }
                    }
                }
            }
        }

        val stories = mutableListOf<CatalogSearchStory>()
        cardsByStory.toSortedMap(compareBy<StoryId> { it.value }).forEach { (storyId, rawCards) ->
            val state = try {
                bootstrap.ensureReady(storyId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (state is CanonicalStoryState.Ready) {
                stories += CatalogSearchStory(state.story, state.toProjection(), rawCards.toList())
            } else {
                val pluginId = rawCards.minByOrNull { it.pluginId.value }?.pluginId ?: return@forEach
                failures += CatalogSearchFailure(pluginId, CANONICAL_NOT_READY_CODE, retryable = true)
            }
        }
        return CatalogSearchResult(stories, failures)
    }

    suspend fun select(story: CatalogSearchStory): CatalogSearchSelectionResult =
        CatalogSearchSelectionResult.Success(story.story.id)

    private suspend fun fetch(
        source: CatalogSource,
        request: CatalogSearchRequest,
    ): CatalogSourceResult<SourceSearchPage> = try {
        source.search(SourceSearchRequest(request.query, request.filterValues[source.pluginId].orEmpty()))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CatalogSourceResult.Failure(CatalogSourceFailure(SOURCE_EXCEPTION_CODE, retryable = true))
    }

    private fun runCatchingProjection(
        source: CatalogSource,
        page: SourceSearchPage,
        matchIndex: CatalogMatchIndex,
    ): SearchProjection? = try {
        project(source, page, matchIndex)
    } catch (_: RuntimeException) {
        null
    }

    private fun project(
        source: CatalogSource,
        page: SourceSearchPage,
        matchIndex: CatalogMatchIndex,
    ): SearchProjection {
        val localIndex = matchIndex.fork()
        val entries = mutableListOf<CatalogEntry>()
        val cards = linkedMapOf<SourceKey, CatalogSearchSourceCard>()
        page.items.sortedBy { it.sourceId }.forEach { item ->
            val incoming = item.toCandidate(source.pluginId)
            val story = when (val resolution = localIndex.resolve(incoming)) {
                is StoryResolution.Existing -> localIndex.story(resolution.storyId)
                is StoryResolution.Create -> resolution.story
            }
            val entry = item.toEntry(source.pluginId, story.id)
            entries += entry
            cards[SourceKey(source.pluginId, item.sourceId)] = item.toCard(source.pluginId)
        }
        val mutation = CatalogSearchSummaryMutation(
            pluginId = source.pluginId,
            pluginVersion = source.version,
            resolvedAtEpochMillis = clock.nowEpochMillis(),
            stories = entries.map { localIndex.story(it.storyId) }.distinctBy { it.id },
            entries = entries,
        )
        return SearchProjection(mutation, cards)
    }

    private data class SearchProjection(
        val mutation: CatalogSearchSummaryMutation,
        val cards: Map<SourceKey, CatalogSearchSourceCard>,
    )

    private companion object {
        const val SOURCE_EXCEPTION_CODE = "catalog.source.exception"
        const val INVALID_SOURCE_CODE = "catalog.source.invalid"
        const val CANONICAL_NOT_READY_CODE = "catalog.search.canonical_not_ready"
    }
}

private fun CatalogSourceResult.Failure.toFailure(pluginId: PluginId) = CatalogSearchFailure(
    pluginId,
    failure.code,
    failure.retryable,
)

private fun SourceItem.toCandidate(pluginId: PluginId) = CatalogMatchCandidate(
    story = Story(
        StoryId("incoming:${pluginId.value}:${sourceId.hashCode().toUInt().toString(HEX_RADIX)}"),
        contentType.toModel(),
    ),
    titles = setOf(title),
    authors = authors,
    sourceKeys = setOf(SourceKey(pluginId, sourceId)),
    externalIdentifiers = externalIdentifiers,
)

private fun SourceItem.toEntry(pluginId: PluginId, storyId: StoryId) = CatalogEntry(
    storyId = storyId,
    pluginId = pluginId,
    sourceId = sourceId,
    title = title,
    authors = authors,
    contentType = contentType.toModel(),
    coverUrl = coverUrl,
    score = if (scoreValue != null && scoreScale != null) Score(scoreValue, scoreScale) else null,
    genres = genres,
    popularityRank = popularityRank,
    publicationStatus = publicationStatus?.toModel(),
    latestUpdate = latestUpdate?.let { CatalogLatestUpdate(it.atEpochMillis, it.releaseLabel) },
    externalIdentifiers = externalIdentifiers,
)

private fun SourceItem.toCard(pluginId: PluginId) = CatalogSearchSourceCard(
    pluginId,
    sourceId,
    title,
    contentType.toModel(),
    authors,
    coverUrl,
    if (scoreValue != null && scoreScale != null) Score(scoreValue, scoreScale) else null,
)

private const val HEX_RADIX = 16
