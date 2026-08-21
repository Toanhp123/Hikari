package app.openstory.catalog.search

import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.home.toModel
import app.openstory.catalog.identity.CatalogStoryIdFactory
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.catalog.projection.toProjection
import app.openstory.catalog.reconciliation.CatalogIngestReconciliationIndex
import app.openstory.catalog.reconciliation.CatalogReconciliationEngine
import app.openstory.catalog.reconciliation.CatalogReconciliationService
import app.openstory.catalog.reconciliation.IncomingSourceResolution
import app.openstory.catalog.reconciliation.ReconciliationEvidenceFactory
import app.openstory.catalog.repository.CatalogCommitChange
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.repository.CatalogSearchSummaryMutation
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceFailure
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceItem
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope

class CatalogSearchService @Inject constructor(
    private val sources: CatalogSourceRegistry,
    private val repository: CatalogRepository,
    private val reconciliationEngine: CatalogReconciliationEngine,
    private val storyIdFactory: CatalogStoryIdFactory,
    private val reconciliation: CatalogReconciliationService,
    private val fusion: CanonicalGenerationRebuilder,
    private val clock: Clock,
    private val bootstrap: CanonicalBootstrapUseCase,
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
                filterCache.get(key) ?: loadFilterGroup(source)?.also { group -> filterCache.put(key, group) }
                    ?: CatalogSearchFilterGroup(source.pluginId, emptyList())
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
        var ingest = ingestContext()
        val cardsByStory = linkedMapOf<StoryId, MutableList<CatalogSearchSourceCard>>()
        val failures = mutableListOf<CatalogSearchFailure>()
        val fetched = supervisorScope {
            sources.enabled().sortedBy { it.pluginId.value }
                .map { source -> async { source to fetch(source, request) } }
                .awaitAll()
        }
        fetched.forEach { (source, result) ->
            when (result) {
                is CatalogSourceResult.Failure -> failures += result.toFailure(source.pluginId)
                is CatalogSourceResult.Success -> {
                    val attempt = runCatchingProjection(source, result.value, ingest)
                    if (attempt == null) {
                        failures += CatalogSearchFailure(source.pluginId, INVALID_SOURCE_CODE, retryable = false)
                    } else {
                        when (val commit = repository.commitSearchSummaries(attempt.mutation)) {
                            is Outcome.Failure -> failures += CatalogSearchFailure(
                                source.pluginId,
                                commit.error.code,
                                commit.error.retryable,
                            )
                            is Outcome.Success -> {
                                attempt.context.applyDurableOwnership(commit.value.sourceStoryIds)
                                ingest = attempt.context
                                routeChanges(commit.value.changes)
                                attempt.cards.forEach { (key, card) ->
                                    val durableStoryId = commit.value.sourceStoryIds[key]
                                        ?: error("Search commit omitted durable owner for $key")
                                    cardsByStory.getOrPut(durableStoryId) { mutableListOf() } += card
                                }
                            }
                        }
                    }
                }
            }
        }

        val stories = mutableListOf<CatalogSearchStory>()
        cardsByStory.toSortedMap(compareBy<StoryId> { it.value }).forEach { (storyId, rawCards) ->
            val state = try {
                bootstrap.ensureReady(storyId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                null
            }
            if (state is CanonicalStoryState.Ready) {
                stories += CatalogSearchStory(state.story, state.toProjection(), rawCards.toList())
            } else {
                val pluginId = rawCards.minByOrNull { it.pluginId.value }?.pluginId ?: return@forEach
                failures += CatalogSearchFailure(pluginId, CANONICAL_NOT_READY_CODE, retryable = true)
            }
        }
        return CatalogSearchResult(stories, failures)
    }

    suspend fun select(story: CatalogSearchStory): CatalogSearchSelectionResult =
        CatalogSearchSelectionResult.Success(story.story.id)

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
        request: CatalogSearchRequest,
    ): CatalogSourceResult<SourceSearchPage> = try {
        source.search(SourceSearchRequest(request.query, request.filterValues[source.pluginId].orEmpty()))
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CatalogSourceResult.Failure(CatalogSourceFailure(SOURCE_EXCEPTION_CODE, retryable = true))
    }

    private fun runCatchingProjection(
        source: CatalogSource,
        page: SourceSearchPage,
        ingest: IngestContext,
    ): SearchProjection? = try {
        project(source, page, ingest)
    } catch (_: RuntimeException) {
        null
    }

    private fun project(
        source: CatalogSource,
        page: SourceSearchPage,
        ingest: IngestContext,
    ): SearchProjection {
        val local = ingest.fork()
        val entries = mutableListOf<CatalogEntry>()
        val stories = linkedMapOf<StoryId, Story>()
        val cards = linkedMapOf<SourceKey, CatalogSearchSourceCard>()
        page.items.sortedBy { it.sourceId }.forEach { item ->
            val evidence = ReconciliationEvidenceFactory.incoming(
                sourceKey = SourceKey(source.pluginId, item.sourceId),
                contentType = item.contentType.toModel(),
                titles = setOf(item.title),
                authors = item.authors,
                identifiers = item.externalIdentifiers,
            )
            val resolution = local.index.resolve(evidence)
            val story = when (resolution) {
                is IncomingSourceResolution.Existing -> Story(
                    resolution.storyId,
                    local.storyContentTypes[resolution.storyId] ?: evidence.contentType,
                )
                is IncomingSourceResolution.Create -> resolution.story
            }
            local.storyContentTypes.putIfAbsent(story.id, story.contentType)
            stories[story.id] = story
            entries += item.toEntry(source.pluginId, story.id)
            cards[SourceKey(source.pluginId, item.sourceId)] = item.toCard(source.pluginId)
        }
        return SearchProjection(
            mutation = CatalogSearchSummaryMutation(
                pluginId = source.pluginId,
                pluginVersion = source.version,
                resolvedAtEpochMillis = clock.nowEpochMillis(),
                stories = stories.values.toList(),
                entries = entries,
            ),
            cards = cards,
            context = local,
        )
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

        fun applyDurableOwnership(sourceOwners: Map<SourceKey, StoryId>) {
            index.applyDurableOwnership(sourceOwners)
        }
    }

    private data class SearchProjection(
        val mutation: CatalogSearchSummaryMutation,
        val cards: Map<SourceKey, CatalogSearchSourceCard>,
        val context: IngestContext,
    )

    private companion object {
        const val SOURCE_EXCEPTION_CODE = "catalog.source.exception"
        const val INVALID_SOURCE_CODE = "catalog.source.invalid"
        const val CANONICAL_NOT_READY_CODE = "catalog.search.canonical_not_ready"
    }
}

private fun CatalogSourceResult.Failure.toFailure(pluginId: PluginId) = CatalogSearchFailure(
    pluginId,
    failure.code,
    failure.retryable,
)

private fun SourceItem.toEntry(pluginId: PluginId, storyId: StoryId) = CatalogEntry(
    storyId = storyId,
    pluginId = pluginId,
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

private fun SourceItem.toCard(pluginId: PluginId) = CatalogSearchSourceCard(
    pluginId,
    sourceId,
    title,
    contentType.toModel(),
    authors,
    coverUrl,
    if (scoreValue != null && scoreScale != null) Score(scoreValue, scoreScale) else null,
)
