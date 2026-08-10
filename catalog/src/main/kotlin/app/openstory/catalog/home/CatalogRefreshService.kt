package app.openstory.catalog.home

import app.openstory.catalog.CatalogStoreFailure
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
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class CatalogRefreshService @Inject constructor(
    private val sources: CatalogSourceRegistry,
    private val repository: CatalogRepository,
    private val matcher: StoryMatcher,
    private val clock: Clock,
) {
    suspend fun refresh(request: SourceHomeRequest = SourceHomeRequest()): List<CatalogRefreshResult> {
        val candidates = repository.matchSnapshot().candidates.toMutableList()
        return supervisorScope {
            sources.enabled().sortedBy { it.pluginId.value }
                .map { source -> async { source to fetch(source, request) } }
                .awaitAll()
                .map { (source, result) ->
                    when (result) {
                        is CatalogSourceResult.Failure -> CatalogRefreshResult.SourceFailure(
                            source.pluginId,
                            result.failure,
                        )
                        is CatalogSourceResult.Success -> runCatchingCommit(source, result.value, candidates)
                    }
                }
        }
    }

    private suspend fun fetch(
        source: CatalogSource,
        request: SourceHomeRequest,
    ): CatalogSourceResult<List<app.openstory.catalog.source.SourceSection>> = try {
        source.home(request)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CatalogSourceResult.Failure(
            app.openstory.catalog.source.CatalogSourceFailure(
                "catalog.source.exception",
                retryable = true,
            ),
        )
    }

    private suspend fun runCatchingCommit(
        source: CatalogSource,
        sections: List<app.openstory.catalog.source.SourceSection>,
        candidates: MutableList<CatalogMatchCandidate>,
    ): CatalogRefreshResult = try {
        commit(source, sections, candidates)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CatalogRefreshResult.SourceFailure(
            source.pluginId,
            app.openstory.catalog.source.CatalogSourceFailure(
                "catalog.source.invalid",
                retryable = false,
            ),
        )
    }

    private suspend fun commit(
        source: CatalogSource,
        sections: List<app.openstory.catalog.source.SourceSection>,
        candidates: MutableList<CatalogMatchCandidate>,
    ): CatalogRefreshResult {
        val localCandidates = candidates.toMutableList()
        val resolved = resolveEntries(source, sections, localCandidates)
        val mutation = CatalogHomeMutation(
            pluginId = source.pluginId,
            pluginVersion = source.version,
            refreshedAtEpochMillis = clock.nowEpochMillis(),
            stories = resolved.values
                .map { entry -> localCandidates.first { it.story.id == entry.storyId }.story }
                .distinctBy { it.id },
            entries = resolved.values.toList(),
            sections = sections.toCatalogSections(resolved),
            orderedSourceItemIds = sections.associate { it.sourceId to it.items.map(SourceItem::sourceId) },
        )
        val stored = commitMutation(mutation)
        return when (stored) {
            is Outcome.Success -> {
                candidates.clear()
                candidates += localCandidates
                CatalogRefreshResult.Success(source.pluginId)
            }
            is Outcome.Failure -> CatalogRefreshResult.StoreFailure(source.pluginId, stored.error)
        }
    }

    private fun resolveEntries(
        source: CatalogSource,
        sections: List<app.openstory.catalog.source.SourceSection>,
        localCandidates: MutableList<CatalogMatchCandidate>,
    ): Map<String, CatalogEntry> = sections
        .flatMap { it.items }
        .distinctBy { it.sourceId }
        .sortedBy { it.sourceId }
        .associate { item ->
            val incoming = item.toCandidate(source)
            val story = when (val resolution = matcher.resolve(incoming, localCandidates)) {
                is StoryResolution.Existing -> localCandidates
                    .first { it.story.id == resolution.storyId }
                    .story
                is StoryResolution.Create -> resolution.story.also { created ->
                    localCandidates += incoming.copy(story = created)
                }
            }
            item.sourceId to item.toEntry(source, story.id)
        }

    private suspend fun commitMutation(
        mutation: CatalogHomeMutation,
    ): Outcome<Unit, CatalogStoreFailure> = try {
        repository.commitHomeRefresh(mutation)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        Outcome.Failure(
            CatalogStoreFailure(
                "catalog.store.exception",
                retryable = true,
            ),
        )
    }
}

private fun List<app.openstory.catalog.source.SourceSection>.toCatalogSections(
    resolved: Map<String, CatalogEntry>,
): List<CatalogHomeSection> = map { section ->
    CatalogHomeSection(
        section.sourceId,
        section.title,
        section.items.map { resolved.getValue(it.sourceId) },
    )
}

private fun SourceItem.toCandidate(source: CatalogSource) = CatalogMatchCandidate(
    story = Story(
        StoryId("incoming:${source.pluginId.value}:${sourceId.stableHash()}"),
        contentType.toModel(),
    ),
    titles = setOf(title),
    authors = authors,
    sourceKeys = setOf(SourceKey(source.pluginId, sourceId)),
)

private fun SourceItem.toEntry(source: CatalogSource, storyId: StoryId) = CatalogEntry(
    storyId = storyId,
    pluginId = source.pluginId,
    sourceId = sourceId,
    title = title,
    authors = authors,
    contentType = contentType.toModel(),
    coverUrl = coverUrl,
    score = if (scoreValue != null && scoreScale != null) Score(scoreValue, scoreScale) else null,
)

private fun String.stableHash(): String = hashCode().toUInt().toString(HEX_RADIX)

private const val HEX_RADIX = 16

internal fun SourceContentType.toModel(): ContentType = when (this) {
    SourceContentType.LIGHT_NOVEL -> ContentType.LIGHT_NOVEL
    SourceContentType.WEB_NOVEL -> ContentType.WEB_NOVEL
    SourceContentType.MANGA -> ContentType.MANGA
    SourceContentType.ANIME -> ContentType.ANIME
}
