package app.openstory.catalog.ui.review

import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.ProtectedContentMappingConflict
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.orchestration.CatalogEvidenceChange
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.identity.StoryMergeReverseRequest
import app.openstory.catalog.identity.StoryMergeReverseResult
import app.openstory.catalog.identity.StoryMergeReversalAssessment
import app.openstory.catalog.identity.StoryMergeReversalAssessmentResult
import app.openstory.catalog.identity.StoryMergeReversalExecutor
import app.openstory.catalog.identity.StoryMergeReversalPlanner
import app.openstory.catalog.identity.StoryMergeReversibility
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.identity.StoryUserStateFootprintReader
import app.openstory.catalog.identity.UserStateFootprint
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.reconciliation.EmptyStoryMergeLineageReader
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
import app.openstory.catalog.reconciliation.StoryMergeLineage
import app.openstory.catalog.reconciliation.StoryMergeLineageReader
import app.openstory.common.Clock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
class ReconciliationReviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun queueRankingIsDeterministicAndPresentationOnly() = runTest(dispatcher.scheduler) {
        val cases = FakeCases(
            listOf(
                case("case-z", "a", "b", confidence = 0.90, evaluatedAt = 40, createdAt = 5),
                case("case-b", "c", "d", confidence = 0.90, evaluatedAt = 60, createdAt = 20),
                case("case-a", "e", "f", confidence = 0.90, evaluatedAt = 60, createdAt = 10),
                case("case-aa", "i", "j", confidence = 0.90, evaluatedAt = 60, createdAt = 10),
                case("case-impact", "k", "l", confidence = 0.90, evaluatedAt = 1, createdAt = 100),
                case("case-high", "g", "h", confidence = 0.95, evaluatedAt = 1, createdAt = 100),
            ),
        )
        val assessmentBefore = cases.current().associate { it.id to it.assessment }
        val footprints = FakeFootprints(
            mapOf(
                StoryId("c") to UserStateFootprint(false, 1, 0, false, 0),
                StoryId("d") to UserStateFootprint(false, 1, 0, false, 0),
                StoryId("e") to UserStateFootprint(false, 1, 0, false, 0),
                StoryId("f") to UserStateFootprint(false, 1, 0, false, 0),
                StoryId("i") to UserStateFootprint(false, 1, 0, false, 0),
                StoryId("j") to UserStateFootprint(false, 1, 0, false, 0),
                StoryId("k") to UserStateFootprint(true, 1, 0, false, 0),
                StoryId("l") to UserStateFootprint(true, 1, 0, false, 0),
            ),
        )
        val viewModel = viewModel(cases, footprints = footprints)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertEquals(
            listOf("case-high", "case-impact", "case-a", "case-aa", "case-b", "case-z"),
            viewModel.state.value.items.map { it.caseId },
        )
        assertEquals(assessmentBefore, cases.current().associate { it.id to it.assessment })
    }

    @Test
    fun mergeAvailabilityFollowsInvariantEligibility() = runTest(dispatcher.scheduler) {
        val cases = FakeCases(
            listOf(
                case("mergeable", "a", "b"),
                case("blocked", "c", "d", eligibility = ReconciliationMergeEligibility.INVARIANT_BLOCKED),
            ),
        )
        val viewModel = viewModel(cases)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        assertTrue(viewModel.state.value.items.first { it.caseId == "mergeable" }.mergeAllowed)
        assertFalse(viewModel.state.value.items.first { it.caseId == "blocked" }.mergeAllowed)
    }

    @Test
    fun keepSeparateUsesExactRevisionAndRemovesResolvedItem() = runTest(dispatcher.scheduler) {
        val cases = FakeCases(listOf(case("case-1", "a", "b", revision = 7)))
        val viewModel = viewModel(cases)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        viewModel.keepSeparate("case-1", 7)
        runCurrent()

        assertEquals(listOf("case-1" to 7L), cases.resolveSeparateCalls)
        assertTrue(viewModel.state.value.items.isEmpty())
    }

    @Test
    fun deferUsesExactRevisionAndLeavesItemInQueue() = runTest(dispatcher.scheduler) {
        val cases = FakeCases(listOf(case("case-1", "a", "b", revision = 4)))
        val viewModel = viewModel(cases, clock = Clock { 1_000L })
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        viewModel.defer("case-1", 4)
        runCurrent()

        assertEquals(1, cases.deferCalls.size)
        assertEquals("case-1", cases.deferCalls.single().first)
        assertEquals(4L, cases.deferCalls.single().second)
        assertEquals(86_401_000L, cases.deferCalls.single().third)
        assertEquals(listOf("case-1"), viewModel.state.value.items.map { it.caseId })
    }

    @Test
    fun protectedConflictRequiresCandidateSelectionFromDomainResultBeforeResubmission() = runTest(dispatcher.scheduler) {
        val cases = FakeCases(listOf(case("case-1", "a", "b")))
        val merge = SequenceMergeExecutor(
            mutableListOf(
                StoryMergeResult.ReviewRequired(
                    reasons = setOf("protected_content_mapping_conflict"),
                    protectedContentMappingConflicts = listOf(
                        ProtectedContentMappingConflict(
                            PluginId("plugin.z"),
                            setOf("source-b", "source-a"),
                        ),
                    ),
                ),
                StoryMergeResult.Merged(StoryId("a"), "merge-1"),
            ),
        )
        val viewModel = viewModel(cases, merge = merge)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        viewModel.merge("case-1", 1)
        runCurrent()
        val conflict = requireNotNull(viewModel.state.value.protectedConflict)
        assertEquals(listOf("source-a", "source-b"), conflict.conflicts.single().candidateSourceStoryIds)

        viewModel.selectProtectedMapping(PluginId("plugin.z"), "free-form")
        assertNull(viewModel.state.value.protectedConflict?.conflicts?.single()?.selectedSourceStoryId)

        viewModel.selectProtectedMapping(PluginId("plugin.z"), "source-b")
        viewModel.confirmProtectedMerge()
        runCurrent()

        assertEquals(2, merge.requests.size)
        assertEquals(1L, conflict.expectedCaseRevision)
        assertEquals("source-b", merge.requests.last().resolutions.single().let {
            (it as app.openstory.catalog.identity.StoryMergeResolution.ContentMappingTarget).sourceStoryId
        })
        assertNull(viewModel.state.value.protectedConflict)
    }

    @Test
    fun nonResolvableDomainConflictDoesNotFabricateProtectedChoice() = runTest(dispatcher.scheduler) {
        val cases = FakeCases(listOf(case("case-1", "a", "b")))
        val merge = SequenceMergeExecutor(
            mutableListOf(
                StoryMergeResult.ReviewRequired(setOf("chapter_state_change_required")),
            ),
        )
        val viewModel = viewModel(cases, merge = merge)
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        viewModel.merge("case-1", 1)
        runCurrent()

        assertNull(viewModel.state.value.protectedConflict)
        assertEquals(listOf("Chapter state change required"), viewModel.state.value.domainConflictReasonLabels)
        assertEquals(listOf("case-1"), viewModel.state.value.items.map { it.caseId })
    }

    @Test
    fun postMergeCorrectionProjectsSafeReverseAndExecutesExactRevision() = runTest(dispatcher.scheduler) {
        val cases = FakeCases(
            listOf(
                case(
                    "case-1",
                    "a",
                    "b",
                    revision = 3,
                    eligibility = ReconciliationMergeEligibility.INVARIANT_BLOCKED,
                ),
            ),
        )
        val planner = FakeReversalPlanner(
            StoryMergeReversalAssessmentResult.Assessed(
                StoryMergeReversalAssessment(
                    mergeEventId = "merge-1",
                    survivingStoryId = StoryId("a"),
                    restoredStoryId = StoryId("b"),
                    reversibility = StoryMergeReversibility.REVERSIBLE,
                    reasonCodes = emptySet(),
                ),
            ),
        )
        val reversal = FakeReversalExecutor(
            StoryMergeReverseResult.Reversed(StoryId("b"), StoryId("a"), "reverse-1"),
        )
        val viewModel = viewModel(
            cases = cases,
            reversalPlanner = planner,
            reversalExecutor = reversal,
            identity = VmIdentityRepository(StoryId("a"), 8),
            lineages = VmLineageReader(
                StoryMergeLineage(
                    mergeEventId = "merge-1",
                    survivorStoryId = StoryId("a"),
                    retiredStoryId = StoryId("b"),
                    reconciliationCaseId = "historical",
                    survivorSourceKeysBefore = setOf(SourceKey(PluginId("plugin:a"), "source:a")),
                    retiredSourceKeysBefore = setOf(SourceKey(PluginId("plugin:b"), "source:b")),
                    mergedAtEpochMillis = 50,
                ),
            ),
        )
        backgroundScope.launch { viewModel.state.collect {} }
        runCurrent()

        val item = viewModel.state.value.items.single()
        assertTrue(item.isPostMergeCorrection)
        assertTrue(item.reverseAllowed)

        viewModel.reverse("case-1", 3)
        runCurrent()

        assertEquals(1, reversal.requests.size)
        assertEquals(3L, reversal.requests.single().expectedReconciliationCaseRevision)
        assertTrue(viewModel.state.value.failureMessage == null)
        assertEquals("case-1", viewModel.state.value.items.single().caseId)
    }

    private fun viewModel(
        cases: FakeCases,
        footprints: StoryUserStateFootprintReader = FakeFootprints(emptyMap()),
        merge: StoryMergeExecutor = SequenceMergeExecutor(mutableListOf(StoryMergeResult.Merged(StoryId("a"), "m"))),
        clock: Clock = Clock { 1_000L },
        reversalPlanner: StoryMergeReversalPlanner = StoryMergeReversalPlanner {
            StoryMergeReversalAssessmentResult.NotFound
        },
        reversalExecutor: StoryMergeReversalExecutor = StoryMergeReversalExecutor {
            StoryMergeReverseResult.NotFound
        },
        identity: StoryIdentityRepository? = null,
        lineages: StoryMergeLineageReader = EmptyStoryMergeLineageReader,
    ) = ReconciliationReviewViewModel(
        cases = cases,
        projections = FakeProjections(cases.current().flatMap { listOf(it.key.left, it.key.right) }.toSet()),
        footprints = footprints,
        review = ReconciliationReviewService(
            cases = cases,
            mergeExecutor = merge,
            clock = clock,
            orchestrator = NoOpReviewEngine,
            reversalPlanner = reversalPlanner,
            reversalExecutor = reversalExecutor,
            identity = identity,
            lineages = lineages,
        ),
        clock = clock,
    )
}

