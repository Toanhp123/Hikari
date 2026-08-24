package app.openstory.catalog.fusion

import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.diagnostics.CanonicalDecisionTrace
import app.openstory.catalog.diagnostics.CanonicalDiagnostics
import app.openstory.catalog.diagnostics.CanonicalDiagnosticsSink
import app.openstory.catalog.diagnostics.CanonicalTraceKind
import app.openstory.catalog.diagnostics.NoOpCanonicalDiagnosticsSink
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataPolicy
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceDetails
import app.openstory.catalog.source.SourceFilter
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceSearchPage
import app.openstory.catalog.source.SourceSearchRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.Clock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CanonicalFusionServiceTest {
    private val storyId = StoryId("story:service")
    private val clock = Clock { 100L }

    @Test
    fun noLocalSourcesMarksDegradedWithoutNetworkFetch() = runTest {
        val repo = FakeCanonicalRepository(preparingState(), emptyList())
        val source = FakeSource()
        val service = service(repo, source)

        assertIs<CanonicalFusionResult.Preparing>(service.rebuild(storyId, CanonicalFusionReason.BOOTSTRAP))
        assertEquals(CanonicalHealth.DEGRADED, repo.markedHealth)
        assertEquals(0, source.detailsCalls)
    }

    @Test
    fun invalidCandidateKeepsPreviousGenerationActive() = runTest {
        val previous = generation("gen:previous", "Previous title")
        val incompatible = sourceRecord(contentType = ContentType.ANIME)
        val repo = FakeCanonicalRepository(preparingState(), listOf(incompatible)).apply {
            currentGeneration = previous
        }

        val result = service(repo, FakeSource()).rebuild(
            storyId,
            CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED,
        )

        val failure = assertIs<CanonicalFusionResult.Failed>(result)
        assertEquals(false, failure.retryable)
        assertSame(previous, repo.currentGeneration)
        assertEquals(0, repo.persistCalls)
        assertEquals(CanonicalHealth.DEGRADED, repo.markedHealth)
    }

    @Test
    fun rebuildEmitsStructuredPrimaryFieldAndValidationFailureTraces() = runTest {
        val traces = mutableListOf<CanonicalDecisionTrace>()
        val incompatible = sourceRecord(contentType = ContentType.ANIME)
        val repo = FakeCanonicalRepository(preparingState(), listOf(incompatible))

        val result = service(
            repo,
            FakeSource(),
            CanonicalDiagnostics(traces::add),
        ).rebuild(storyId, CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED)

        assertIs<CanonicalFusionResult.Failed>(result)
        val primary = traces.single { it.kind == CanonicalTraceKind.PRIMARY_SELECTION }
        assertEquals(listOf("primary.initial_best"), primary.reasonCodes)
        assertEquals(setOf(incompatible.key), primary.sourceKeys)
        val title = traces.single { trace ->
            trace.kind == CanonicalTraceKind.FIELD_FUSION && trace.field == CanonicalFieldKey.TITLE
        }
        assertEquals(setOf(incompatible.key), title.sourceKeys)
        val failed = traces.single { it.kind == CanonicalTraceKind.GENERATION_FAILED }
        assertTrue("canonical.generation.invalid.content-type-contradiction" in failed.reasonCodes)
    }

    @Test
    fun diagnosticSinkFailureDoesNotChangeSuccessfulPromotion() = runTest {
        val repo = FakeCanonicalRepository(preparingState(), listOf(sourceRecord()))
        val diagnostics = CanonicalDiagnostics(CanonicalDiagnosticsSink { error("boom") })

        val result = service(repo, FakeSource(), diagnostics).rebuild(storyId, CanonicalFusionReason.BOOTSTRAP)

        assertIs<CanonicalFusionResult.Promoted>(result)
        assertEquals(1, repo.persistCalls)
    }

    @Test
    fun promotionRaceRereadsAndRetriesOnceAgainstCurrentGeneration() = runTest {
        val raced = generation("gen:raced", "Raced title")
        val repo = FakeCanonicalRepository(preparingState(), listOf(sourceRecord())).apply {
            persistResults.add(false)
            persistResults.add(true)
            generationInstalledOnFirstRace = raced
        }

        val result = service(repo, FakeSource()).rebuild(storyId, CanonicalFusionReason.BOOTSTRAP)

        assertIs<CanonicalFusionResult.Promoted>(result)
        assertEquals(2, repo.persistCalls)
        assertEquals(listOf(null, raced.id), repo.expectedActiveIds)
    }

    @Test
    fun promotionRaceRebuildsFromFreshSourceEvidence() = runTest {
        val refreshed = sourceRecord().copy(
            entry = sourceRecord().entry.copy(title = "Refreshed title"),
            fusionFingerprint = "fusion:refreshed",
        )
        val repo = FakeCanonicalRepository(preparingState(), listOf(sourceRecord())).apply {
            persistResults.add(false)
            persistResults.add(true)
            recordsInstalledOnFirstRace = listOf(refreshed)
        }

        val result = assertIs<CanonicalFusionResult.Promoted>(
            service(repo, FakeSource()).rebuild(storyId, CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED),
        )

        assertEquals("Refreshed title", result.generation.metadata.title)
        assertEquals(2, repo.sourceReadCalls)
    }

    @Test
    fun repeatedPromotionRaceReturnsRetryableFailureWithoutReplacingCurrentGeneration() = runTest {
        val raced = generation("gen:raced", "Raced title")
        val repo = FakeCanonicalRepository(preparingState(), listOf(sourceRecord())).apply {
            persistResults.add(false)
            persistResults.add(false)
            generationInstalledOnFirstRace = raced
        }

        val result = service(repo, FakeSource()).rebuild(storyId, CanonicalFusionReason.BOOTSTRAP)

        val failure = assertIs<CanonicalFusionResult.Failed>(result)
        assertEquals("canonical.promotion.race", failure.code)
        assertEquals(true, failure.retryable)
        assertSame(raced, repo.currentGeneration)
        assertEquals(2, repo.persistCalls)
    }

    @Test
    fun changedCandidatePromotesAndSemanticallyEqualRebuildIsUnchanged() = runTest {
        val record = sourceRecord()
        val repo = FakeCanonicalRepository(preparingState(), listOf(record))
        val service = service(repo, FakeSource())

        val first = assertIs<CanonicalFusionResult.Promoted>(
            service.rebuild(storyId, CanonicalFusionReason.BOOTSTRAP),
        )
        repo.currentGeneration = first.generation
        val second = service.rebuild(storyId, CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED)

        assertIs<CanonicalFusionResult.Unchanged>(second)
        assertEquals(1, repo.persistCalls)
    }

    @Test
    fun semanticallyUnchangedRebuildRepairsDegradedStateHealthFromActiveGeneration() = runTest {
        val repo = FakeCanonicalRepository(preparingState(), listOf(sourceRecord()))
        val service = service(repo, FakeSource())
        val promoted = assertIs<CanonicalFusionResult.Promoted>(
            service.rebuild(storyId, CanonicalFusionReason.BOOTSTRAP),
        )
        repo.currentGeneration = promoted.generation
        repo.stateHealthOverride = CanonicalHealth.DEGRADED
        repo.markedHealth = null

        assertIs<CanonicalFusionResult.Unchanged>(
            service.rebuild(storyId, CanonicalFusionReason.POLICY_REEVALUATION),
        )

        assertEquals(promoted.generation.health, repo.markedHealth)
    }

    private fun service(
        repo: FakeCanonicalRepository,
        source: FakeSource,
        diagnostics: CanonicalDiagnostics = CanonicalDiagnostics(NoOpCanonicalDiagnosticsSink),
    ): CanonicalFusionService {
        val registry = object : CatalogSourceRegistry {
            override suspend fun enabled(): List<CatalogSource> = listOf(source)
            override suspend fun source(pluginId: PluginId): CatalogSource? = source.takeIf { it.pluginId == pluginId }
        }
        return CanonicalFusionService(
            canonical = repo,
            engine = CatalogFusionEngine(),
            validator = CanonicalGenerationValidator(),
            availability = CatalogSourceAvailabilityResolver(registry, CatalogMetadataPolicy(clock)),
            clock = clock,
            diagnostics = diagnostics,
        )
    }

    private fun preparingState() = CanonicalStoryState.Preparing(
        story = Story(storyId, ContentType.MANGA),
        health = CanonicalHealth.REEVALUATING,
        preference = CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.AUTO, null, 0),
        sources = emptyList(),
    )

    private fun sourceRecord(contentType: ContentType = ContentType.MANGA): CatalogSourceRecord {
        val key = SourceKey(PluginId("provider.a"), "source")
        val entry = CatalogEntry(
            storyId = storyId,
            pluginId = key.pluginId,
            sourceId = key.sourceId,
            title = "Title",
            contentType = contentType,
        )
        return CatalogSourceRecord(
            key,
            storyId,
            entry,
            CatalogMetadataStamp("1", 1L),
            CatalogMetadataStamp("1", 90L),
            "identity",
            "fusion",
        )
    }

    private fun generation(id: String, title: String): CanonicalGeneration {
        val source = SourceKey(PluginId("provider.a"), "source")
        return CanonicalGeneration(
            id = id,
            storyId = storyId,
            fusionPolicyVersion = FUSION_POLICY_VERSION,
            primarySelectionPolicyVersion = PRIMARY_SELECTION_POLICY_VERSION,
            fusionFingerprint = "fusion:$id",
            effectivePrimary = source,
            metadata = app.openstory.catalog.canonical.CanonicalMetadata(
                title = title,
                description = null,
                coverUrl = null,
                sourceUrl = null,
                popularityRank = null,
                aliases = emptyList(),
                authors = emptyList(),
                genres = emptyList(),
                languageTags = emptyList(),
                publicationStatus = null,
                latestUpdate = null,
                score = null,
            ),
            health = CanonicalHealth.FRESH,
            provenance = emptyMap(),
            createdAtEpochMillis = 50L,
        )
    }

    private class FakeCanonicalRepository(
        private val state: CanonicalStoryState,
        records: List<CatalogSourceRecord>,
    ) : CanonicalCatalogRepository {
        private var records: List<CatalogSourceRecord> = records
        var currentGeneration: CanonicalGeneration? = null
        var stateHealthOverride: CanonicalHealth? = null
        var markedHealth: CanonicalHealth? = null
        var persistCalls = 0
        val persistResults = ArrayDeque<Boolean>()
        val expectedActiveIds = mutableListOf<String?>()
        var generationInstalledOnFirstRace: CanonicalGeneration? = null
        var recordsInstalledOnFirstRace: List<CatalogSourceRecord>? = null
        var sourceReadCalls = 0

        override fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?> = flow { emit(state(storyId)) }
        override fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>> = flowOf(emptyList())
        override suspend fun state(storyId: StoryId): CanonicalStoryState? = if (currentGeneration == null) {
            state
        } else {
            CanonicalStoryState.Ready(
                state.story,
                stateHealthOverride ?: currentGeneration!!.health,
                state.preference,
                state.sources,
                currentGeneration!!,
            )
        }
        override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> {
            sourceReadCalls++
            return records
        }
        override suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration? = currentGeneration
        override suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference = state.preference
        override suspend fun setSourcePreference(preference: CanonicalSourcePreference) = Unit
        override suspend fun persistCandidate(
            candidate: CanonicalGeneration,
            expectedActiveGenerationId: String?,
        ): Boolean {
            persistCalls++
            expectedActiveIds += expectedActiveGenerationId
            val accepted = if (persistResults.isEmpty()) true else persistResults.removeFirst()
            if (accepted) {
                currentGeneration = candidate
            } else if (persistCalls == 1) {
                generationInstalledOnFirstRace?.let { currentGeneration = it }
                recordsInstalledOnFirstRace?.let { records = it }
            }
            return accepted
        }
        override suspend fun markHealth(storyId: StoryId, health: CanonicalHealth) { markedHealth = health }
        override suspend fun cleanupObsoleteGenerations(storyId: StoryId) = Unit
    }

    private class FakeSource : CatalogSource {
        override val pluginId = PluginId("provider.a")
        override val version = "1"
        var detailsCalls = 0
        override suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>> =
            CatalogSourceResult.Success(emptyList())
        override suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage> =
            CatalogSourceResult.Success(SourceSearchPage(emptyList(), null))
        override suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails> {
            detailsCalls++
            error("Fusion must not fetch Details")
        }
        override suspend fun filters(): CatalogSourceResult<List<SourceFilter>> = CatalogSourceResult.Success(emptyList())
    }
}
