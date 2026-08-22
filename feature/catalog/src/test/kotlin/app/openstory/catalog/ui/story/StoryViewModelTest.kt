package app.openstory.catalog.ui.story

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalFieldContributor
import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalFieldProvenance
import app.openstory.catalog.canonical.CanonicalFieldStrategy
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalSourceSummary
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.details.CatalogDetailsLoader
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataCoordinator
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataPolicy
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Story
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.orchestration.CatalogEvidenceChange
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.reconciliation.ReconciliationAssessment
import app.openstory.catalog.reconciliation.ReconciliationCase
import app.openstory.catalog.reconciliation.ReconciliationCaseKey
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.ReconciliationCaseStatus
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.catalog.reconciliation.ReconciliationReasonCode
import app.openstory.catalog.reconciliation.ReconciliationResolutionOrigin
import app.openstory.catalog.reconciliation.ReconciliationReviewService
import app.openstory.catalog.reconciliation.ReconciliationSemanticDecision
import app.openstory.catalog.identity.ProtectedContentMappingConflict
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryMappingScheduler
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun autoPresentationComesFromCanonicalGenerationNotRawSourceOrder() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(readyState())
        val viewModel = viewModel(canonical)
        runCurrent()

        assertEquals("Canonical title", viewModel.state.value.story?.preferredTitle)
        assertEquals("canonical.jpg", viewModel.state.value.story?.coverUrl)
        assertNull(viewModel.state.value.selectedSource)
    }

    @Test
    fun rawInspectionDoesNotChangeCanonicalPresentation() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(readyState())
        val viewModel = viewModel(canonical)
        runCurrent()

        viewModel.selectInspectionSource(SourceKey(PluginId("catalog.b"), "source-b"))
        runCurrent()

        assertEquals("Canonical title", viewModel.state.value.story?.preferredTitle)
        assertEquals(
            StorySourceIdentity(PluginId("catalog.b"), "source-b"),
            viewModel.state.value.selectedSource,
        )
        assertEquals("gen:ready", (canonical.current() as CanonicalStoryState.Ready).generation.id)
    }

    @Test
    fun pinAndAutomaticModePersistPreferenceAndRequestCanonicalRebuild() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(readyState())
        val rebuilder = RecordingRebuilder(canonical)
        val engine = RecordingStoryEngineEventSink()
        val viewModel = viewModel(canonical, rebuilder, engine = engine)
        runCurrent()

        val pinned = SourceKey(PluginId("catalog.b"), "source-b")
        viewModel.pinPrimary(pinned)
        runCurrent()

        assertEquals(CanonicalSourcePreferenceMode.PINNED, canonical.current().preference.mode)
        assertEquals(pinned, canonical.current().preference.pinnedSource)
        assertEquals(listOf(canonical.current().story.id), engine.preferenceChanges)
        assertFalse(rebuilder.reasons.contains(CanonicalFusionReason.SOURCE_PREFERENCE_CHANGED))

        viewModel.useAutomaticPrimary()
        runCurrent()

        assertEquals(CanonicalSourcePreferenceMode.AUTO, canonical.current().preference.mode)
        assertNull(canonical.current().preference.pinnedSource)
        assertEquals(listOf(canonical.current().story.id, canonical.current().story.id), engine.preferenceChanges)
        assertFalse(rebuilder.reasons.contains(CanonicalFusionReason.SOURCE_PREFERENCE_CHANGED))
    }

    @Test
    fun preparingStateShowsNoRawProviderFallbackAndBootstrapsOnce() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(preparingState())
        val rebuilder = RecordingRebuilder(canonical)
        val viewModel = viewModel(canonical, rebuilder)
        runCurrent()

        assertNull(viewModel.state.value.story)
        assertEquals(listOf(CanonicalFusionReason.BOOTSTRAP), rebuilder.reasons)
    }

    @Test
    fun retiredAssistedStoryIdUsesResolvedCanonicalStoryId() = runTest(dispatcher.scheduler) {
        val survivor = readyState(StoryId("story:survivor"))
        val canonical = FakeCanonicalRepository(survivor, aliases = setOf(StoryId("story:retired")))
        val viewModel = viewModel(canonical, storyId = StoryId("story:retired"))
        runCurrent()

        assertEquals(StoryId("story:survivor"), viewModel.state.value.storyId)
        assertEquals(StoryId("story:survivor"), viewModel.state.value.story?.storyId)
    }

    @Test
    fun highConfidencePendingCaseProjectsContextualPromptWithSameDurableIdentity() = runTest(dispatcher.scheduler) {
        val cases = StoryPromptCases(listOf(promptCase(confidence = 0.90, revision = 4)))
        val viewModel = viewModel(
            canonical = FakeCanonicalRepository(readyState()),
            reviewCases = cases,
            reviewProjections = StoryPromptProjections,
        )
        runCurrent()

        val prompt = requireNotNull(viewModel.state.value.reconciliationPrompt)
        assertEquals("prompt-case", prompt.caseId)
        assertEquals(4L, prompt.caseRevision)
        assertEquals(StoryId("story:2"), prompt.otherStoryId)
        assertEquals("Other canonical title", prompt.otherStoryTitle)
        assertTrue(prompt.mergeAllowed)
    }

    @Test
    fun lowConfidencePendingCaseRemainsDurableButDoesNotPrompt() = runTest(dispatcher.scheduler) {
        val cases = StoryPromptCases(listOf(promptCase(confidence = 0.80)))
        val viewModel = viewModel(FakeCanonicalRepository(readyState()), reviewCases = cases)
        runCurrent()

        assertNull(viewModel.state.value.reconciliationPrompt)
        assertEquals(ReconciliationCaseStatus.PENDING, cases.current().single().status)
    }

    @Test
    fun deferSuppressesContextualPromptWithoutResolvingCase() = runTest(dispatcher.scheduler) {
        val clock = MutableStoryClock(1_000L)
        val cases = StoryPromptCases(listOf(promptCase(confidence = 0.90)))
        val viewModel = viewModel(FakeCanonicalRepository(readyState()), reviewCases = cases, reviewClock = clock)
        runCurrent()
        assertTrue(viewModel.state.value.reconciliationPrompt != null)

        viewModel.deferReconciliationPrompt()
        runCurrent()

        assertNull(viewModel.state.value.reconciliationPrompt)
        assertEquals(ReconciliationCaseStatus.PENDING, cases.current().single().status)
        val suppressUntil = requireNotNull(cases.current().single().contextualPromptSuppressedUntilEpochMillis)
        assertEquals(86_401_000L, suppressUntil)

        advanceTimeBy(86_399_999L)
        runCurrent()
        assertNull(viewModel.state.value.reconciliationPrompt)

        clock.now = suppressUntil
        advanceTimeBy(1L)
        runCurrent()
        assertEquals("prompt-case", requireNotNull(viewModel.state.value.reconciliationPrompt).caseId)
    }

    @Test
    fun keepSeparateResolvesCaseAndRemovesPrompt() = runTest(dispatcher.scheduler) {
        val cases = StoryPromptCases(listOf(promptCase(confidence = 0.90)))
        val viewModel = viewModel(FakeCanonicalRepository(readyState()), reviewCases = cases)
        runCurrent()

        viewModel.keepReconciliationSeparate()
        runCurrent()

        assertNull(viewModel.state.value.reconciliationPrompt)
        assertEquals(ReconciliationCaseStatus.RESOLVED_SEPARATE, cases.current().single().status)
    }

    @Test
    fun invariantBlockedCaseCanExplainButNeverOffersMerge() = runTest(dispatcher.scheduler) {
        val cases = StoryPromptCases(
            listOf(promptCase(confidence = 0.92, eligibility = ReconciliationMergeEligibility.INVARIANT_BLOCKED)),
        )
        val viewModel = viewModel(FakeCanonicalRepository(readyState()), reviewCases = cases)
        runCurrent()

        val prompt = requireNotNull(viewModel.state.value.reconciliationPrompt)
        assertFalse(prompt.mergeAllowed)
    }

    @Test
    fun protectedConflictHandsOffExactCaseToSharedReviewFlow() = runTest(dispatcher.scheduler) {
        val cases = StoryPromptCases(listOf(promptCase(confidence = 0.92, revision = 3)))
        val merge = StoryPromptMergeExecutor(
            StoryMergeResult.ReviewRequired(
                reasons = setOf("protected_content_mapping_conflict"),
                protectedContentMappingConflicts = listOf(
                    ProtectedContentMappingConflict(PluginId("plugin.a"), setOf("one", "two")),
                ),
            ),
        )
        val viewModel = viewModel(FakeCanonicalRepository(readyState()), reviewCases = cases, reviewMerge = merge)
        runCurrent()
        var handedOffCaseId: String? = null

        viewModel.mergeReconciliationPrompt { handedOffCaseId = it }
        runCurrent()

        assertEquals("prompt-case", handedOffCaseId)
        assertEquals("prompt-case", merge.requests.single().reconciliationCaseId)
    }

    @Test
    fun storyViewModelHasNoRawCatalogRepositoryOrFusionEngineDependency() {
        val dependencies = StoryViewModel::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.name }

        assertFalse(dependencies.any { it == CatalogRepository::class.java.name })
        assertFalse(dependencies.any { it.endsWith("CatalogFusionEngine") })
        assertFalse(dependencies.any { it.endsWith("CanonicalFusionService") })
    }

    private fun TestScope.viewModel(
        canonical: FakeCanonicalRepository,
        rebuilder: RecordingRebuilder = RecordingRebuilder(canonical),
        engine: RecordingStoryEngineEventSink = RecordingStoryEngineEventSink(),
        storyId: StoryId = canonical.requestId,
        reviewCases: ReconciliationCaseRepository = EmptyStoryPromptCases,
        reviewProjections: CatalogStoryProjectionRepository = EmptyStoryPromptProjections,
        reviewMerge: StoryMergeExecutor = StoryPromptMergeExecutor(StoryMergeResult.Merged(storyId, "merge:test")),
        reviewClock: Clock = Clock { 100L },
    ): StoryViewModel {
        val legacy = EmptyCatalogRepository()
        val registry = EmptySourceRegistry
        val clock = reviewClock
        val metadata = CatalogMetadataCoordinator(
            repository = legacy,
            sources = registry,
            loader = CatalogDetailsLoader(
                sources = registry,
                repository = legacy,
                reconciliationEngine = app.openstory.catalog.reconciliation.CatalogReconciliationEngine(
                    app.openstory.catalog.reconciliation.ReconciliationPolicy(),
                ),
                storyIdFactory = app.openstory.catalog.identity.CatalogStoryIdFactory(),
                orchestrator = engine,
                clock = clock,
            ),
            policy = CatalogMetadataPolicy(clock),
            clock = clock,
            processScope = backgroundScope,
        )
        return StoryViewModel(
            StoryAssistedArgs(storyId),
            canonical,
            CanonicalBootstrapUseCase(canonical, rebuilder),
            metadata,
            engine,
            LibraryService(FakeLibraryRepository(), clock, NoOpMappingScheduler),
            FakeProgressRepository(),
            StoryReconciliationController(
                reviewCases,
                reviewProjections,
                ReconciliationReviewService(reviewCases, reviewMerge, clock, engine),
                clock,
            ),
        ).also { viewModel ->
            backgroundScope.launch { viewModel.state.collect {} }
        }
    }
}