private object NoOpReviewEngine : CanonicalEngineEventSink {
    override suspend fun onEvidenceChanged(change: CatalogEvidenceChange) = Unit
    override suspend fun onSourceLinked(storyId: StoryId, sourceKey: SourceKey) = Unit
    override suspend fun onSourceUnlinked(storyId: StoryId, sourceKey: SourceKey) = Unit
    override suspend fun onSourcePreferenceChanged(storyId: StoryId): CanonicalFusionResult =
        CanonicalFusionResult.Preparing(storyId)
    override suspend fun onStoryMerged(storyId: StoryId): CanonicalFusionResult = CanonicalFusionResult.Preparing(storyId)
}

private class FakeReversalPlanner(
    private val result: StoryMergeReversalAssessmentResult,
) : StoryMergeReversalPlanner {
    val requests = mutableListOf<StoryMergeReverseRequest>()

    override suspend fun assess(request: StoryMergeReverseRequest): StoryMergeReversalAssessmentResult {
        requests += request
        return result
    }
}

private class FakeReversalExecutor(
    private val result: StoryMergeReverseResult,
) : StoryMergeReversalExecutor {
    val requests = mutableListOf<StoryMergeReverseRequest>()

    override suspend fun reverse(request: StoryMergeReverseRequest): StoryMergeReverseResult {
        requests += request
        return result
    }
}

