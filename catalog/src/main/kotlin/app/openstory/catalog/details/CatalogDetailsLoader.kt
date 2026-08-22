package app.openstory.catalog.details

import app.openstory.catalog.home.toModel
import app.openstory.catalog.identity.CatalogStoryIdFactory
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataFailure
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.reconciliation.CatalogIngestReconciliationIndex
import app.openstory.catalog.reconciliation.CatalogReconciliationEngine
import app.openstory.catalog.reconciliation.IncomingSourceResolution
import app.openstory.catalog.reconciliation.ReconciliationEvidenceFactory
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.orchestration.CatalogEvidenceLevel
import app.openstory.catalog.orchestration.toEvidenceChange
import app.openstory.catalog.repository.CatalogCommitChange
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceDetails
import app.openstory.common.Clock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

internal sealed interface CatalogDetailsLoadResult {
    data class Success(
        val storyId: StoryId,
        val entry: CatalogEntry,
        val pluginVersion: String,
        val resolvedAtEpochMillis: Long,
    ) : CatalogDetailsLoadResult

    data class Failure(
        val failure: CatalogMetadataFailure,
        val attemptedPluginVersion: String?,
    ) : CatalogDetailsLoadResult
}

class CatalogDetailsLoader @Inject constructor(
    private val sources: CatalogSourceRegistry,
    private val repository: CatalogRepository,
    private val reconciliationEngine: CatalogReconciliationEngine,
    private val storyIdFactory: CatalogStoryIdFactory,
    private val orchestrator: CanonicalEngineEventSink,
    private val clock: Clock,
) {
    internal suspend fun load(
        key: CatalogMetadataKey,
        sourceHint: CatalogSource? = null,
    ): CatalogDetailsLoadResult = when (val lookup = resolveSource(key, sourceHint)) {
        is SourceLookup.Success -> load(lookup.source, key)
        is SourceLookup.Failure -> CatalogDetailsLoadResult.Failure(lookup.failure, attemptedPluginVersion = null)
    }

    private suspend fun load(
        source: CatalogSource,
        key: CatalogMetadataKey,
    ): CatalogDetailsLoadResult = when (val fetched = fetchDetails(source, key.sourceId)) {
        is DetailsFetch.Failure -> CatalogDetailsLoadResult.Failure(
            fetched.failure,
            attemptedPluginVersion = source.version,
        )
        is DetailsFetch.Success -> if (fetched.details.sourceId == key.sourceId) {
            persist(source, key, fetched.details)
        } else {
            CatalogDetailsLoadResult.Failure(
                CatalogMetadataFailure.SourceIdMismatch(key.sourceId, fetched.details.sourceId),
                attemptedPluginVersion = source.version,
            )
        }
    }

    private suspend fun resolveSource(key: CatalogMetadataKey, sourceHint: CatalogSource?): SourceLookup {
        if (sourceHint?.pluginId == key.pluginId) return SourceLookup.Success(sourceHint)
        return try {
            sources.source(key.pluginId)
                ?.let(SourceLookup::Success)
                ?: SourceLookup.Failure(CatalogMetadataFailure.SourceUnavailable(key.pluginId))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            SourceLookup.Failure(CatalogMetadataFailure.SourceFailure(SOURCE_EXCEPTION_CODE, retryable = true))
        }
    }

    private suspend fun fetchDetails(source: CatalogSource, sourceId: String): DetailsFetch = try {
        when (val result = source.details(sourceId)) {
            is CatalogSourceResult.Success -> DetailsFetch.Success(result.value)
            is CatalogSourceResult.Failure -> DetailsFetch.Failure(
                CatalogMetadataFailure.SourceFailure(result.failure.code, result.failure.retryable),
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        DetailsFetch.Failure(CatalogMetadataFailure.SourceFailure(SOURCE_EXCEPTION_CODE, retryable = true))
    }

    private suspend fun persist(
        source: CatalogSource,
        key: CatalogMetadataKey,
        details: SourceDetails,
    ): CatalogDetailsLoadResult = try {
        val persisted = repository.metadataSnapshot(key)
        val story = if (persisted != null) {
            Story(persisted.entry.storyId, persisted.entry.contentType)
        } else {
            resolveUnowned(source.pluginId, details)
        }
        val entry = details.toEntry(source.pluginId, story.id)
        val resolvedAt = clock.nowEpochMillis()
        store(
            source = source,
            entry = entry,
            mutation = CatalogDetailsMutation(story.id, entry, source.version, resolvedAt),
            resolvedAt = resolvedAt,
        )
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CatalogDetailsLoadResult.Failure(
            CatalogMetadataFailure.StoreFailure(STORE_EXCEPTION_CODE, retryable = true),
            attemptedPluginVersion = source.version,
        )
    }

    private suspend fun resolveUnowned(pluginId: PluginId, details: SourceDetails): Story {
        val records = repository.sourceRecords()
        val contentTypes = records.groupBy { it.storyId }
            .mapValues { (_, values) -> values.first().entry.contentType }
        val index = CatalogIngestReconciliationIndex(
            reconciliationEngine,
            storyIdFactory,
            records.map(ReconciliationEvidenceFactory::fromRecord),
        )
        val evidence = ReconciliationEvidenceFactory.incoming(
            sourceKey = SourceKey(pluginId, details.sourceId),
            contentType = details.contentType.toModel(),
            titles = setOf(details.title) + details.aliases,
            authors = details.authors,
            identifiers = details.externalIdentifiers,
        )
        return when (val resolution = index.resolve(evidence)) {
            is IncomingSourceResolution.Existing -> Story(
                resolution.storyId,
                contentTypes[resolution.storyId] ?: evidence.contentType,
            )
            is IncomingSourceResolution.Create -> resolution.story
        }
    }

    private suspend fun store(
        source: CatalogSource,
        entry: CatalogEntry,
        mutation: CatalogDetailsMutation,
        resolvedAt: Long,
    ): CatalogDetailsLoadResult = try {
        when (val committed = repository.commitDetails(mutation)) {
            is app.openstory.common.Outcome.Success -> {
                routeChanges(committed.value.changes)
                CatalogDetailsLoadResult.Success(
                    storyId = committed.value.storyId,
                    entry = entry.copy(storyId = committed.value.storyId),
                    pluginVersion = source.version,
                    resolvedAtEpochMillis = resolvedAt,
                )
            }
            is app.openstory.common.Outcome.Failure -> CatalogDetailsLoadResult.Failure(
                CatalogMetadataFailure.StoreFailure(committed.error.code, committed.error.retryable),
                attemptedPluginVersion = source.version,
            )
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CatalogDetailsLoadResult.Failure(
            CatalogMetadataFailure.StoreFailure(STORE_EXCEPTION_CODE, retryable = true),
            attemptedPluginVersion = source.version,
        )
    }

    private suspend fun routeChanges(changes: List<CatalogCommitChange>) {
        changes.forEach { change ->
            orchestrator.onEvidenceChanged(change.toEvidenceChange(CatalogEvidenceLevel.FULL))
        }
    }

    private sealed interface SourceLookup {
        data class Success(val source: CatalogSource) : SourceLookup
        data class Failure(val failure: CatalogMetadataFailure) : SourceLookup
    }

    private sealed interface DetailsFetch {
        data class Success(val details: SourceDetails) : DetailsFetch
        data class Failure(val failure: CatalogMetadataFailure.SourceFailure) : DetailsFetch
    }

    private companion object {
        const val SOURCE_EXCEPTION_CODE = "catalog.source.exception"
        const val STORE_EXCEPTION_CODE = "catalog.store.exception"
    }
}

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
    publicationStatus = publicationStatus?.toModel(),
    latestUpdate = latestUpdate?.let { CatalogLatestUpdate(it.atEpochMillis, it.releaseLabel) },
    externalIdentifiers = externalIdentifiers,
)
