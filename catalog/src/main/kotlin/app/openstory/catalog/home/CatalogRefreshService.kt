package app.openstory.catalog.home

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.matching.CatalogMatchIndex
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.matching.StoryMatcher
import app.openstory.catalog.matching.StoryResolution
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceFeedKind
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceItem
import app.openstory.catalog.source.SourcePublicationStatus
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
        var matchIndex = CatalogMatchIndex(matcher, repository.matchSnapshot().candidates)
        val fetched = supervisorScope {
            sources.enabled().sortedBy { it.pluginId.value }
                .map { source -> async { source to fetch(source, request) } }
                .awaitAll()
        }
        return fetched.map { (source, result) ->
            when (result) {
                is CatalogSourceResult.Failure -> CatalogRefreshResult.SourceFailure(
                    source.pluginId,
                    result.failure,
                )
                is CatalogSourceResult.Success -> runCatchingCommit(source, result.value, matchIndex).let { attempt ->
                    attempt.committedIndex?.let { matchIndex = it }
                    attempt.result
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
        matchIndex: CatalogMatchIndex,
    ): CommitAttempt = try {
        commit(source, sections, matchIndex)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CommitAttempt(
            CatalogRefreshResult.SourceFailure(
                source.pluginId,
                app.openstory.catalog.source.CatalogSourceFailure(
                    "catalog.source.invalid",
                    retryable = false,
                ),
            ),
            committedIndex = null,
        )
    }

    private suspend fun commit(
        source: CatalogSource,
        sections: List<app.openstory.catalog.source.SourceSection>,
        matchIndex: CatalogMatchIndex,
    ): CommitAttempt {
        val localIndex = matchIndex.fork()
        val resolved = resolveEntries(source, sections, localIndex)
        val refreshedAtEpochMillis = clock.nowEpochMillis()
        val mutation = CatalogHomeMutation(
            pluginId = source.pluginId,
            pluginVersion = source.version,
            refreshedAtEpochMillis = refreshedAtEpochMillis,
            stories = resolved.values
                .map { entry -> localIndex.story(entry.storyId) }
                .distinctBy { it.id },
            entries = resolved.values.toList(),
            sections = sections.toCatalogSections(resolved),
            orderedSourceItemIds = sections.associate { it.sourceId to it.items.map(SourceItem::sourceId) },
        )
        return commitMutation(mutation).fold(
            onSuccess = {
                CommitAttempt(
                    CatalogRefreshResult.Success(source.pluginId, refreshedAtEpochMillis),
                    localIndex,
                )
            },
            onFailure = { failure ->
                CommitAttempt(
                    CatalogRefreshResult.StoreFailure(source.pluginId, failure),
                    committedIndex = null,
                )
            },
        )
    }

    private fun resolveEntries(
        source: CatalogSource,
        sections: List<app.openstory.catalog.source.SourceSection>,
        matchIndex: CatalogMatchIndex,
    ): Map<String, CatalogEntry> = sections
        .flatMap { it.items }
        .distinctBy { it.sourceId }
        .sortedBy { it.sourceId }
        .associate { item ->
            val incoming = item.toCandidate(source)
            val story = when (val resolution = matchIndex.resolve(incoming)) {
                is StoryResolution.Existing -> matchIndex.story(resolution.storyId)
                is StoryResolution.Create -> resolution.story
            }
            item.sourceId to item.toEntry(source, story.id)
        }

    private data class CommitAttempt(
        val result: CatalogRefreshResult,
        val committedIndex: CatalogMatchIndex?,
    )

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
        sourceId = section.sourceId,
        title = section.title,
        items = section.items.map { resolved.getValue(it.sourceId) },
        kind = section.kind.toModel(),
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
    externalIdentifiers = externalIdentifiers,
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
    genres = genres,
    popularityRank = popularityRank,
    publicationStatus = publicationStatus?.toModel(),
    latestUpdate = latestUpdate?.let { CatalogLatestUpdate(it.atEpochMillis, it.releaseLabel) },
    externalIdentifiers = externalIdentifiers,
)

private fun String.stableHash(): String = hashCode().toUInt().toString(HEX_RADIX)

private const val HEX_RADIX = 16

internal fun SourceContentType.toModel(): ContentType = when (this) {
    SourceContentType.LIGHT_NOVEL -> ContentType.LIGHT_NOVEL
    SourceContentType.WEB_NOVEL -> ContentType.WEB_NOVEL
    SourceContentType.MANGA -> ContentType.MANGA
    SourceContentType.ANIME -> ContentType.ANIME
}

private fun SourceFeedKind.toModel(): CatalogFeedKind = when (this) {
    SourceFeedKind.POPULAR -> CatalogFeedKind.POPULAR
    SourceFeedKind.LATEST_UPDATES -> CatalogFeedKind.LATEST_UPDATES
    SourceFeedKind.TOP_RATED -> CatalogFeedKind.TOP_RATED
    SourceFeedKind.OTHER -> CatalogFeedKind.OTHER
}

internal fun SourcePublicationStatus.toModel(): PublicationStatus = when (this) {
    SourcePublicationStatus.ONGOING -> PublicationStatus.ONGOING
    SourcePublicationStatus.COMPLETED -> PublicationStatus.COMPLETED
    SourcePublicationStatus.HIATUS -> PublicationStatus.HIATUS
    SourcePublicationStatus.CANCELLED -> PublicationStatus.CANCELLED
    SourcePublicationStatus.UPCOMING -> PublicationStatus.UPCOMING
}