private class VmIdentityRepository(
    private val resolved: StoryId,
    private val revision: Long,
) : StoryIdentityRepository {
    override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(resolved)
    override suspend fun resolve(storyId: StoryId): StoryId = resolved
    override suspend fun identityState(storyId: StoryId): CanonicalIdentityState =
        CanonicalIdentityState(resolved, revision, createdAtEpochMillis = 1)
}

private class VmLineageReader(
    private vararg val values: StoryMergeLineage,
) : StoryMergeLineageReader {
    override suspend fun lineagesFor(storyId: StoryId): List<StoryMergeLineage> = values.toList()
}

private class FakeCases(initial: List<ReconciliationCase>) : ReconciliationCaseRepository {
    private val all = linkedMapOf<String, ReconciliationCase>().apply { initial.forEach { put(it.id, it) } }
    private val pending = MutableStateFlow(all.values.filter { it.status == ReconciliationCaseStatus.PENDING })
    val resolveSeparateCalls = mutableListOf<Pair<String, Long>>()
    val deferCalls = mutableListOf<Triple<String, Long, Long>>()

    fun current(): List<ReconciliationCase> = all.values.toList()

    override fun observePending(): Flow<List<ReconciliationCase>> = pending
    override fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>> = flowOf(
        all.values.filter { storyId == it.key.left || storyId == it.key.right },
    )
    override suspend fun find(caseId: String): ReconciliationCase? = all[caseId]
    override suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase? = all.values.firstOrNull { it.key == key }
    override suspend fun recordAssessment(
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCase? = all.values.firstOrNull { it.key == key }

    override suspend fun resolveSeparate(
        caseId: String,
        expectedRevision: Long,
        origin: ReconciliationResolutionOrigin,
        resolvedAtEpochMillis: Long,
    ): Boolean {
        resolveSeparateCalls += caseId to expectedRevision
        val current = all[caseId] ?: return false
        if (current.revision != expectedRevision) return false
        all[caseId] = current.copy(
            status = ReconciliationCaseStatus.RESOLVED_SEPARATE,
            resolutionOrigin = origin,
            revision = current.revision + 1,
            lastEvaluatedAtEpochMillis = resolvedAtEpochMillis,
        )
        publish()
        return true
    }

    override suspend fun defer(caseId: String, expectedRevision: Long, suppressUntilEpochMillis: Long): Boolean {
        deferCalls += Triple(caseId, expectedRevision, suppressUntilEpochMillis)
        val current = all[caseId] ?: return false
        if (current.revision != expectedRevision) return false
        all[caseId] = current.copy(contextualPromptSuppressedUntilEpochMillis = suppressUntilEpochMillis)
        publish()
        return true
    }

    private fun publish() {
        pending.value = all.values.filter { it.status == ReconciliationCaseStatus.PENDING }
    }
}

private class FakeFootprints(
    private val values: Map<StoryId, UserStateFootprint>,
) : StoryUserStateFootprintReader {
    override suspend fun read(storyIds: Set<StoryId>): Map<StoryId, UserStateFootprint> =
        values.filterKeys { it in storyIds }
}

private class FakeProjections(storyIds: Set<StoryId>) : CatalogStoryProjectionRepository {
    private val values = storyIds.sortedBy { it.value }.map { id ->
        CatalogStoryProjection(id, "Title ${id.value}", ContentType.MANGA, "${id.value}.jpg")
    }
    override fun observe(): Flow<List<CatalogStoryProjection>> = flowOf(values)
}

private class SequenceMergeExecutor(
    private val results: MutableList<StoryMergeResult>,
) : StoryMergeExecutor {
    val requests = mutableListOf<StoryMergeRequest>()
    override suspend fun execute(request: StoryMergeRequest): StoryMergeResult {
        requests += request
        return results.removeAt(0)
    }
}

private fun case(
    id: String,
    left: String,
    right: String,
    confidence: Double = 0.80,
    eligibility: ReconciliationMergeEligibility = ReconciliationMergeEligibility.MERGEABLE,
    evaluatedAt: Long = 10,
    createdAt: Long = 1,
    revision: Long = 1,
): ReconciliationCase {
    val key = ReconciliationCaseKey.of(StoryId(left), StoryId(right))
    val fingerprint = "fp:$id"
    return ReconciliationCase(
        id = id,
        key = key,
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
        createdAtEpochMillis = createdAt,
        lastEvaluatedAtEpochMillis = evaluatedAt,
    )
}