private class RecordingStoryEngineEventSink : CanonicalEngineEventSink {
    val evidenceChanges = mutableListOf<CatalogEvidenceChange>()
    val preferenceChanges = mutableListOf<StoryId>()
    val merged = mutableListOf<StoryId>()
    var preferenceResult: CanonicalFusionResult? = null

    override suspend fun onEvidenceChanged(change: CatalogEvidenceChange) {
        evidenceChanges += change
    }

    override suspend fun onSourceLinked(storyId: StoryId, sourceKey: SourceKey) = Unit

    override suspend fun onSourceUnlinked(storyId: StoryId, sourceKey: SourceKey) = Unit

    override suspend fun onSourcePreferenceChanged(storyId: StoryId): CanonicalFusionResult {
        preferenceChanges += storyId
        return preferenceResult ?: CanonicalFusionResult.Preparing(storyId)
    }

    override suspend fun onStoryMerged(storyId: StoryId): CanonicalFusionResult {
        merged += storyId
        return CanonicalFusionResult.Preparing(storyId)
    }
}

private class FakeCanonicalRepository(
    initial: CanonicalStoryState,
    aliases: Set<StoryId> = emptySet(),
) : CanonicalCatalogRepository {
    private val state = MutableStateFlow<CanonicalStoryState?>(initial)
    private val acceptedIds = aliases + initial.story.id
    val requestId: StoryId = initial.story.id

    fun current(): CanonicalStoryState = requireNotNull(state.value)

    override fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?> =
        if (storyId in acceptedIds) state else flowOf(null)

    override fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>> = flowOf(emptyList())

    override suspend fun state(storyId: StoryId): CanonicalStoryState? =
        state.value.takeIf { storyId in acceptedIds }

    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = emptyList()

    override suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration? =
        (state(storyId) as? CanonicalStoryState.Ready)?.generation

    override suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference =
        requireNotNull(state(storyId)).preference

    override suspend fun setSourcePreference(preference: CanonicalSourcePreference) {
        val current = current()
        val resolved = preference.copy(storyId = current.story.id)
        state.value = when (current) {
            is CanonicalStoryState.Preparing -> current.copy(preference = resolved)
            is CanonicalStoryState.Ready -> current.copy(preference = resolved)
        }
    }

    override suspend fun persistCandidate(
        candidate: CanonicalGeneration,
        expectedActiveGenerationId: String?,
    ): Boolean = false

    override suspend fun markHealth(storyId: StoryId, health: CanonicalHealth) = Unit
    override suspend fun cleanupObsoleteGenerations(storyId: StoryId) = Unit
}

