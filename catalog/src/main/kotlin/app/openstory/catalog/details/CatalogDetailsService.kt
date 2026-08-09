package app.openstory.catalog.details

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.matching.SourceKey
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.matching.StoryResolution
import app.openstory.catalog.home.toModel
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.common.Clock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

sealed interface CatalogDetailsResult {
    data class Success(val story: Story, val entry: CatalogEntry) : CatalogDetailsResult
    data class Failure(val failure: CatalogDetailsFailure) : CatalogDetailsResult
}

sealed interface CatalogDetailsFailure {
    data class SourceUnavailable(val pluginId: PluginId) : CatalogDetailsFailure
    data class SourceFailure(val code: String, val retryable: Boolean) : CatalogDetailsFailure
    data class SourceIdMismatch(val requested: String, val returned: String) : CatalogDetailsFailure
    data class StoreFailure(val code: String, val retryable: Boolean) : CatalogDetailsFailure
}

class CatalogDetailsService(
    private val sources: CatalogSourceRegistry,
    private val repository: CatalogRepository,
    private val matcher: StoryMatcher,
    private val clock: Clock,
) {
    suspend fun load(pluginId: PluginId, sourceId: String): CatalogDetailsResult {
        val source = sources.source(pluginId) ?: return CatalogDetailsResult.Failure(CatalogDetailsFailure.SourceUnavailable(pluginId))
        val result = source.details(sourceId)
        if (result is CatalogSourceResult.Failure) return CatalogDetailsResult.Failure(CatalogDetailsFailure.SourceFailure(result.failure.code, result.failure.retryable))
        val details = (result as CatalogSourceResult.Success).value
        if (details.sourceId != sourceId) return CatalogDetailsResult.Failure(CatalogDetailsFailure.SourceIdMismatch(sourceId, details.sourceId))
        val snapshot = repository.matchSnapshot()
        val incoming = CatalogMatchCandidate(
            Story(StoryId("incoming:${pluginId.value}:${sourceId.hashCode().toUInt().toString(16)}"), details.contentType.toModel()),
            setOf(details.title) + details.aliases, details.authors, setOf(SourceKey(pluginId, sourceId)),
        )
        val resolution = matcher.resolve(incoming, snapshot.candidates)
        val story = when (resolution) {
            is StoryResolution.Existing -> snapshot.candidates.first { it.story.id == resolution.storyId }.story
            is StoryResolution.Create -> resolution.story
        }
        val entry = CatalogEntry(
            story.id, pluginId, details.sourceId, details.title, details.aliases, details.authors,
            details.description, details.genres, details.contentType.toModel(), details.languageTags,
            details.coverUrl, details.sourceUrl,
            if (details.scoreValue != null && details.scoreScale != null) Score(details.scoreValue, details.scoreScale) else null,
            details.popularityRank,
        )
        return when (val stored = repository.commitDetails(CatalogDetailsMutation(story.id, entry, source.version, clock.nowEpochMillis()))) {
            is app.openstory.common.Outcome.Success -> CatalogDetailsResult.Success(story, entry)
            is app.openstory.common.Outcome.Failure -> CatalogDetailsResult.Failure(CatalogDetailsFailure.StoreFailure(stored.error.code, stored.error.retryable))
        }
    }
}
