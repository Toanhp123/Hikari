package app.openstory.catalog.ui.story

import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.details.CatalogDetailsLoader
import app.openstory.catalog.details.CatalogFullMetadataFallbackService
import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CatalogFusionEngine
import app.openstory.catalog.fusion.CatalogSourceAvailabilityResolver
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataCoordinator
import app.openstory.catalog.metadata.CatalogMetadataPolicy
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.reconciliation.ReconciliationCaseRepository
import app.openstory.catalog.reconciliation.ReconciliationCaseStatus
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.catalog.reconciliation.ReconciliationReviewService
import app.openstory.catalog.identity.ProtectedContentMappingConflict
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.ui.state.ContentState
import app.openstory.common.Clock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

        assertEquals("Canonical title", viewModel.state.value.requireStory().preferredTitle)
        assertEquals("canonical.jpg", viewModel.state.value.requireStory().coverUrl)
        assertNull(viewModel.state.value.selectedSource)
    }

    @Test
    fun rawInspectionDoesNotChangeCanonicalPresentation() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(readyState())
        val viewModel = viewModel(canonical)
        runCurrent()

        viewModel.selectInspectionSource(SourceKey(PluginId("catalog.b"), "source-b"))
        runCurrent()

        assertEquals("Canonical title", viewModel.state.value.requireStory().preferredTitle)
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

        assertTrue(viewModel.state.value.content is ContentState.Failed)
        assertEquals(listOf(CanonicalFusionReason.BOOTSTRAP), rebuilder.reasons)
    }

    @Test
    fun preparingWhileBootstrapInFlightIsPendingNotUnavailable() = runTest(dispatcher.scheduler) {
        val gate = CompletableDeferred<Unit>()
        val canonical = FakeCanonicalRepository(preparingState())
        val rebuilder = RecordingRebuilder(canonical, gate = gate)
        val viewModel = viewModel(canonical, rebuilder)
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Pending)

        gate.complete(Unit)
        runCurrent()
    }

    @Test
    fun bootstrapReturningPreparingTerminatesAsFailedNotPermanentPending() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(preparingState())
        val viewModel = viewModel(canonical)
        runCurrent()

        val failed = viewModel.state.value.content as ContentState.Failed
        assertEquals("catalog.story.canonical_still_preparing", failed.failure.code)
        assertFalse(failed.failure.retryable)
    }

    @Test
    fun canonicalReadyBecomesReadyStory() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(FakeCanonicalRepository(readyState()))
        runCurrent()

        val ready = viewModel.state.value.content as ContentState.Ready
        assertEquals("Canonical title", ready.value.preferredTitle)
    }

    @Test
    fun bootstrapReadyRendersEvenWhenCanonicalObservationFirstFails() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(readyState(), observeFails = true)
        val viewModel = viewModel(canonical)
        runCurrent()

        val ready = viewModel.state.value.content as ContentState.Ready
        assertEquals("Canonical title", ready.value.preferredTitle)
        assertEquals("catalog.story.observe_exception", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun observationFailureAfterPreparingSnapshotKeepsBootstrapReadyUsable() = runTest(dispatcher.scheduler) {
        val gate = CompletableDeferred<Unit>()
        val canonical = FakeCanonicalRepository(
            initial = preparingState(),
            observedInitial = preparingState(),
        )
        val viewModel = viewModel(canonical, RecordingRebuilder(canonical, gate = gate))
        runCurrent()
        assertTrue(viewModel.state.value.content is ContentState.Pending)

        canonical.setCurrent(readyState())
        gate.complete(Unit)
        runCurrent()
        assertTrue(viewModel.state.value.content is ContentState.Ready)

        canonical.failObservation()
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertEquals("catalog.story.observe_exception", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun bootstrapReadyNeverTransitionsThroughFalseStillPreparingFailure() = runTest(dispatcher.scheduler) {
        val gate = CompletableDeferred<Unit>()
        val canonical = FakeCanonicalRepository(preparingState(), observeFails = true)
        val rebuilder = RecordingRebuilder(canonical, gate = gate)
        val viewModel = viewModel(canonical, rebuilder)
        val observed = mutableListOf<ContentState<StoryUiModel>>()
        backgroundScope.launch { viewModel.state.map { it.content }.collect { observed += it } }
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Pending)

        canonical.emit(readyState())
        gate.complete(Unit)
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertFalse(
            observed.any { content ->
                content is ContentState.Failed && content.failure.code == "catalog.story.canonical_still_preparing"
            },
        )
    }

    @Test
    fun resolvedStoryIdChangeCannotReuseOldPersonalObservationState() = runTest(dispatcher.scheduler) {
        val storyA = StoryId("story:a")
        val storyB = StoryId("story:b")
        val canonical = FakeCanonicalRepository(readyState(storyA))
        val libraryRepository = MutableLibraryRepository(
            listOf(LibraryEntry(storyA, LibraryStatus.READING, 1L, 1L)),
        )
        val progressRepository = MutableProgressRepository(listOf(progressFor(storyA)))
        val viewModel = viewModel(
            canonical = canonical,
            libraryRepository = libraryRepository,
            progressRepository = progressRepository,
        )
        runCurrent()

        assertEquals(LibraryStatus.READING, viewModel.state.value.libraryStatus)
        assertEquals(storyA, viewModel.state.value.resumeTarget?.storyId)

        canonical.emit(readyState(storyB))
        runCurrent()

        assertEquals(storyB, viewModel.state.value.storyId)
        assertTrue(viewModel.state.value.libraryStatusResolved)
        assertNull(viewModel.state.value.libraryStatus)
        assertNull(viewModel.state.value.resumeTarget)
    }

    @Test
    fun canonicalObservationStaysScopedToStableRouteStoryId() = runTest(dispatcher.scheduler) {
        val routeId = StoryId("story:retired")
        val canonical = FakeCanonicalRepository(
            readyState(StoryId("story:survivor")),
            aliases = setOf(routeId),
        )
        val engine = RecordingStoryEngineEventSink()
        val viewModel = viewModel(canonical, engine = engine, storyId = routeId)
        runCurrent()

        canonical.emit(readyState(StoryId("story:merged-again")))
        runCurrent()
        viewModel.pinPrimary(SourceKey(PluginId("catalog.b"), "source-b"))
        runCurrent()

        assertEquals(listOf(routeId), canonical.observedIds.distinct())
        assertEquals(listOf(routeId), canonical.preferenceRequestStoryIds)
        assertEquals(listOf(routeId), engine.preferenceChanges)
        assertEquals(StoryId("story:merged-again"), viewModel.state.value.storyId)
    }

    @Test
    fun bootstrapExceptionWithoutContentIsBlockingFailed() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(preparingState())
        val rebuilder = RecordingRebuilder(canonical, failure = IllegalStateException("boom"))
        val viewModel = viewModel(canonical, rebuilder)
        runCurrent()

        val failed = viewModel.state.value.content as ContentState.Failed
        assertEquals("catalog.story.canonical_bootstrap_failed", failed.failure.code)
        assertTrue(failed.failure.retryable)
    }

    @Test
    fun contentRetryRestartsCanonicalObservationAndBootstrapNotSourceRefresh() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(preparingState(), observeFails = true)
        val rebuilder = RecordingRebuilder(canonical)
        val viewModel = viewModel(canonical, rebuilder)
        runCurrent()
        val firstObserveAttempts = canonical.observeAttempts
        val firstBootstrapAttempts = rebuilder.reasons.size

        viewModel.retryContent()
        runCurrent()

        assertTrue(canonical.observeAttempts > firstObserveAttempts)
        assertTrue(rebuilder.reasons.size > firstBootstrapAttempts)
        assertFalse(viewModel.state.value.refresh.inProgress)
        assertNull(viewModel.state.value.refresh.failure)
    }

    @Test
    fun observationRetryRestartsSurfacedCanonicalIssueWithoutRestartingBootstrap() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(readyState(), observeFails = true)
        val rebuilder = RecordingRebuilder(canonical)
        val viewModel = viewModel(canonical, rebuilder)
        runCurrent()
        val firstObserveAttempts = canonical.observeAttempts
        val firstBootstrapAttempts = rebuilder.reasons.size

        viewModel.retryObservation()
        runCurrent()

        assertTrue(canonical.observeAttempts > firstObserveAttempts)
        assertEquals(firstBootstrapAttempts, rebuilder.reasons.size)
        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertEquals("catalog.story.observe_exception", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun observationRetryDoesNothingWhileCanonicalReadinessIsBlocking() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(preparingState(), observeFails = true)
        val viewModel = viewModel(canonical)
        runCurrent()
        val observeAttempts = canonical.observeAttempts

        viewModel.retryObservation()
        runCurrent()

        assertEquals(observeAttempts, canonical.observeAttempts)
        assertTrue(viewModel.state.value.content is ContentState.Failed)
    }

    @Test
    fun bootstrapReadyRemainsUsableWhenRecoveredObservationHasNoCanonicalSnapshot() =
        runTest(dispatcher.scheduler) {
            val canonical = FakeCanonicalRepository(readyState(), observeFails = true)
            val viewModel = viewModel(canonical)
            runCurrent()

            assertTrue(viewModel.state.value.content is ContentState.Ready)

            canonical.emit(null)
            canonical.observeFails = false
            viewModel.retryObservation()
            runCurrent()

            assertTrue(viewModel.state.value.content is ContentState.Ready)
            assertNull(viewModel.state.value.observationIssue)
        }

    @Test
    fun recoveredCanonicalPreparingInvalidatesOlderBootstrapFallback() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(readyState(), observeFails = true)
        val viewModel = viewModel(canonical)
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Ready)

        canonical.emit(preparingState())
        canonical.observeFails = false
        viewModel.retryObservation()
        runCurrent()

        val failed = viewModel.state.value.content as ContentState.Failed
        assertEquals("catalog.story.canonical_still_preparing", failed.failure.code)
        assertNull(viewModel.state.value.observationIssue)
    }

    @Test
    fun sourceDetailRefreshDoesNothingWithoutReadyStory() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(FakeCanonicalRepository(preparingState()))
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertFalse(viewModel.state.value.refresh.inProgress)
        assertNull(viewModel.state.value.refresh.failure)
        assertTrue(viewModel.state.value.content is ContentState.Failed)
    }

    @Test
    fun sourceDetailRefreshKeepsReadyStoryVisible() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(FakeCanonicalRepository(readyState()))
        runCurrent()

        viewModel.refresh()
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertFalse(viewModel.state.value.refresh.inProgress)
        assertTrue(viewModel.state.value.refresh.failure != null)
    }

    @Test
    fun observationBootstrapPreferenceAndRefreshFailuresDoNotOverwriteEachOther() = runTest(dispatcher.scheduler) {
        val canonical = FakeCanonicalRepository(readyState(), observeFails = true)
        val engine = RecordingStoryEngineEventSink().apply {
            preferenceResult = CanonicalFusionResult.Failed(
                StoryId("story:1"),
                "catalog.story.preference_failed",
                retryable = true,
            )
        }
        val viewModel = viewModel(canonical, engine = engine)
        runCurrent()

        assertEquals("catalog.story.observe_exception", viewModel.state.value.observationIssue?.code)

        viewModel.refresh()
        runCurrent()
        assertTrue(viewModel.state.value.refresh.failure != null)
        assertEquals("catalog.story.observe_exception", viewModel.state.value.observationIssue?.code)

        viewModel.selectInspectionSource(SourceKey(PluginId("catalog.b"), "source-b"))
        runCurrent()
        assertTrue(viewModel.state.value.refresh.failure != null)
        assertEquals("catalog.story.observe_exception", viewModel.state.value.observationIssue?.code)

        viewModel.pinPrimary(SourceKey(PluginId("catalog.b"), "source-b"))
        runCurrent()
        assertEquals("catalog.story.preference_failed", viewModel.state.value.commandFailure?.code)
        assertTrue(viewModel.state.value.refresh.failure != null)
        assertEquals("catalog.story.observe_exception", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun libraryAndProgressEnrichmentDoNotBlockStoryBody() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(
            canonical = FakeCanonicalRepository(readyState()),
            libraryRepository = NeverLibraryRepository(),
            progressRepository = NeverProgressRepository,
        )
        runCurrent()

        assertTrue(viewModel.state.value.content is ContentState.Ready)
        assertFalse(viewModel.state.value.libraryStatusResolved)
        assertNull(viewModel.state.value.resumeTarget)
    }

    @Test
    fun unresolvedLibraryMembershipDoesNotPretendStoryIsNotInLibrary() = runTest(dispatcher.scheduler) {
        val libraryRepository = NeverLibraryRepository()
        val viewModel = viewModel(
            canonical = FakeCanonicalRepository(readyState()),
            libraryRepository = libraryRepository,
        )
        runCurrent()

        assertFalse(viewModel.state.value.libraryStatusResolved)
        assertNull(viewModel.state.value.libraryStatus)

        viewModel.changeLibraryStatus(LibraryStatus.READING)
        runCurrent()

        assertEquals(0, libraryRepository.mutationCalls)
    }

    @Test
    fun libraryMutationUsesTheResolvedSnapshotVisibleWhenActionWasAccepted() = runTest(dispatcher.scheduler) {
        val storyA = StoryId("story:a")
        val storyB = StoryId("story:b")
        val canonical = FakeCanonicalRepository(readyState(storyA))
        val libraryRepository = MutableLibraryRepository(
            listOf(LibraryEntry(storyA, LibraryStatus.READING, 1L, 1L)),
        )
        val viewModel = viewModel(canonical = canonical, libraryRepository = libraryRepository)
        runCurrent()

        canonical.emit(readyState(storyB))
        viewModel.changeLibraryStatus(LibraryStatus.COMPLETED)
        runCurrent()

        assertEquals(listOf(storyA), libraryRepository.mutatedStoryIds)
    }

    @Test
    fun retiredAssistedStoryIdUsesResolvedCanonicalStoryId() = runTest(dispatcher.scheduler) {
        val survivor = readyState(StoryId("story:survivor"))
        val canonical = FakeCanonicalRepository(survivor, aliases = setOf(StoryId("story:retired")))
        val viewModel = viewModel(canonical, storyId = StoryId("story:retired"))
        runCurrent()

        assertEquals(StoryId("story:survivor"), viewModel.state.value.storyId)
        assertEquals(StoryId("story:survivor"), viewModel.state.value.requireStory().storyId)
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
        assertTrue(dependencies.any { it.endsWith("CatalogFullMetadataFallbackService") })
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
        libraryRepository: LibraryRepository = FakeLibraryRepository(),
        progressRepository: ReadingProgressRepository = FakeProgressRepository(),
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
        val identity = StoryViewModelIdentityRepository(canonical)
        val fullMetadata = CatalogFullMetadataFallbackService(
            canonical = canonical,
            metadata = metadata,
            fusion = CatalogFusionEngine(),
            availability = CatalogSourceAvailabilityResolver(registry, CatalogMetadataPolicy(clock)),
            identity = identity,
        )
        return StoryViewModel(
            StoryAssistedArgs(storyId),
            canonical,
            CanonicalBootstrapUseCase(canonical, rebuilder),
            fullMetadata,
            metadata,
            engine,
            LibraryService(libraryRepository, clock, NoOpMappingScheduler),
            progressRepository,
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
