package app.openstory.catalog.home

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.identity.CatalogStoryIdFactory
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.reconciliation.CatalogIngestReconciliationIndex
import app.openstory.catalog.reconciliation.CatalogReconciliationEngine
import app.openstory.catalog.reconciliation.CatalogReconciliationService
import app.openstory.catalog.reconciliation.IncomingSourceResolution
import app.openstory.catalog.reconciliation.ReconciliationEvidenceFactory
import app.openstory.catalog.repository.CatalogCommitChange
import app.openstory.catalog.repository.CatalogHomeCommitResult
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
    private val reconciliationEngine: CatalogReconciliationEngine,
    private val storyIdFactory: CatalogStoryIdFactory,
    private val reconciliation: CatalogReconciliationService,
    private val fusion: CanonicalGenerationRebuilder,
    private val clock: Clock,
) {
    suspend fun refresh(request: SourceHomeRequest = SourceHomeRequest()): List<CatalogRefreshResult> {
        var ingest = ingestContext()
        val fetched = supervisorScope {
            sources.enabled().sortedBy { it.pluginId.value }
                .map { source -> async { source to fetch(source, request) } }
                .awaitAll()
        }
        return fetched.map { (source, result) ->
            when (result) {
                is CatalogSourceResult.Failure -> CatalogRefreshResult.SourceFailure(source.pluginId, result.failure)
                is CatalogSourceResult.Success -> runCatchingCommit(source, result.value, ingest).let { attempt ->
                    attempt.committedContext?.let { ingest = it }
                    attempt.result
                }
            }
        }
    }

    private suspend fun ingestContext(): IngestContext {
        val records = repository.sourceRecords()
        return IngestContext(
            index = CatalogIngestReconciliationIndex(
                reconciliationEngine,
                storyIdFactory,
                records.map(ReconciliationEvidenceFactory::fromRecord),
            ),
            storyContentTypes = records.groupBy { it.storyId }
                .mapValues { (_, values) -> values.first().entry.contentType }
                .toMutableMap(),
        )
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
            app.openstory.catalog.source.CatalogSourceFailure("catalog.source.exception", retryable = true),
        )
    }

    private suspend fun runCatchingCommit(
        source: CatalogSource,
        sections: List<app.openstory.catalog.source.SourceSection>,
        ingest: IngestContext,
    ): CommitAttempt = try {
        commit(source, sections, ingest)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CommitAttempt(
            CatalogRefreshResult.SourceFailure(
                source.pluginId,
                app.openstory.catalog.source.CatalogSourceFailure("catalog.source.invalid", retryable = false),
            ),
            committedContext = null,
        )
    }

    private suspend fun commit(
        source: CatalogSource,
        sections: List<app.openstory.catalog.source.SourceSection>,
        ingest: IngestContext,
    ): CommitAttempt {
        val local = ingest.fork()
        val resolved = resolveEntries(source, sections, local)
        val refreshedAtEpochMillis = clock.nowEpochMillis()
        val mutation = CatalogHomeMutation(
            pluginId = source.pluginId,
            pluginVersion = source.version,
            refreshedAtEpochMillis = refreshedAtEpochMillis,
            stories = resolved.values.map(ResolvedEntry::story).distinctBy { it.id },
            entries = resolved.values.map(ResolvedEntry::entry),
            sections = sections.toCatalogSections(resolved.mapValues { it.value.entry }),
            orderedSourceItemIds = sections.associate { it.sourceId to it.items.map(SourceItem::sourceId) },
        )
        return when (val committed = commitMutation(mutation)) {
            is Outcome.Success -> {
                local.applyDurableOwnership(committed.value.changes)
                routeChanges(committed.value.changes)
                CommitAttempt(
                    CatalogRefreshResult.Success(source.pluginId, refreshedAtEpochMillis),
                    local,
                )
            }
            is Outcome.Failure -> CommitAttempt(
                CatalogRefreshResult.StoreFailure(source.pluginId, committed.error),
                committedContext = null,
            )
        }
    }

    private fun resolveEntries(
        source: CatalogSource,
        sections: List<app.openstory.catalog.source.SourceSection>,
        ingest: IngestContext,
    ): Map<String, ResolvedEntry> = sections
        .flatMap { it.items }
        .distinctBy { it.sourceId }
        .sortedBy { it.sourceId }
        .associate { item ->
            val evidence = ReconciliationEvidenceFactory.incoming(
                sourceKey = SourceKey(source.pluginId, item.sourceId),
                contentType = item.contentType.toModel(),
                titles = setOf(item.title),
                authors = item.authors,
                identifiers = item.externalIdentifiers,
            )
            val resolution = ingest.index.resolve(evidence)
            val story = when (resolution) {
                is IncomingSourceResolution.Existing -> Story(
                    resolution.storyId,
                    ingest.storyContentTypes[resolution.storyId] ?: evidence.contentType,
                )
                is IncomingSourceResolution.Create -> resolution.story
            }
            ingest.storyContentTypes.putIfAbsent(story.id, story.contentType)
            item.sourceId to ResolvedEntry(story, item.toEntry(source, story.id))
        }

    private suspend fun routeChanges(changes: List<CatalogCommitChange>) {
        changes.forEach { change ->
            if (change.identityFingerprintChanged) reconciliation.reconcile(change.sourceKey)
            if (change.fusionFingerprintChanged) {
                fusion.rebuild(change.storyId, CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED)
            }
        }
    }

    private data class IngestContext(
        val index: CatalogIngestReconciliationIndex,
        val storyContentTypes: MutableMap<StoryId, ContentType>,
    ) {
        fun fork(): IngestContext = IngestContext(index.fork(), storyContentTypes.toMutableMap())

        fun applyDurableOwnership(changes: List<CatalogCommitChange>) {
            index.applyDurableOwnership(changes.associate { it.sourceKey to it.storyId })
        }
    }

    private data class ResolvedEntry(val story: Story, val entry: CatalogEntry)

    private data class CommitAttempt(
        val result: CatalogRefreshResult,
        val committedContext: IngestContext?,
    )

    private suspend fun commitMutation(
        mutation: CatalogHomeMutation,
    ): Outcome<CatalogHomeCommitResult, CatalogStoreFailure> = try {
        repository.commitHomeRefresh(mutation)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        Outcome.Failure(CatalogStoreFailure("catalog.store.exception", retryable = true))
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
