package app.openstory.catalog.search

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalSourceSummary
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.matching.CatalogMatchCandidate
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.orchestration.CatalogEvidenceLevel
import app.openstory.catalog.RecordingCanonicalEngineEventSink
import app.openstory.catalog.repository.CatalogCommitChange
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.repository.CatalogSearchSummaryCommitResult
import app.openstory.catalog.repository.CatalogSearchSummaryMutation
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceContentType
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceItem
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CatalogSearchServiceTest {

    @Test
    fun committedSearchChangesRouteThroughSummaryEvidenceEvents() = runTest {
        val canonical = FakeCanonicalRepository()
        val storyId = StoryId("story:routed")
        val key = SourceKey(PluginId("catalog.a"), "source-a")
        val repository = FakeRepository(
            canonical,
            forcedChanges = listOf(
                CatalogCommitChange(
                    storyId = storyId,
                    sourceKey = key,
                    identityFingerprintChanged = true,
                    fusionFingerprintChanged = true,
                ),
            ),
        )
        val engine = RecordingCanonicalEngineEventSink()
        val service = service(
            listOf(Source("catalog.a", item("source-a", "Provider title"))),
            repository,
            canonical,
            engine = engine,
        )

        service.search(CatalogSearchRequest("provider"))

        assertEquals(1, engine.evidenceChanges.size)
        val routed = engine.evidenceChanges.single()
        assertEquals(storyId, routed.storyId)
        assertEquals(key, routed.sourceKey)
        assertEquals(CatalogEvidenceLevel.SUMMARY, routed.level)
        assertTrue(routed.identityFingerprintChanged)
        assertTrue(routed.fusionFingerprintChanged)
    }
    @Test
    fun summaryIsCommittedBeforeCanonicalCardConstructionAndRawValuesDoNotOwnPresentation() = runTest {
        val canonical = FakeCanonicalRepository()
        val repository = FakeRepository(canonical)
        val source = Source("catalog.a", item("source-a", "Raw provider title"))
        val service = service(listOf(source), repository, canonical)

        val result = service.search(CatalogSearchRequest("raw"))

        assertEquals(1, repository.searchCommits)
        val story = result.stories.single()
        assertEquals("Canonical Raw provider title", story.presentation.title)
        assertEquals("canonical.jpg", story.presentation.coverUrl)
        assertEquals("Raw provider title", story.sources.single().title)
        assertEquals(0, source.detailsCalls)
    }

    @Test
    fun existingSourceKeyDurableOwnerWinsOverProposedSearchStoryId() = runTest {
        val canonicalId = StoryId("story:durable")
        val key = SourceKey(PluginId("catalog.a"), "source-a")
        val canonical = FakeCanonicalRepository().apply {
            put(readyState(canonicalId, "Persisted canonical"))
        }
        val repository = FakeRepository(canonical, existingOwners = mutableMapOf(key to canonicalId))
        val service = service(listOf(Source("catalog.a", item("source-a", "Provider title"))), repository, canonical)

        val story = service.search(CatalogSearchRequest("provider")).stories.single()

        assertEquals(canonicalId, story.story.id)
        assertEquals("Persisted canonical", story.presentation.title)
    }

    @Test
    fun preparingCanonicalBuildFailureOmitsRawEmergencyCard() = runTest {
        val canonical = FakeCanonicalRepository()
        val repository = FakeRepository(canonical)
        val rebuilder = FakeRebuilder(canonical, promote = false)
        val source = Source("catalog.a", item("source-a", "Raw title"))
        val service = service(listOf(source), repository, canonical, rebuilder)

        val result = service.search(CatalogSearchRequest("raw"))

        assertTrue(result.stories.isEmpty())
        assertEquals(listOf("catalog.search.canonical_not_ready"), result.failures.map { it.code })
        assertEquals(0, source.detailsCalls)
    }

    @Test
    fun providerResultOrderDoesNotChangeGroupedCanonicalStory() = runTest {
        val first = Source("catalog.a", item("a", "Same", setOf("Author")))
        val second = Source("catalog.b", item("b", "Same", setOf("Author")))

        val forward = searchWith(listOf(first, second))
        val reverse = searchWith(listOf(second, first))

        assertEquals(forward.stories.map { it.presentation.title }, reverse.stories.map { it.presentation.title })
        assertEquals(2, forward.stories.single().sources.size)
    }

    @Test
    fun durableOwnerFromFirstCommitControlsMatchingForLaterProviders() = runTest {
        val durable = StoryId("story:durable")
        val firstKey = SourceKey(PluginId("catalog.a"), "a")
        val canonical = FakeCanonicalRepository().apply {
            put(readyState(durable, "Durable canonical"))
        }
        val repository = FakeRepository(canonical, existingOwners = mutableMapOf(firstKey to durable))
        val service = service(
            listOf(
                Source("catalog.a", item("a", "Same title", setOf("Same Author"))),
                Source("catalog.b", item("b", "Same title", setOf("Same Author"))),
            ),
            repository,
            canonical,
        )

        val result = service.search(CatalogSearchRequest("same"))

        assertEquals(1, result.stories.size)
        assertEquals(durable, result.stories.single().story.id)
        assertEquals(setOf("catalog.a", "catalog.b"), result.stories.single().sources.map { it.pluginId.value }.toSet())
    }

    @Test
    fun selectionIsNavigationOnlyAndNeverRequestsDetails() = runTest {
        val canonical = FakeCanonicalRepository()
        val repository = FakeRepository(canonical)
        val source = Source("catalog.a", item("source-a", "Raw title"))
        val service = service(listOf(source), repository, canonical)
        val story = service.search(CatalogSearchRequest("raw")).stories.single()

        val selected = service.select(story)

        assertEquals(story.story.id, assertIs<CatalogSearchSelectionResult.Success>(selected).storyId)
        assertEquals(0, source.detailsCalls)
    }

    private suspend fun searchWith(sources: List<Source>): CatalogSearchResult {
        val canonical = FakeCanonicalRepository()
        return service(sources, FakeRepository(canonical), canonical).search(CatalogSearchRequest("same"))
    }

    private fun service(
        sourceList: List<Source>,
        repository: FakeRepository,
        canonical: FakeCanonicalRepository,
        rebuilder: FakeRebuilder = FakeRebuilder(canonical, promote = true),
        engine: RecordingCanonicalEngineEventSink = RecordingCanonicalEngineEventSink(),
    ): CatalogSearchService {
        val clock = Clock { 100L }
        return CatalogSearchService(
            sources = Registry(sourceList),
            repository = repository,
            reconciliationEngine = app.openstory.catalog.reconciliation.CatalogReconciliationEngine(
                app.openstory.catalog.reconciliation.ReconciliationPolicy(),
            ),
            storyIdFactory = app.openstory.catalog.identity.CatalogStoryIdFactory(),
            orchestrator = engine,
            clock = clock,
            bootstrap = CanonicalBootstrapUseCase(canonical, rebuilder),
            filterCache = CatalogFilterCache(),
        )
    }

    private fun item(id: String, title: String, authors: Set<String> = emptySet()) = SourceItem(
        sourceId = id,
        title = title,
        contentType = SourceContentType.MANGA,
        authors = authors,
        coverUrl = "raw.jpg",
        scoreValue = 9.0,
        scoreScale = 10.0,
    )
}

