package app.openstory.catalog.reconciliation

import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.identity.ProtectedContentMappingConflict
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.orchestration.CatalogEvidenceChange
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResolution
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.common.Clock
import app.openstory.common.FakeClock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ReconciliationReviewServiceTest {
    @Test
    fun committedMergeNotifiesCanonicalEngineWithSurvivor() = runTest {
        val case = reviewCase()
        val engine = RecordingReviewEngine()
        val service = reviewService(
            RecordingCases(case),
            RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "merge:notify")),
            FakeClock(1_000),
            engine,
        )

        val result = service.resolve(command(case, ReconciliationReviewAction.MERGE))

        assertEquals(ReconciliationReviewResult.Merged(StoryId("story:a")), result)
        assertEquals(listOf(StoryId("story:a")), engine.merged)
    }

    @Test
    fun postMergeOrchestrationFailureDoesNotRewriteCommittedMergeResult() = runTest {
        val case = reviewCase()
        val engine = RecordingReviewEngine(failOnMerge = true)
        val service = reviewService(
            RecordingCases(case),
            RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "merge:durable")),
            FakeClock(1_000),
            engine,
        )

        val result = service.resolve(command(case, ReconciliationReviewAction.MERGE))

        assertEquals(ReconciliationReviewResult.Merged(StoryId("story:a")), result)
        assertEquals(listOf(StoryId("story:a")), engine.merged)
    }

    @Test
    fun reviewRequiredDoesNotNotifyCanonicalEngine() = runTest {
        val case = reviewCase()
        val engine = RecordingReviewEngine()
        val service = reviewService(
            RecordingCases(case),
            RecordingMergeExecutor(StoryMergeResult.ReviewRequired(setOf("blocked"))),
            FakeClock(1_000),
            engine,
        )

        service.resolve(command(case, ReconciliationReviewAction.MERGE))

        assertTrue(engine.merged.isEmpty())
    }

    @Test
    fun mergeUsesSharedExecutorWithUserReviewContext() = runTest {
        val case = reviewCase()
        val cases = RecordingCases(case)
        val executor = RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "merge:1"))
        val service = reviewService(cases, executor, FakeClock(1_000))

        val result = service.resolve(command(case, ReconciliationReviewAction.MERGE))

        assertEquals(ReconciliationReviewResult.Merged(StoryId("story:a")), result)
        val request = executor.requests.single()
        assertEquals(StoryMergeOrigin.USER_REVIEW_APPROVAL, request.origin)
        assertEquals(case.id, request.reconciliationCaseId)
        assertEquals(case.key.left, request.leftStoryId)
        assertEquals(case.key.right, request.rightStoryId)
        assertEquals(case.evidenceFingerprint, request.evidenceFingerprint)
        assertEquals(case.policyVersion, request.reconciliationPolicyVersion)
    }

    @Test
    fun invariantBlockedReviewDoesNotCallExecutor() = runTest {
        val case = reviewCase(mergeEligibility = ReconciliationMergeEligibility.INVARIANT_BLOCKED)
        val executor = RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "unused"))
        val service = reviewService(RecordingCases(case), executor, FakeClock(1_000))

        assertEquals(
            ReconciliationReviewResult.InvariantBlocked,
            service.resolve(command(case, ReconciliationReviewAction.MERGE)),
        )
        assertTrue(executor.requests.isEmpty())
    }

    @Test
    fun protectedMappingConflictIsReturnedWithoutResolvingCase() = runTest {
        val case = reviewCase()
        val conflict = ProtectedContentMappingConflict(
            pluginId = PluginId("plugin:content"),
            candidateSourceStoryIds = setOf("source:a", "source:b"),
        )
        val cases = RecordingCases(case)
        val executor = RecordingMergeExecutor(
            StoryMergeResult.ReviewRequired(
                reasons = setOf("content_mapping.protected_conflict"),
                protectedContentMappingConflicts = listOf(conflict),
            ),
        )
        val service = reviewService(cases, executor, FakeClock(1_000))

        assertEquals(
            ReconciliationReviewResult.ConflictResolutionRequired(listOf(conflict)),
            service.resolve(command(case, ReconciliationReviewAction.MERGE)),
        )
        assertEquals(ReconciliationCaseStatus.PENDING, requireNotNull(cases.current).status)
    }

    @Test
    fun protectedMappingConflictsAreReturnedInDeterministicOrder() = runTest {
        val case = reviewCase()
        val executor = RecordingMergeExecutor(
            StoryMergeResult.ReviewRequired(
                reasons = setOf("content_mapping.protected_conflict"),
                protectedContentMappingConflicts = listOf(
                    ProtectedContentMappingConflict(
                        pluginId = PluginId("plugin:z"),
                        candidateSourceStoryIds = linkedSetOf("source:z2", "source:z1"),
                    ),
                    ProtectedContentMappingConflict(
                        pluginId = PluginId("plugin:a"),
                        candidateSourceStoryIds = linkedSetOf("source:a2", "source:a1"),
                    ),
                ),
            ),
        )
        val service = reviewService(RecordingCases(case), executor, FakeClock(1_000))

        val result = assertIs<ReconciliationReviewResult.ConflictResolutionRequired>(
            service.resolve(command(case, ReconciliationReviewAction.MERGE)),
        )

        assertEquals(listOf("plugin:a", "plugin:z"), result.conflicts.map { it.pluginId.value })
        assertEquals(listOf("source:a1", "source:a2"), result.conflicts.first().candidateSourceStoryIds.toList())
        assertEquals(listOf("source:z1", "source:z2"), result.conflicts.last().candidateSourceStoryIds.toList())
    }

    @Test
    fun explicitProtectedMappingResolutionIsTranslatedIntoMergeResolution() = runTest {
        val case = reviewCase()
        val executor = RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "merge:2"))
        val service = reviewService(RecordingCases(case), executor, FakeClock(1_000))
        val pluginId = PluginId("plugin:content")

        service.resolve(
            command(
                case,
                ReconciliationReviewAction.MERGE,
                protectedMappingResolutions = listOf(ProtectedMappingResolution(pluginId, "source:b")),
            ),
        )

        assertEquals(
            listOf(StoryMergeResolution.ContentMappingTarget(pluginId, "source:b")),
            executor.requests.single().resolutions,
        )
    }

    @Test
    fun duplicateProtectedMappingSelectionsAreRejectedBeforeExecutor() = runTest {
        val case = reviewCase()
        val pluginId = PluginId("plugin:content")
        val executor = RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "unused"))
        val service = reviewService(RecordingCases(case), executor, FakeClock(1_000))

        val result = service.resolve(
            command(
                case,
                ReconciliationReviewAction.MERGE,
                protectedMappingResolutions = listOf(
                    ProtectedMappingResolution(pluginId, "source:a"),
                    ProtectedMappingResolution(pluginId, "source:b"),
                ),
            ),
        )

        assertEquals(ReconciliationReviewResult.StaleCase, result)
        assertTrue(executor.requests.isEmpty())
    }

    @Test
    fun nonMappingDomainConflictReturnsStableReasonCodesAndLeavesCasePending() = runTest {
        val case = reviewCase()
        val cases = RecordingCases(case)
        val executor = RecordingMergeExecutor(
            StoryMergeResult.ReviewRequired(
                reasons = setOf("canonical_source_preference.pinned_conflict"),
            ),
        )
        val service = reviewService(cases, executor, FakeClock(1_000))

        assertEquals(
            ReconciliationReviewResult.DomainStateChangeRequired(
                setOf("canonical_source_preference.pinned_conflict"),
            ),
            service.resolve(command(case, ReconciliationReviewAction.MERGE)),
        )
        assertEquals(ReconciliationCaseStatus.PENDING, requireNotNull(cases.current).status)
    }

    @Test
    fun domainStateChangeReasonCodesAreReturnedInDeterministicOrder() = runTest {
        val case = reviewCase()
        val executor = RecordingMergeExecutor(
            StoryMergeResult.ReviewRequired(
                reasons = linkedSetOf("z.reason", "a.reason"),
            ),
        )
        val service = reviewService(RecordingCases(case), executor, FakeClock(1_000))

        val result = assertIs<ReconciliationReviewResult.DomainStateChangeRequired>(
            service.resolve(command(case, ReconciliationReviewAction.MERGE)),
        )

        assertEquals(listOf("a.reason", "z.reason"), result.reasonCodes.toList())
    }

    @Test
    fun keepSeparatePersistsUserResolutionAtCurrentRevision() = runTest {
        val case = reviewCase()
        val cases = RecordingCases(case)
        val service = reviewService(
            cases,
            RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "unused")),
            FakeClock(1_234),
        )

        assertEquals(
            ReconciliationReviewResult.KeptSeparate,
            service.resolve(command(case, ReconciliationReviewAction.KEEP_SEPARATE)),
        )
        val resolved = requireNotNull(cases.current)
        assertEquals(ReconciliationCaseStatus.RESOLVED_SEPARATE, resolved.status)
        assertEquals(ReconciliationResolutionOrigin.USER, resolved.resolutionOrigin)
        assertEquals(case.evidenceFingerprint, resolved.evidenceFingerprint)
        assertEquals(case.policyVersion, resolved.policyVersion)
        assertEquals(1_234, cases.lastResolvedAt)
    }

    @Test
    fun deferKeepsCasePendingAndVisibleToQueueWithCallerOwnedSuppressionDeadline() = runTest {
        val case = reviewCase()
        val cases = RecordingCases(case)
        val service = reviewService(
            cases,
            RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "unused")),
            FakeClock(1_000),
        )
        val until = 86_400_000L

        assertEquals(
            ReconciliationReviewResult.Deferred(until),
            service.resolve(
                command(
                    case,
                    ReconciliationReviewAction.DEFER,
                    suppressUntilEpochMillis = until,
                ),
            ),
        )
        val pending = cases.observePending().first().single()
        assertEquals(ReconciliationCaseStatus.PENDING, pending.status)
        assertEquals(until, pending.contextualPromptSuppressedUntilEpochMillis)
    }


    @Test
    fun deferNeverShortensExistingSuppressionAndAvoidsWrite() = runTest {
        val case = reviewCase(contextualPromptSuppressedUntilEpochMillis = 90_000L)
        val cases = RecordingCases(case)
        val service = reviewService(
            cases,
            RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "unused")),
            FakeClock(1_000),
        )

        assertEquals(
            ReconciliationReviewResult.Deferred(90_000L),
            service.resolve(
                command(
                    case,
                    ReconciliationReviewAction.DEFER,
                    suppressUntilEpochMillis = 80_000L,
                ),
            ),
        )
        assertEquals(0, cases.mutationCount)
        assertEquals(90_000L, requireNotNull(cases.current).contextualPromptSuppressedUntilEpochMillis)
    }

    @Test
    fun deferDeadlineMustBeStrictlyInFuture() = runTest {
        val case = reviewCase()
        val cases = RecordingCases(case)
        val service = reviewService(
            cases,
            RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "unused")),
            FakeClock(1_000),
        )

        assertEquals(
            ReconciliationReviewResult.StaleCase,
            service.resolve(
                command(
                    case,
                    ReconciliationReviewAction.DEFER,
                    suppressUntilEpochMillis = 1_000L,
                ),
            ),
        )
        assertEquals(0, cases.mutationCount)
    }

    @Test
    fun repeatedCompletedKeepSeparateIsIdempotentForOriginalCommandRevision() = runTest {
        val case = reviewCase()
        val cases = RecordingCases(case)
        val service = reviewService(
            cases,
            RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "unused")),
            FakeClock(1_234),
        )
        val command = command(case, ReconciliationReviewAction.KEEP_SEPARATE)

        assertEquals(ReconciliationReviewResult.KeptSeparate, service.resolve(command))
        assertEquals(ReconciliationReviewResult.KeptSeparate, service.resolve(command))
        assertEquals(1, cases.mutationCount)
    }

    @Test
    fun staleRevisionHasNoSideEffects() = runTest {
        val case = reviewCase(revision = 3)
        val cases = RecordingCases(case)
        val executor = RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "unused"))
        val service = reviewService(cases, executor, FakeClock(1_000))

        val result = service.resolve(
            ReconciliationReviewCommand(
                caseId = case.id,
                expectedCaseRevision = 2,
                action = ReconciliationReviewAction.MERGE,
            ),
        )

        assertEquals(ReconciliationReviewResult.StaleCase, result)
        assertTrue(executor.requests.isEmpty())
        assertEquals(0, cases.mutationCount)
    }

    @Test
    fun staleMergePlanMapsToStaleCaseWithoutRepositoryMutation() = runTest {
        val case = reviewCase()
        val cases = RecordingCases(case)
        val service = reviewService(
            cases,
            RecordingMergeExecutor(StoryMergeResult.StalePlan(setOf(case.key.left, case.key.right))),
            FakeClock(1_000),
        )

        assertEquals(
            ReconciliationReviewResult.StaleCase,
            service.resolve(command(case, ReconciliationReviewAction.MERGE)),
        )
        assertEquals(0, cases.mutationCount)
    }

    @Test
    fun repeatedCompletedMergeUsesExecutorIdempotencyAndReturnsSurvivor() = runTest {
        val case = reviewCase(status = ReconciliationCaseStatus.RESOLVED_MERGED)
        val executor = RecordingMergeExecutor(StoryMergeResult.AlreadyMerged(StoryId("story:a")))
        val service = reviewService(RecordingCases(case), executor, FakeClock(1_000))

        assertEquals(
            ReconciliationReviewResult.Merged(StoryId("story:a")),
            service.resolve(command(case, ReconciliationReviewAction.MERGE)),
        )
        assertEquals(1, executor.requests.size)
    }

    private fun command(
        case: ReconciliationCase,
        action: ReconciliationReviewAction,
        protectedMappingResolutions: List<ProtectedMappingResolution> = emptyList(),
        suppressUntilEpochMillis: Long? = null,
    ) = ReconciliationReviewCommand(
        caseId = case.id,
        expectedCaseRevision = case.revision,
        action = action,
        protectedMappingResolutions = protectedMappingResolutions,
        suppressUntilEpochMillis = suppressUntilEpochMillis,
    )

    private fun reviewService(
        cases: ReconciliationCaseRepository,
        executor: StoryMergeExecutor,
        clock: Clock,
        engine: RecordingReviewEngine = RecordingReviewEngine(),
    ): ReconciliationReviewService = ReconciliationReviewService(cases, executor, clock, engine)

    private class RecordingReviewEngine(
        private val failOnMerge: Boolean = false,
    ) : CanonicalEngineEventSink {
        val merged = mutableListOf<StoryId>()

        override suspend fun onEvidenceChanged(change: CatalogEvidenceChange) = Unit
        override suspend fun onSourceLinked(storyId: StoryId, sourceKey: SourceKey) = Unit
        override suspend fun onSourceUnlinked(storyId: StoryId, sourceKey: SourceKey) = Unit
        override suspend fun onSourcePreferenceChanged(storyId: StoryId): CanonicalFusionResult =
            CanonicalFusionResult.Preparing(storyId)

        override suspend fun onStoryMerged(storyId: StoryId): CanonicalFusionResult {
            merged += storyId
            if (failOnMerge) error("post-merge orchestration failed")
            return CanonicalFusionResult.Preparing(storyId)
        }
    }

    private fun reviewCase(
        status: ReconciliationCaseStatus = ReconciliationCaseStatus.PENDING,
        mergeEligibility: ReconciliationMergeEligibility = ReconciliationMergeEligibility.MERGEABLE,
        revision: Long = 1,
        contextualPromptSuppressedUntilEpochMillis: Long? = null,
    ) = ReconciliationCase(
        id = "case:story:a:story:b",
        key = ReconciliationCaseKey.of(StoryId("story:a"), StoryId("story:b")),
        status = status,
        assessment = ReconciliationAssessment(
            policyVersion = 7,
            semanticDecision = ReconciliationSemanticDecision.REVIEW,
            mergeEligibility = mergeEligibility,
            confidence = 0.82,
            titleSimilarity = 0.9,
            authorSimilarity = 0.7,
            winningLead = 0.2,
            matchedIdentifiers = emptySet(),
            conflictingIdentifiers = emptySet(),
            reasons = setOf(ReconciliationReasonCode.TITLE_SIMILAR),
            identityEvidenceFingerprint = "fingerprint:review",
        ),
        evidenceFingerprint = "fingerprint:review",
        policyVersion = 7,
        resolutionOrigin = null,
        contextualPromptSuppressedUntilEpochMillis = contextualPromptSuppressedUntilEpochMillis,
        revision = revision,
        createdAtEpochMillis = 100,
        lastEvaluatedAtEpochMillis = 200,
    )

    private class RecordingCases(initial: ReconciliationCase?) : ReconciliationCaseRepository {
        private val state = MutableStateFlow(initial)
        var mutationCount = 0
        var lastResolvedAt: Long? = null
        val current: ReconciliationCase? get() = state.value

        override fun observePending(): Flow<List<ReconciliationCase>> = MutableStateFlow(
            listOfNotNull(state.value).filter { it.status == ReconciliationCaseStatus.PENDING },
        )

        override fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>> = MutableStateFlow(
            listOfNotNull(state.value).filter { it.key.left == storyId || it.key.right == storyId },
        )

        override suspend fun find(caseId: String): ReconciliationCase? = state.value?.takeIf { it.id == caseId }

        override suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase? =
            state.value?.takeIf { it.key == key && it.status != ReconciliationCaseStatus.SUPERSEDED }

        override suspend fun recordAssessment(
            key: ReconciliationCaseKey,
            assessment: ReconciliationAssessment,
            evaluatedAtEpochMillis: Long,
        ): ReconciliationCase? = error("unused")

        override suspend fun resolveSeparate(
            caseId: String,
            expectedRevision: Long,
            origin: ReconciliationResolutionOrigin,
            resolvedAtEpochMillis: Long,
        ): Boolean {
            val current = state.value ?: return false
            if (current.id != caseId ||
                current.revision != expectedRevision ||
                current.status != ReconciliationCaseStatus.PENDING
            ) {
                return false
            }
            mutationCount += 1
            lastResolvedAt = resolvedAtEpochMillis
            state.value = current.copy(
                status = ReconciliationCaseStatus.RESOLVED_SEPARATE,
                resolutionOrigin = origin,
                revision = current.revision + 1,
                lastEvaluatedAtEpochMillis = resolvedAtEpochMillis,
            )
            return true
        }

        override suspend fun defer(caseId: String, expectedRevision: Long, suppressUntilEpochMillis: Long): Boolean {
            val current = state.value ?: return false
            if (current.id != caseId ||
                current.revision != expectedRevision ||
                current.status != ReconciliationCaseStatus.PENDING
            ) {
                return false
            }
            mutationCount += 1
            state.value = current.copy(
                contextualPromptSuppressedUntilEpochMillis = maxOf(
                    current.contextualPromptSuppressedUntilEpochMillis ?: 0L,
                    suppressUntilEpochMillis,
                ),
            )
            return true
        }
    }

    private class RecordingMergeExecutor(private val result: StoryMergeResult) : StoryMergeExecutor {
        val requests = mutableListOf<StoryMergeRequest>()

        override suspend fun execute(request: StoryMergeRequest): StoryMergeResult {
            requests += request
            return result
        }
    }
}
