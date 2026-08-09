package app.openstory.catalog.search

import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.matching.SourceKey
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.matching.StoryResolution
import app.openstory.catalog.home.toModel
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceItem
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CancellationException

class CatalogSearchService(
    private val sources: CatalogSourceRegistry,
    private val repository: CatalogRepository,
    private val matcher: StoryMatcher,
) {
    suspend fun search(request: CatalogSearchRequest): CatalogSearchResult {
        val candidates = repository.matchSnapshot().candidates.toMutableList()
        val stories = linkedMapOf<StoryId, MutableList<CatalogSearchSourceCard>>()
        val failures = mutableListOf<CatalogSearchFailure>()
        sources.enabled().sortedBy { it.pluginId.value }.forEach { source ->
            try {
                when (val result = source.search(SourceSearchRequest(request.query, request.filterValues))) {
                    is CatalogSourceResult.Failure -> failures += CatalogSearchFailure(source.pluginId, result.failure.code, result.failure.retryable)
                    is CatalogSourceResult.Success -> result.value.items.sortedBy { it.sourceId }.forEach { item ->
                        val incoming = item.toCandidate(source.pluginId)
                        val resolution = matcher.resolve(incoming, candidates)
                        val story = when (resolution) {
                            is StoryResolution.Existing -> candidates.first { it.story.id == resolution.storyId }.story
                            is StoryResolution.Create -> resolution.story.also { created -> candidates += incoming.copy(story = created) }
                        }
                        stories.getOrPut(story.id) { mutableListOf() } += item.toCard(source.pluginId)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failures += CatalogSearchFailure(source.pluginId, "catalog.source.exception", retryable = true)
            }
        }
        return CatalogSearchResult(stories.map { (storyId, cards) -> CatalogSearchStory(candidates.first { it.story.id == storyId }.story, cards.toList()) }, failures)
    }
}

private fun SourceItem.toCandidate(pluginId: app.openstory.common.id.PluginId) = CatalogMatchCandidate(
    story = app.openstory.catalog.model.Story(StoryId("incoming:${pluginId.value}:${sourceId.hashCode().toUInt().toString(16)}"), contentType.toModel()),
    titles = setOf(title), authors = authors, sourceKeys = setOf(SourceKey(pluginId, sourceId)),
)

private fun SourceItem.toCard(pluginId: app.openstory.common.id.PluginId) = CatalogSearchSourceCard(
    pluginId, sourceId, title, contentType.toModel(), authors, coverUrl,
    if (scoreValue != null && scoreScale != null) app.openstory.catalog.model.Score(scoreValue, scoreScale) else null,
)
