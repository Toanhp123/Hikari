package app.openstory.catalog.search

import app.openstory.catalog.details.CatalogDetailsFailure
import app.openstory.catalog.details.CatalogDetailsResult
import app.openstory.catalog.details.CatalogDetailsService
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.matching.CatalogMatchIndex
import app.openstory.catalog.matching.SourceKey
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.matching.StoryResolution
import app.openstory.catalog.home.toModel
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.CatalogSourceFailure
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceItem
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
    private val details: CatalogDetailsService,
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
        val stories = linkedMapOf<StoryId, MutableList<CatalogSearchSourceCard>>()
        val failures = mutableListOf<CatalogSearchFailure>()
        supervisorScope {
            sources.enabled().sortedBy { it.pluginId.value }
                .map { source -> async { source to fetch(source, request) } }
                .awaitAll()
                .forEach { (source, result) ->
                    when (result) {
                        is CatalogSourceResult.Failure -> failures += CatalogSearchFailure(
                            source.pluginId,
                            result.failure.code,
                            result.failure.retryable,
                        )
                        is CatalogSourceResult.Success -> try {
                            val projection = project(source.pluginId, result.value, matchIndex)
                            matchIndex = projection.matchIndex
                            projection.cards.forEach { (storyId, cards) ->
                                stories.getOrPut(storyId) { mutableListOf() } += cards
                            }
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            failures += CatalogSearchFailure(
                                source.pluginId,
                                "catalog.source.invalid",
                                retryable = false,
                            )
                        }
                    }
                }
        }
        val canonicalStories = stories.map { (storyId, cards) ->
            CatalogSearchStory(
                matchIndex.story(storyId),
                cards.toList(),
            )
        }
        return CatalogSearchResult(canonicalStories, failures)
    }

    suspend fun select(story: CatalogSearchStory): CatalogSearchSelectionResult {
        val source = story.sources.firstOrNull()
            ?: return CatalogSearchSelectionResult.Failure(EMPTY_SELECTION_CODE, retryable = false)
        return try {
            details.load(source.pluginId, source.sourceId).toSelectionResult()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CatalogSearchSelectionResult.Failure(SELECTION_EXCEPTION_CODE, retryable = true)
        }
    }

    private suspend fun fetch(
        source: CatalogSource,
        request: CatalogSearchRequest,
    ): CatalogSourceResult<SourceSearchPage> = try {
        source.search(SourceSearchRequest(request.query, request.filterValues[source.pluginId].orEmpty()))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CatalogSourceResult.Failure(
            CatalogSourceFailure(SOURCE_EXCEPTION_CODE, retryable = true),
        )
    }

    private fun project(
        pluginId: app.openstory.common.id.PluginId,
        page: SourceSearchPage,
        matchIndex: CatalogMatchIndex,
    ): SearchProjection {
        val localIndex = matchIndex.fork()
        val cards = linkedMapOf<StoryId, MutableList<CatalogSearchSourceCard>>()
        page.items.sortedBy { it.sourceId }.forEach { item ->
            val incoming = item.toCandidate(pluginId)
            val story = when (val resolution = localIndex.resolve(incoming)) {
                is StoryResolution.Existing -> localIndex.story(resolution.storyId)
                is StoryResolution.Create -> resolution.story
            }
            cards.getOrPut(story.id) { mutableListOf() } += item.toCard(pluginId)
        }
        return SearchProjection(localIndex, cards)
    }

    private data class SearchProjection(
        val matchIndex: CatalogMatchIndex,
        val cards: Map<StoryId, List<CatalogSearchSourceCard>>,
    )

    private companion object {
        const val SOURCE_EXCEPTION_CODE = "catalog.source.exception"
        const val EMPTY_SELECTION_CODE = "catalog.search.selection_empty"
        const val SELECTION_EXCEPTION_CODE = "catalog.search.selection_exception"
    }
}

private fun CatalogDetailsResult.toSelectionResult(): CatalogSearchSelectionResult = when (this) {
    is CatalogDetailsResult.Success -> CatalogSearchSelectionResult.Success(story.id)
    is CatalogDetailsResult.Failure -> CatalogSearchSelectionResult.Failure(
        code = failure.code(),
        retryable = failure.retryable(),
    )
}

private fun CatalogDetailsFailure.code(): String = when (this) {
    is CatalogDetailsFailure.SourceUnavailable -> "catalog.source_unavailable"
    is CatalogDetailsFailure.SourceFailure -> code
    is CatalogDetailsFailure.SourceIdMismatch -> "catalog.details_source_mismatch"
    is CatalogDetailsFailure.StoreFailure -> code
}

private fun CatalogDetailsFailure.retryable(): Boolean = when (this) {
    is CatalogDetailsFailure.SourceUnavailable -> false
    is CatalogDetailsFailure.SourceFailure -> retryable
    is CatalogDetailsFailure.SourceIdMismatch -> false
    is CatalogDetailsFailure.StoreFailure -> retryable
}

private fun SourceItem.toCandidate(pluginId: app.openstory.common.id.PluginId) = CatalogMatchCandidate(
    story = app.openstory.catalog.model.Story(
        StoryId("incoming:${pluginId.value}:${sourceId.hashCode().toUInt().toString(HEX_RADIX)}"),
        contentType.toModel(),
    ),
    titles = setOf(title),
    authors = authors,
    sourceKeys = setOf(SourceKey(pluginId, sourceId)),
)

private fun SourceItem.toCard(pluginId: app.openstory.common.id.PluginId) = CatalogSearchSourceCard(
    pluginId, sourceId, title, contentType.toModel(), authors, coverUrl,
    if (scoreValue != null && scoreScale != null) {
        app.openstory.catalog.model.Score(scoreValue, scoreScale)
    } else {
        null
    },
)

private const val HEX_RADIX = 16