private class Registry(private val sources: List<CatalogSource>) : CatalogSourceRegistry {
    override suspend fun enabled(): List<CatalogSource> = sources
    override suspend fun source(pluginId: PluginId): CatalogSource? = sources.firstOrNull { it.pluginId == pluginId }
}

private class Source(id: String, private val item: SourceItem) : CatalogSource {
    override val pluginId = PluginId(id)
    override val version: String = "1.0.0"
    var detailsCalls = 0

    override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> =
        CatalogSourceResult.Success(SourceSearchPage(listOf(item), null))

    override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> {
        detailsCalls++
        error("Search must not request Details")
    }

    override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> = error("unused")
    override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = CatalogSourceResult.Success(emptyList())
}

private class FakeRepository(
    private val canonical: FakeCanonicalRepository,
    private val existingOwners: MutableMap<SourceKey, StoryId> = mutableMapOf(),
    private val forcedChanges: List<CatalogCommitChange> = emptyList(),
) : CatalogRepository {
    var searchCommits = 0
    private val matchCandidates = mutableListOf<CatalogMatchCandidate>()

    override suspend fun commitSearchSummaries(
        mutation: CatalogSearchSummaryMutation,
    ): Outcome<CatalogSearchSummaryCommitResult, CatalogStoreFailure> {
        searchCommits++
        val mapping = linkedMapOf<SourceKey, StoryId>()
        mutation.entries.forEach { entry ->
            val key = SourceKey(entry.pluginId, entry.sourceId)
            val owner = existingOwners.getOrPut(key) { entry.storyId }
            mapping[key] = owner
            if (canonical.state(owner) == null) canonical.put(preparingState(owner, entry.copy(storyId = owner)))
        }
        mutation.entries.groupBy { mapping.getValue(SourceKey(it.pluginId, it.sourceId)) }.forEach { (storyId, entries) ->
            val story = mutation.stories.firstOrNull { it.id == entries.first().storyId }
                ?.copy(id = storyId) ?: Story(storyId, entries.first().contentType)
            matchCandidates.removeAll { it.story.id == storyId }
            matchCandidates += CatalogMatchCandidate(
                story,
                entries.flatMap { setOf(it.title) + it.aliases }.toSet(),
                entries.flatMap { it.authors }.toSet(),
                entries.map { SourceKey(it.pluginId, it.sourceId) }.toSet(),
            )
        }
        return Outcome.Success(CatalogSearchSummaryCommitResult(mapping, forcedChanges))
    }

    override suspend fun matchSnapshot(): CatalogMatchSnapshot = CatalogMatchSnapshot(matchCandidates.toList())
    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = flowOf(emptyList())
    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = null
    override suspend fun sourceRecord(key: CatalogMetadataKey): CatalogSourceRecord? = null
    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = emptyList()
    override suspend fun sourceRecords(): List<CatalogSourceRecord> = emptyList()
    override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<app.openstory.catalog.repository.CatalogHomeCommitResult, CatalogStoreFailure> =
        Outcome.Success(app.openstory.catalog.repository.CatalogHomeCommitResult(emptyList()))
    override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<app.openstory.catalog.repository.CatalogDetailsCommitResult, CatalogStoreFailure> =
        Outcome.Success(app.openstory.catalog.repository.CatalogDetailsCommitResult(mutation.storyId, emptyList()))
}

