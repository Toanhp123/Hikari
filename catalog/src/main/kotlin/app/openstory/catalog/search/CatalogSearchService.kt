package app.openstory.catalog.search

import app.openstory.catalog.matching.CatalogMatchCandidate
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
) {
    suspend fun filters(): List<CatalogSearchFilterGroup> = supervisorScope {
        sources.enabled().sortedBy { it.pluginId.value }
            .map { source ->
                async {
                    val definitions = try {
                        when (val result = source.filters()) {
                            is CatalogSourceResult.Success -> result.value
                            is CatalogSourceResult.Failure -> emptyList()
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        emptyList()
                    }
                    CatalogSearchFilterGroup(source.pluginId, definitions)
                }
            }
            .awaitAll()
    }

    suspend fun search(request: CatalogSearchRequest): CatalogSearchResult {
        val candidates = repository.matchSnapshot().candidates.toMutableList()
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
                            val projection = project(source.pluginId, result.value, candidates)
                            candidates.clear()
                            candidates += projection.candidates
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
                candidates.first { it.story.id == storyId }.story,
                cards.toList(),
            )
        }
        return CatalogSearchResult(canonicalStories, failures)
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
        candidates: List<CatalogMatchCandidate>,
    ): SearchProjection {
        val localCandidates = candidates.toMutableList()
        val cards = linkedMapOf<StoryId, MutableList<CatalogSearchSourceCard>>()
        page.items.sortedBy { it.sourceId }.forEach { item ->
            val incoming = item.toCandidate(pluginId)
            val story = when (val resolution = matcher.resolve(incoming, localCandidates)) {
                is StoryResolution.Existing -> localCandidates.first { it.story.id == resolution.storyId }.story
                is StoryResolution.Create -> resolution.story.also { created ->
                    localCandidates += incoming.copy(story = created)
                }
            }
            cards.getOrPut(story.id) { mutableListOf() } += item.toCard(pluginId)
        }
        return SearchProjection(localCandidates, cards)
    }

    private data class SearchProjection(
        val candidates: List<CatalogMatchCandidate>,
        val cards: Map<StoryId, List<CatalogSearchSourceCard>>,
    )

    private companion object {
        const val SOURCE_EXCEPTION_CODE = "catalog.source.exception"
    }
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
