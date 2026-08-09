package app.openstory.catalog.home

import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.matching.SourceKey
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.matching.StoryResolution
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceItem
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CancellationException

class CatalogRefreshService(
    private val sources: CatalogSourceRegistry,
    private val repository: CatalogRepository,
    private val matcher: StoryMatcher,
    private val clock: Clock,
) {
    suspend fun refresh(request: SourceHomeRequest = SourceHomeRequest()): List<CatalogRefreshResult> {
        val candidates = repository.matchSnapshot().candidates.toMutableList()
        return sources.enabled().sortedBy { it.pluginId.value }.map { source -> refreshSource(source, request, candidates) }
    }

    private suspend fun refreshSource(
        source: CatalogSource,
        request: SourceHomeRequest,
        candidates: MutableList<CatalogMatchCandidate>,
    ): CatalogRefreshResult = try {
        when (val result = source.home(request)) {
            is CatalogSourceResult.Failure -> CatalogRefreshResult.SourceFailure(source.pluginId, result.failure)
            is CatalogSourceResult.Success -> commit(source, result.value, candidates)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CatalogRefreshResult.SourceFailure(
            source.pluginId,
            app.openstory.catalog.source.CatalogSourceFailure("catalog.source.exception", retryable = true),
        )
    }

    private suspend fun commit(
        source: CatalogSource,
        sections: List<app.openstory.catalog.source.SourceSection>,
        candidates: MutableList<CatalogMatchCandidate>,
    ): CatalogRefreshResult {
        val resolved = sections.flatMap { it.items }.distinctBy { it.sourceId }.sortedBy { it.sourceId }.associate { item ->
            val incoming = item.toCandidate(source)
            val story = when (val resolution = matcher.resolve(incoming, candidates)) {
                is StoryResolution.Existing -> candidates.first { it.story.id == resolution.storyId }.story
                is StoryResolution.Create -> resolution.story.also { created -> candidates += incoming.copy(story = created) }
            }
            item.sourceId to item.toEntry(source, story.id)
        }
        val catalogSections = sections.map { section ->
            CatalogHomeSection(section.sourceId, section.title, section.items.map { resolved.getValue(it.sourceId) })
        }
        val mutation = CatalogHomeMutation(
            pluginId = source.pluginId,
            pluginVersion = source.version,
            refreshedAtEpochMillis = clock.nowEpochMillis(),
            stories = resolved.values.map { entry -> candidates.first { it.story.id == entry.storyId }.story }.distinctBy { it.id },
            entries = resolved.values.toList(),
            sections = catalogSections,
            orderedSourceItemIds = sections.associate { it.sourceId to it.items.map(SourceItem::sourceId) },
        )
        return when (val stored = repository.commitHomeRefresh(mutation)) {
            is Outcome.Success -> CatalogRefreshResult.Success(source.pluginId)
            is Outcome.Failure -> CatalogRefreshResult.StoreFailure(source.pluginId, stored.error)
        }
    }
}

private fun SourceItem.toCandidate(source: CatalogSource) = CatalogMatchCandidate(
    story = Story(StoryId("incoming:${source.pluginId.value}:${sourceId.hashCode().toUInt().toString(16)}"), contentType.toModel()),
    titles = setOf(title), authors = authors, sourceKeys = setOf(SourceKey(source.pluginId, sourceId)),
)

private fun SourceItem.toEntry(source: CatalogSource, storyId: StoryId) = CatalogEntry(
    storyId = storyId, pluginId = source.pluginId, sourceId = sourceId, title = title,
    authors = authors, contentType = contentType.toModel(), coverUrl = coverUrl,
    score = if (scoreValue != null && scoreScale != null) Score(scoreValue, scoreScale) else null,
)

internal fun SourceContentType.toModel(): ContentType = when (this) {
    SourceContentType.LIGHT_NOVEL -> ContentType.LIGHT_NOVEL
    SourceContentType.WEB_NOVEL -> ContentType.WEB_NOVEL
    SourceContentType.MANGA -> ContentType.MANGA
    SourceContentType.ANIME -> ContentType.ANIME
}