private class RecordingRebuilder(
    private val canonical: FakeCanonicalRepository,
) : CanonicalGenerationRebuilder {
    val reasons = mutableListOf<CanonicalFusionReason>()

    override suspend fun rebuild(storyId: StoryId, reason: CanonicalFusionReason): CanonicalFusionResult {
        reasons += reason
        val current = canonical.current()
        return when (current) {
            is CanonicalStoryState.Ready -> CanonicalFusionResult.Unchanged(current.generation)
            is CanonicalStoryState.Preparing -> CanonicalFusionResult.Preparing(current.story.id)
        }
    }
}

private class EmptyCatalogRepository : CatalogRepository {
    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = flowOf(emptyList())
    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun matchSnapshot(): CatalogMatchSnapshot = CatalogMatchSnapshot(emptyList())
    override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = null
    override suspend fun sourceRecord(key: CatalogMetadataKey): CatalogSourceRecord? = null
    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = emptyList()
    override suspend fun sourceRecords(): List<CatalogSourceRecord> = emptyList()
    override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<app.openstory.catalog.repository.CatalogHomeCommitResult, CatalogStoreFailure> =
        Outcome.Success(app.openstory.catalog.repository.CatalogHomeCommitResult(emptyList()))

    override suspend fun commitSearchSummaries(
        mutation: app.openstory.catalog.repository.CatalogSearchSummaryMutation,
    ) = app.openstory.common.Outcome.Failure(
        app.openstory.catalog.CatalogStoreFailure("test.search.unsupported", retryable = false),
    )

