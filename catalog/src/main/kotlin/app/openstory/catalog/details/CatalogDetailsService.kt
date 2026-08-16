package app.openstory.catalog.details

import app.openstory.catalog.home.toModel
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.matching.CatalogMatchIndex
import app.openstory.catalog.matching.SourceKey
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.matching.StoryResolution
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceDetails
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

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

class CatalogDetailsService @Inject constructor(
    private val sources: CatalogSourceRegistry,
    private val repository: CatalogRepository,
    private val matcher: StoryMatcher,
    private val clock: Clock,
) {
    suspend fun load(pluginId: PluginId, sourceId: String): CatalogDetailsResult =
        when (val lookup = lookupSource(pluginId)) {
            is SourceLookup.Failure -> CatalogDetailsResult.Failure(lookup.failure)
            is SourceLookup.Success -> loadDetails(lookup.source, sourceId)
        }

    private suspend fun lookupSource(pluginId: PluginId): SourceLookup = try {
        sources.source(pluginId)
            ?.let(SourceLookup::Success)
            ?: SourceLookup.Failure(CatalogDetailsFailure.SourceUnavailable(pluginId))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        SourceLookup.Failure(CatalogDetailsFailure.SourceUnavailable(pluginId))
    }

    private suspend fun loadDetails(
        source: CatalogSource,
        sourceId: String,
    ): CatalogDetailsResult = when (val fetch = fetchDetails(source, sourceId)) {
        is DetailsFetch.Failure -> CatalogDetailsResult.Failure(fetch.failure)
        is DetailsFetch.Success -> enrich(source, sourceId, fetch.details)
    }

    private suspend fun fetchDetails(
        source: CatalogSource,
        sourceId: String,
    ): DetailsFetch = try {
        when (val result = source.details(sourceId)) {
            is CatalogSourceResult.Failure -> DetailsFetch.Failure(
                CatalogDetailsFailure.SourceFailure(
                    result.failure.code,
                    result.failure.retryable,
                ),
            )
            is CatalogSourceResult.Success -> DetailsFetch.Success(result.value)
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DetailsFetch.Failure(
            CatalogDetailsFailure.SourceFailure(
                SOURCE_EXCEPTION_CODE,
                retryable = true,
            ),
        )
    }

    private suspend fun enrich(
        source: CatalogSource,
        requestedSourceId: String,
        details: SourceDetails,
    ): CatalogDetailsResult = if (details.sourceId != requestedSourceId) {
        CatalogDetailsResult.Failure(
            CatalogDetailsFailure.SourceIdMismatch(requestedSourceId, details.sourceId),
        )
    } else {
        persist(source, details)
    }

    private suspend fun persist(
        source: CatalogSource,
        details: SourceDetails,
    ): CatalogDetailsResult {
        val matchIndex = CatalogMatchIndex(matcher, repository.matchSnapshot().candidates)
        val incoming = details.toCandidate(source.pluginId)
        val story = when (val resolution = matchIndex.resolve(incoming)) {
            is StoryResolution.Existing -> matchIndex.story(resolution.storyId)
            is StoryResolution.Create -> resolution.story
        }
        val entry = details.toEntry(source.pluginId, story.id)
        val mutation = CatalogDetailsMutation(
            story.id,
            entry,
            source.version,
            clock.nowEpochMillis(),
        )
        return store(story, entry, mutation)
    }

    private suspend fun store(
        story: Story,
        entry: CatalogEntry,
        mutation: CatalogDetailsMutation,
    ): CatalogDetailsResult = try {
        when (val stored = repository.commitDetails(mutation)) {
            is Outcome.Success -> CatalogDetailsResult.Success(
                story.copy(id = stored.value),
                entry.copy(storyId = stored.value),
            )
            is Outcome.Failure -> CatalogDetailsResult.Failure(
                CatalogDetailsFailure.StoreFailure(
                    stored.error.code,
                    stored.error.retryable,
                ),
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CatalogDetailsResult.Failure(
            CatalogDetailsFailure.StoreFailure(
                STORE_EXCEPTION_CODE,
                retryable = true,
            ),
        )
    }

    private sealed interface SourceLookup {
        data class Success(val source: CatalogSource) : SourceLookup
        data class Failure(val failure: CatalogDetailsFailure) : SourceLookup
    }

    private sealed interface DetailsFetch {
        data class Success(val details: SourceDetails) : DetailsFetch
        data class Failure(val failure: CatalogDetailsFailure.SourceFailure) : DetailsFetch
    }

    private companion object {
        const val SOURCE_EXCEPTION_CODE = "catalog.source.exception"
        const val STORE_EXCEPTION_CODE = "catalog.store.exception"
    }
}

private fun SourceDetails.toCandidate(pluginId: PluginId) = CatalogMatchCandidate(
    Story(
        StoryId("incoming:${pluginId.value}:${sourceId.hashCode().toUInt().toString(HEX_RADIX)}"),
        contentType.toModel(),
    ),
    setOf(title) + aliases,
    authors,
    setOf(SourceKey(pluginId, sourceId)),
)

private fun SourceDetails.toEntry(pluginId: PluginId, storyId: StoryId) = CatalogEntry(
    storyId = storyId,
    pluginId = pluginId,
    sourceId = sourceId,
    title = title,
    aliases = aliases,
    authors = authors,
    description = description,
    genres = genres,
    contentType = contentType.toModel(),
    languageTags = languageTags,
    coverUrl = coverUrl,
    sourceUrl = sourceUrl,
    score = if (scoreValue != null && scoreScale != null) Score(scoreValue, scoreScale) else null,
    popularityRank = popularityRank,
)

private const val HEX_RADIX = 16