private class FakeCanonicalRepository : CanonicalCatalogRepository {
    private val states = mutableMapOf<StoryId, MutableStateFlow<CanonicalStoryState?>>()

    fun put(state: CanonicalStoryState) {
        states.getOrPut(state.story.id) { MutableStateFlow(null) }.value = state
    }

    override fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?> =
        states.getOrPut(storyId) { MutableStateFlow(null) }
    override fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>> = flowOf(emptyList())
    override suspend fun state(storyId: StoryId): CanonicalStoryState? = states[storyId]?.value
    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = emptyList()
    override suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration? =
        (state(storyId) as? CanonicalStoryState.Ready)?.generation
    override suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference =
        requireNotNull(state(storyId)).preference
    override suspend fun setSourcePreference(preference: CanonicalSourcePreference) = Unit
    override suspend fun persistCandidate(candidate: CanonicalGeneration, expectedActiveGenerationId: String?) = false
    override suspend fun markHealth(storyId: StoryId, health: CanonicalHealth) = Unit
    override suspend fun cleanupObsoleteGenerations(storyId: StoryId) = Unit
}

private class FakeRebuilder(
    private val canonical: FakeCanonicalRepository,
    private val promote: Boolean,
) : CanonicalGenerationRebuilder {
    override suspend fun rebuild(storyId: StoryId, reason: CanonicalFusionReason): CanonicalFusionResult {
        val state = canonical.state(storyId) ?: return CanonicalFusionResult.Failed(storyId, "missing", false)
        if (!promote) return CanonicalFusionResult.Preparing(storyId)
        if (state is CanonicalStoryState.Ready) return CanonicalFusionResult.Unchanged(state.generation)
        state as CanonicalStoryState.Preparing
        val title = state.sources.firstOrNull()?.entry?.title ?: "Canonical"
        val ready = readyState(storyId, "Canonical $title", state.sources)
        canonical.put(ready)
        return CanonicalFusionResult.Promoted(ready.generation)
    }
}

private fun preparingState(storyId: StoryId, entry: CatalogEntry): CanonicalStoryState.Preparing {
    val key = SourceKey(entry.pluginId, entry.sourceId)
    return CanonicalStoryState.Preparing(
        story = Story(storyId, entry.contentType),
        health = CanonicalHealth.REEVALUATING,
        preference = CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.AUTO, null, 0),
        sources = listOf(
            CanonicalSourceSummary(
                key,
                entry,
                CatalogMetadataStamp("1.0.0", 100L),
                null,
                "identity:${key.pluginId.value}:${key.sourceId}",
                "fusion:${key.pluginId.value}:${key.sourceId}",
            ),
        ),
    )
}

private fun readyState(
    storyId: StoryId,
    title: String,
    sources: List<CanonicalSourceSummary> = listOf(
        CanonicalSourceSummary(
            SourceKey(PluginId("catalog.a"), "source-a"),
            CatalogEntry(storyId, PluginId("catalog.a"), "source-a", title, contentType = ContentType.MANGA),
            CatalogMetadataStamp("1.0.0", 100L),
            null,
            "identity:a",
            "fusion:a",
        ),
    ),
): CanonicalStoryState.Ready {
    val primary = sources.first().sourceKey
    val generation = CanonicalGeneration(
        id = "gen:${storyId.value}",
        storyId = storyId,
        fusionPolicyVersion = 1,
        primarySelectionPolicyVersion = 1,
        fusionFingerprint = "fusion:${storyId.value}",
        effectivePrimary = primary,
        metadata = CanonicalMetadata(
            title,
            null,
            "canonical.jpg",
            null,
            null,
            emptyList(),
            emptyList(),
            emptyList(),
            emptyList(),
            null,
            null,
            null,
        ),
        health = CanonicalHealth.FRESH,
        provenance = emptyMap(),
        createdAtEpochMillis = 100L,
    )
    return CanonicalStoryState.Ready(
        Story(storyId, ContentType.MANGA),
        CanonicalHealth.FRESH,
        CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.AUTO, null, 0),
        sources,
        generation,
    )
}