    override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<app.openstory.catalog.repository.CatalogDetailsCommitResult, CatalogStoreFailure> =
        Outcome.Success(app.openstory.catalog.repository.CatalogDetailsCommitResult(mutation.storyId, emptyList()))
}

private object EmptySourceRegistry : CatalogSourceRegistry {
    override suspend fun enabled(): List<CatalogSource> = emptyList()
    override suspend fun source(pluginId: PluginId): CatalogSource? = null
}

private class FakeLibraryRepository : LibraryRepository {
    override fun observe(): Flow<List<LibraryEntry>> = flowOf(emptyList())
    override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long): LibraryEntry =
        LibraryEntry(storyId, status, addedAt, addedAt)
    override suspend fun remove(storyId: StoryId) = Unit
    override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long): LibraryEntry? = null
}

private object NoOpMappingScheduler : LibraryMappingScheduler {
    override fun schedule(storyId: StoryId) = Unit
}

private class FakeProgressRepository : ReadingProgressRepository {
    override fun observeAll(): Flow<List<ReadingProgress>> = flowOf(emptyList())
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = flowOf(null)
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId): ReadingProgress? = null
    override suspend fun save(progress: ReadingProgress) = Unit
}

private object EmptyStoryPromptCases : ReconciliationCaseRepository {
    override fun observePending(): Flow<List<ReconciliationCase>> = flowOf(emptyList())
    override fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>> = flowOf(emptyList())
    override suspend fun find(caseId: String): ReconciliationCase? = null
    override suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase? = null
    override suspend fun recordAssessment(
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCase? = null
    override suspend fun resolveSeparate(
        caseId: String,
        expectedRevision: Long,
        origin: ReconciliationResolutionOrigin,
        resolvedAtEpochMillis: Long,
    ): Boolean = false
    override suspend fun defer(caseId: String, expectedRevision: Long, suppressUntilEpochMillis: Long): Boolean = false
}

private object EmptyStoryPromptProjections : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(emptyList())
}

private object StoryPromptProjections : CatalogStoryProjectionRepository {
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(
        listOf(
            CatalogStoryProjection(StoryId("story:1"), "Canonical title", ContentType.MANGA, null),
            CatalogStoryProjection(StoryId("story:2"), "Other canonical title", ContentType.MANGA, "other.jpg"),
        ),
    )
}

private class StoryPromptCases(initial: List<ReconciliationCase>) : ReconciliationCaseRepository {
    private val cases = MutableStateFlow(initial)
    fun current(): List<ReconciliationCase> = cases.value

    override fun observePending(): Flow<List<ReconciliationCase>> = cases.map { list ->
        list.filter { it.status == ReconciliationCaseStatus.PENDING }
    }
    override fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>> = cases.map { list ->
        list.filter { it.key.left == storyId || it.key.right == storyId }
    }
    override suspend fun find(caseId: String): ReconciliationCase? = cases.value.firstOrNull { it.id == caseId }
    override suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase? = cases.value.firstOrNull { it.key == key }
    override suspend fun recordAssessment(
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCase? = findActive(key)

    override suspend fun resolveSeparate(
        caseId: String,
        expectedRevision: Long,
        origin: ReconciliationResolutionOrigin,
        resolvedAtEpochMillis: Long,
    ): Boolean {
        val current = find(caseId) ?: return false
        if (current.revision != expectedRevision || current.status != ReconciliationCaseStatus.PENDING) return false
        cases.value = cases.value.map { item ->
            if (item.id == caseId) item.copy(
                status = ReconciliationCaseStatus.RESOLVED_SEPARATE,
                resolutionOrigin = origin,
                revision = item.revision + 1,
                lastEvaluatedAtEpochMillis = resolvedAtEpochMillis,
            ) else item
        }
        return true
    }

    override suspend fun defer(caseId: String, expectedRevision: Long, suppressUntilEpochMillis: Long): Boolean {
        val current = find(caseId) ?: return false
        if (current.revision != expectedRevision || current.status != ReconciliationCaseStatus.PENDING) return false
        cases.value = cases.value.map { item ->
            if (item.id == caseId) item.copy(contextualPromptSuppressedUntilEpochMillis = suppressUntilEpochMillis) else item
        }
        return true
    }
}

private class StoryPromptMergeExecutor(private val result: StoryMergeResult) : StoryMergeExecutor {
    val requests = mutableListOf<StoryMergeRequest>()
    override suspend fun execute(request: StoryMergeRequest): StoryMergeResult {
        requests += request
        return result
    }
}

private class MutableStoryClock(var now: Long) : Clock {
    override fun nowEpochMillis(): Long = now
}

private fun promptCase(
    confidence: Double,
    revision: Long = 1,
    eligibility: ReconciliationMergeEligibility = ReconciliationMergeEligibility.MERGEABLE,
): ReconciliationCase {
    val fingerprint = "prompt-fingerprint"
    return ReconciliationCase(
        id = "prompt-case",
        key = ReconciliationCaseKey.of(StoryId("story:1"), StoryId("story:2")),
        status = ReconciliationCaseStatus.PENDING,
        assessment = ReconciliationAssessment(
            policyVersion = 1,
            semanticDecision = ReconciliationSemanticDecision.REVIEW,
            mergeEligibility = eligibility,
            confidence = confidence,
            titleSimilarity = confidence,
            authorSimilarity = null,
            winningLead = null,
            matchedIdentifiers = emptySet(),
            conflictingIdentifiers = emptySet(),
            reasons = setOf(ReconciliationReasonCode.TITLE_SIMILAR),
            identityEvidenceFingerprint = fingerprint,
        ),
        evidenceFingerprint = fingerprint,
        policyVersion = 1,
        resolutionOrigin = null,
        contextualPromptSuppressedUntilEpochMillis = null,
        revision = revision,
        createdAtEpochMillis = 1,
        lastEvaluatedAtEpochMillis = 2,
    )
}

private fun preparingState(storyId: StoryId = StoryId("story:1")): CanonicalStoryState.Preparing {
    val sources = sourceSummaries(storyId)
    return CanonicalStoryState.Preparing(
        Story(storyId, ContentType.MANGA),
        CanonicalHealth.REEVALUATING,
        CanonicalSourcePreference(storyId, CanonicalSourcePreferenceMode.AUTO, null, 0),
        sources,
    )
}

private fun readyState(storyId: StoryId = StoryId("story:1")): CanonicalStoryState.Ready {
    val preparing = preparingState(storyId)
    val primary = preparing.sources.first().sourceKey
    val generation = CanonicalGeneration(
        id = "gen:ready",
        storyId = storyId,
        fusionPolicyVersion = 1,
        primarySelectionPolicyVersion = 1,
        fusionFingerprint = "fusion:ready",
        effectivePrimary = primary,
        metadata = CanonicalMetadata(
            title = "Canonical title",
            description = "Canonical description",
            coverUrl = "canonical.jpg",
            sourceUrl = "canonical-url",
            popularityRank = null,
            aliases = listOf("Canonical alias"),
            authors = listOf("Canonical author"),
            genres = listOf("Fantasy"),
            languageTags = listOf("en"),
            publicationStatus = null,
            latestUpdate = null,
            score = null,
        ),
        health = CanonicalHealth.FRESH,
        provenance = mapOf(
            CanonicalFieldKey.TITLE to CanonicalFieldProvenance(
                CanonicalFieldKey.TITLE,
                CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK,
                listOf(CanonicalFieldContributor(primary, "fusion:a", CatalogMetadataLevel.Full)),
                listOf("primary"),
                1,
            ),
        ),
        createdAtEpochMillis = 1,
    )
    return CanonicalStoryState.Ready(
        preparing.story,
        CanonicalHealth.FRESH,
        preparing.preference,
        preparing.sources,
        generation,
    )
}

private fun sourceSummaries(storyId: StoryId): List<CanonicalSourceSummary> = listOf(
    sourceSummary(storyId, "catalog.b", "source-b", "Raw B"),
    sourceSummary(storyId, "catalog.a", "source-a", "Raw A"),
)

private fun sourceSummary(
    storyId: StoryId,
    pluginId: String,
    sourceId: String,
    title: String,
): CanonicalSourceSummary {
    val plugin = PluginId(pluginId)
    return CanonicalSourceSummary(
        sourceKey = SourceKey(plugin, sourceId),
        entry = CatalogEntry(storyId, plugin, sourceId, title, contentType = ContentType.MANGA),
        summary = CatalogMetadataStamp("1.0.0", 1),
        full = CatalogMetadataStamp("1.0.0", 2),
        identityFingerprint = "identity:$pluginId:$sourceId",
        fusionFingerprint = "fusion:$pluginId:$sourceId",
    )
}
