package app.openstory.catalog.orchestration

import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.reconciliation.CatalogReconciliationMaintenance
import app.openstory.catalog.reconciliation.RECONCILIATION_POLICY_VERSION
import app.openstory.catalog.reconciliation.ReconciliationMaintenanceCase
import app.openstory.catalog.reconciliation.ReconciliationRunResult
import app.openstory.common.FakeClock
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CanonicalEngineMaintenanceServiceTest {
    private val story = StoryId("story:maintenance")
    private val clock = FakeClock(1_000_000L)

    @Test
    fun fusionWorkRebuildsAndCompletes() = runTest {
        val fixture = fixture(
            ready = listOf(work(CanonicalEngineWorkType.FUSION_REBUILD)),
        )

        val report = fixture.service.drainReady(limit = 8)

        assertEquals(listOf(story to CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED), fixture.fusion.calls)
        assertEquals(listOf(CanonicalEngineWorkType.FUSION_REBUILD), fixture.work.completed.map { it.type })
        assertEquals(1, report.succeeded)
        assertEquals(0, report.retried)
    }

    @Test
    fun reconciliationWorkReevaluatesWholeResolvedStoryAndCompletesReview() = runTest {
        val fixture = fixture(
            ready = listOf(work(CanonicalEngineWorkType.RECONCILIATION_REEVALUATION)),
        )
        fixture.reconciliation.results = listOf(ReconciliationRunResult.ReviewRecorded("case:1"))

        val report = fixture.service.drainReady(limit = 8)

        assertEquals(listOf(story), fixture.reconciliation.reevaluated)
        assertEquals(1, fixture.work.completed.size)
        assertEquals(0, report.retried)
    }

    @Test
    fun reconciliationRunsBeforeFusionForSameStoryEvenIfQueueReturnsFusionFirst() = runTest {
        val order = mutableListOf<String>()
        val fixture = fixture(
            ready = listOf(
                work(CanonicalEngineWorkType.FUSION_REBUILD),
                work(CanonicalEngineWorkType.RECONCILIATION_REEVALUATION),
            ),
            onReconcile = { order += "reconciliation" },
            onFusion = { order += "fusion" },
        )

        fixture.service.drainReady(limit = 8)

        assertEquals(listOf("reconciliation", "fusion"), order)
    }

    @Test
    fun postMergeDerivedDispatchCompletesOnlyAfterDispatcherAcceptsFullRepair() = runTest {
        val fixture = fixture(
            ready = listOf(
                work(
                    CanonicalEngineWorkType.POST_MERGE_DERIVED,
                    reason = "story-merge-derived-state",
                ),
            ),
        )

        fixture.service.drainReady(limit = 8)

        assertEquals(
            listOf(
                story to PostMergeDerivedRequirements(
                    reaggregateChapters = true,
                    recomputeMappings = true,
                    refreshChapterSync = true,
                ),
            ),
            fixture.derived.calls,
        )
        assertEquals(listOf(CanonicalEngineWorkType.POST_MERGE_DERIVED), fixture.work.completed.map { it.type })
    }

    @Test
    fun transientFusionFailurePersistsExponentialBackoffAndDoesNotComplete() = runTest {
        val fixture = fixture(
            ready = listOf(work(CanonicalEngineWorkType.FUSION_REBUILD, attemptCount = 0)),
            fusionResult = CanonicalFusionResult.Failed(story, "canonical.promotion.race", retryable = true),
        )

        val report = fixture.service.drainReady(limit = 8)

        assertEquals(1, report.retried)
        assertTrue(fixture.work.completed.isEmpty())
        assertEquals(
            RetryRecord(
                item = work(CanonicalEngineWorkType.FUSION_REBUILD, attemptCount = 0),
                failureCode = "canonical.promotion.race",
                nextAttemptAtEpochMillis = clock.nowEpochMillis() + 5 * 60 * 1000L,
            ),
            fixture.work.retries.single(),
        )
    }

    @Test
    fun retryBackoffUsesFiveTenTwentyMinutesAndCapsAtSixHours() = runTest {
        val attempts = listOf(0, 1, 2, 40)
        val expectedDelays = listOf(
            5 * 60 * 1000L,
            10 * 60 * 1000L,
            20 * 60 * 1000L,
            6 * 60 * 60 * 1000L,
        )

        attempts.zip(expectedDelays).forEach { (attemptCount, expectedDelay) ->
            val fixture = fixture(
                ready = listOf(work(CanonicalEngineWorkType.FUSION_REBUILD, attemptCount = attemptCount)),
                fusionResult = CanonicalFusionResult.Failed(story, "temporary", retryable = true),
            )

            fixture.service.drainReady(limit = 1)

            assertEquals(clock.nowEpochMillis() + expectedDelay, fixture.work.retries.single().nextAttemptAtEpochMillis)
        }
    }

    @Test
    fun nonRetryableFusionFailureIsBlockedAsInvariantAndMarksStoryDegraded() = runTest {
        val fixture = fixture(
            ready = listOf(work(CanonicalEngineWorkType.FUSION_REBUILD)),
            fusionResult = CanonicalFusionResult.Failed(story, "canonical.story.missing", retryable = false),
        )

        val report = fixture.service.drainReady(limit = 8)

        assertEquals(listOf(story), fixture.health.degraded)
        assertEquals(listOf("canonical.story.missing"), fixture.work.blocked.map { it.failureCode })
        assertEquals(1, report.failedInvariant)
        assertTrue(fixture.work.completed.isEmpty())
    }

    @Test
    fun missingCanonicalOwnerParksDirtyWorkBeforeAnyEngineMutation() = runTest {
        val missing = StoryId("story:missing-owner")
        val fixture = fixture(
            ready = listOf(
                CanonicalEngineWorkItem(
                    storyId = missing,
                    type = CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                    reason = CanonicalEngineWorkReasons.RETRY,
                    requiredPolicyVersion = RECONCILIATION_POLICY_VERSION,
                    attemptCount = 0,
                    nextAttemptAtEpochMillis = 0L,
                    lastFailureCode = null,
                ),
            ),
            identity = FakeIdentity(missingStoryIds = setOf(missing)),
        )

        val report = fixture.service.drainReady(limit = 8)

        assertTrue(fixture.reconciliation.reevaluated.isEmpty())
        assertEquals(1, fixture.work.blocked.size)
        assertEquals("canonical.maintenance.missing_story_owner", fixture.work.blocked.single().failureCode)
        assertEquals(listOf(missing), fixture.health.degraded)
        assertEquals(1, report.failedInvariant)
    }

    @Test
    fun futureRequiredPolicyVersionParksWorkBeforeOlderBinaryMutatesState() = runTest {
        val fixture = fixture(
            ready = listOf(
                work(
                    CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                    requiredPolicyVersion = RECONCILIATION_POLICY_VERSION + 1,
                ),
                work(
                    CanonicalEngineWorkType.FUSION_REBUILD,
                    requiredPolicyVersion = app.openstory.catalog.fusion.FUSION_POLICY_VERSION + 1,
                ),
            ),
        )

        val report = fixture.service.drainReady(limit = 8)

        assertTrue(fixture.reconciliation.reevaluated.isEmpty())
        assertTrue(fixture.fusion.calls.isEmpty())
        assertEquals(2, fixture.work.blocked.size)
        assertTrue(
            fixture.work.blocked.all {
                it.failureCode == "canonical.maintenance.unsupported_required_policy_version"
            },
        )
        assertEquals(2, report.failedInvariant)
        assertEquals(listOf(story, story), fixture.health.degraded)
    }

    @Test
    fun stalePolicyCandidateWithMissingPolicyStateIsReportedAsInvariantInsteadOfSilentlySkipped() = runTest {
        val fixture = fixture()
        fixture.reader.stalePolicyStories = listOf(story)

        val report = fixture.service.runConsistencySafetyPass(limit = 8)

        assertTrue(fixture.work.marks.isEmpty())
        assertEquals(1, report.failedInvariant)
    }

    @Test
    fun futurePersistedPolicyStateFailsClosedInsteadOfBeingReevaluatedByOlderBinary() = runTest {
        val fixture = fixture()
        fixture.reader.stalePolicyStories = listOf(story)
        fixture.reader.policyByStory[story] = CanonicalMaintenancePolicyState(
            fusionPolicyVersion = app.openstory.catalog.fusion.FUSION_POLICY_VERSION + 1,
            primarySelectionPolicyVersion = app.openstory.catalog.fusion.PRIMARY_SELECTION_POLICY_VERSION,
            reconciliationPolicyVersions = setOf(RECONCILIATION_POLICY_VERSION),
        )

        val report = fixture.service.runConsistencySafetyPass(limit = 8)

        assertEquals(
            listOf(
                WorkMarkRecord(
                    story,
                    CanonicalEngineWorkType.FUSION_REBUILD,
                    CanonicalEngineWorkReasons.POLICY_VERSION_CHANGED,
                    requiredPolicyVersion = null,
                ),
            ),
            fixture.work.marks,
        )
        assertEquals(
            listOf(CanonicalMaintenanceFailureCodes.UNSUPPORTED_PERSISTED_POLICY_VERSION),
            fixture.work.blocked.map(BlockRecord::failureCode),
        )
        assertTrue(fixture.reconciliation.reevaluated.isEmpty())
        assertTrue(fixture.fusion.calls.isEmpty())
        assertEquals(listOf(story), fixture.health.degraded)
        assertEquals(1, report.failedInvariant)
    }

    @Test
    fun safetyPassRequeuesPersistedPolicyBlockAfterUpgradeSoFusionCanRepairHealth() = runTest {
        val fixture = fixture()
        fixture.work.parked += work(
            CanonicalEngineWorkType.FUSION_REBUILD,
            reason = CanonicalEngineWorkReasons.POLICY_VERSION_CHANGED,
            requiredPolicyVersion = null,
            nextAttemptAtEpochMillis = Long.MAX_VALUE,
            lastFailureCode = CanonicalMaintenanceFailureCodes.UNSUPPORTED_PERSISTED_POLICY_VERSION,
        )
        fixture.reader.policyByStory[story] = currentPolicyState()

        val report = fixture.service.runConsistencySafetyPass(limit = 8)

        assertEquals(1, fixture.work.requeued.size)
        assertEquals(listOf(story to CanonicalFusionReason.POLICY_REEVALUATION), fixture.fusion.calls)
        assertEquals(1, report.succeeded)
    }

    @Test
    fun safetyPassRequeuesPreviouslyUnsupportedPolicyWorkAfterBinaryCatchesUp() = runTest {
        val fixture = fixture()
        fixture.work.parked += work(
            CanonicalEngineWorkType.FUSION_REBUILD,
            requiredPolicyVersion = app.openstory.catalog.fusion.FUSION_POLICY_VERSION,
            nextAttemptAtEpochMillis = Long.MAX_VALUE,
            lastFailureCode = CanonicalMaintenanceFailureCodes.UNSUPPORTED_REQUIRED_POLICY_VERSION,
        )

        val report = fixture.service.runConsistencySafetyPass(limit = 8)

        assertEquals(1, fixture.work.requeued.size)
        assertEquals(listOf(story to CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED), fixture.fusion.calls)
        assertEquals(1, report.succeeded)
    }

    @Test
    fun policyReevaluationRunsOnlyStalePolicyFamilies() = runTest {
        val fixture = fixture(
            ready = listOf(work(CanonicalEngineWorkType.POLICY_REEVALUATION)),
        )
        fixture.reader.policyByStory[story] = CanonicalMaintenancePolicyState(
            fusionPolicyVersion = app.openstory.catalog.fusion.FUSION_POLICY_VERSION,
            primarySelectionPolicyVersion = app.openstory.catalog.fusion.PRIMARY_SELECTION_POLICY_VERSION - 1,
            reconciliationPolicyVersions = setOf(RECONCILIATION_POLICY_VERSION),
        )

        fixture.service.drainReady(limit = 8)

        assertTrue(fixture.reconciliation.reevaluated.isEmpty())
        assertEquals(listOf(story to CanonicalFusionReason.POLICY_REEVALUATION), fixture.fusion.calls)
        assertEquals(1, fixture.work.completed.size)
    }

    @Test
    fun reconciliationPolicyChangeReevaluatesIdentityButFusionOnlyChangeDoesNot() = runTest {
        val identityFixture = fixture(
            ready = listOf(work(CanonicalEngineWorkType.POLICY_REEVALUATION)),
        )
        identityFixture.reader.policyByStory[story] = CanonicalMaintenancePolicyState(
            fusionPolicyVersion = app.openstory.catalog.fusion.FUSION_POLICY_VERSION,
            primarySelectionPolicyVersion = app.openstory.catalog.fusion.PRIMARY_SELECTION_POLICY_VERSION,
            reconciliationPolicyVersions = setOf(RECONCILIATION_POLICY_VERSION - 1),
        )

        identityFixture.service.drainReady(limit = 8)
        assertEquals(listOf(story), identityFixture.reconciliation.reevaluated)
        assertTrue(identityFixture.fusion.calls.isEmpty())

        val fusionFixture = fixture(
            ready = listOf(work(CanonicalEngineWorkType.POLICY_REEVALUATION)),
        )
        fusionFixture.reader.policyByStory[story] = CanonicalMaintenancePolicyState(
            fusionPolicyVersion = app.openstory.catalog.fusion.FUSION_POLICY_VERSION - 1,
            primarySelectionPolicyVersion = app.openstory.catalog.fusion.PRIMARY_SELECTION_POLICY_VERSION,
            reconciliationPolicyVersions = setOf(RECONCILIATION_POLICY_VERSION),
        )

        fusionFixture.service.drainReady(limit = 8)
        assertTrue(fusionFixture.reconciliation.reevaluated.isEmpty())
        assertEquals(listOf(story to CanonicalFusionReason.POLICY_REEVALUATION), fusionFixture.fusion.calls)
    }

    @Test
    fun policyBacklogMarksOnlyBoundedStaleStoriesWithSpecificWorkTypes() = runTest {
        val fixture = fixture()
        val fusionOnly = StoryId("story:fusion")
        val reconciliationOnly = StoryId("story:reconciliation")
        val both = StoryId("story:both")
        val ignoredByLimit = StoryId("story:outside-limit")
        fixture.reader.stalePolicyStories = listOf(fusionOnly, reconciliationOnly, both, ignoredByLimit)
        fixture.reader.policyByStory[fusionOnly] = currentPolicyState().copy(
            fusionPolicyVersion = app.openstory.catalog.fusion.FUSION_POLICY_VERSION - 1,
        )
        fixture.reader.policyByStory[reconciliationOnly] = currentPolicyState().copy(
            reconciliationPolicyVersions = setOf(RECONCILIATION_POLICY_VERSION - 1),
        )
        fixture.reader.policyByStory[both] = CanonicalMaintenancePolicyState(
            fusionPolicyVersion = null,
            primarySelectionPolicyVersion = null,
            reconciliationPolicyVersions = setOf(RECONCILIATION_POLICY_VERSION - 1),
        )

        val marked = fixture.service.enqueuePolicyReevaluationIfNeeded(limit = 3)

        assertEquals(3, marked)
        assertEquals(
            listOf(
                WorkMarkRecord(
                    fusionOnly,
                    CanonicalEngineWorkType.FUSION_REBUILD,
                    CanonicalEngineWorkReasons.POLICY_VERSION_CHANGED,
                    app.openstory.catalog.fusion.FUSION_POLICY_VERSION,
                ),
                WorkMarkRecord(
                    reconciliationOnly,
                    CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                    CanonicalEngineWorkReasons.POLICY_VERSION_CHANGED,
                    RECONCILIATION_POLICY_VERSION,
                ),
                WorkMarkRecord(
                    both,
                    CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                    CanonicalEngineWorkReasons.POLICY_VERSION_CHANGED,
                    RECONCILIATION_POLICY_VERSION,
                ),
                WorkMarkRecord(
                    both,
                    CanonicalEngineWorkType.FUSION_REBUILD,
                    CanonicalEngineWorkReasons.POLICY_VERSION_CHANGED,
                    app.openstory.catalog.fusion.FUSION_POLICY_VERSION,
                ),
            ),
            fixture.work.marks,
        )
        assertFalse(fixture.work.marks.any { it.storyId == ignoredByLimit })
    }

    @Test
    fun safetyPassTreatsPolicyIdentityInvariantAsSemanticFailureWithoutRetryingWorkerQueue() = runTest {
        val stale = StoryId("story:broken-identity")
        val identity = FakeIdentity(invariantStoryIds = setOf(stale))
        val fixture = fixture(identity = identity)
        fixture.reader.stalePolicyStories = listOf(stale)

        val report = fixture.service.runConsistencySafetyPass(limit = 8)

        assertEquals(1, report.failedInvariant)
        assertTrue(fixture.work.marks.isEmpty())
        assertTrue(fixture.work.retries.isEmpty())
    }

    @Test
    fun scoreOnlyFusionPolicyChangeDoesNotReopenCurrentKeepSeparateCase() = runTest {
        val fixture = fixture()
        fixture.reader.stalePolicyStories = listOf(story)
        fixture.reader.policyByStory[story] = CanonicalMaintenancePolicyState(
            fusionPolicyVersion = app.openstory.catalog.fusion.FUSION_POLICY_VERSION - 1,
            primarySelectionPolicyVersion = app.openstory.catalog.fusion.PRIMARY_SELECTION_POLICY_VERSION,
            reconciliationPolicyVersions = setOf(RECONCILIATION_POLICY_VERSION),
        )

        fixture.service.enqueuePolicyReevaluationIfNeeded(limit = 8)

        assertEquals(listOf(CanonicalEngineWorkType.FUSION_REBUILD), fixture.work.marks.map { it.type })
    }

    @Test
    fun safetyPassOnlyReevaluatesPendingCasesWhoseEvidenceFingerprintChanged() = runTest {
        val changedLeft = StoryId("story:a")
        val changedRight = StoryId("story:b")
        val stableLeft = StoryId("story:c")
        val stableRight = StoryId("story:d")
        val fixture = fixture()
        fixture.reader.pendingCases = listOf(
            ReconciliationMaintenanceCase("case:changed", changedLeft, changedRight, "fingerprint:old", 1),
            ReconciliationMaintenanceCase("case:stable", stableLeft, stableRight, "fingerprint:current", 1),
        )
        fixture.reconciliation.currentFingerprints["case:changed"] = false
        fixture.reconciliation.currentFingerprints["case:stable"] = true

        fixture.service.runConsistencySafetyPass(limit = 16)

        val reconciliationMarks = fixture.work.marks.filter {
            it.type == CanonicalEngineWorkType.RECONCILIATION_REEVALUATION
        }
        assertEquals(setOf(changedLeft, changedRight), reconciliationMarks.mapTo(linkedSetOf()) { it.storyId })
        assertFalse(reconciliationMarks.any { it.storyId == stableLeft || it.storyId == stableRight })
    }

    @Test
    fun safetyPassDoesNotScanUnmarkedStoryUniverse() = runTest {
        val fixture = fixture()
        fixture.reader.totalStoryCountForAssertion = 50_000
        fixture.reader.pendingCases = listOf(
            ReconciliationMaintenanceCase("case:only", StoryId("story:x"), StoryId("story:y"), "old", 1),
        )
        fixture.reconciliation.currentFingerprints["case:only"] = false

        fixture.service.runConsistencySafetyPass(limit = 8)

        assertEquals(1, fixture.reconciliation.fingerprintChecks)
        assertEquals(1, fixture.reader.pendingCaseQueries)
        assertEquals(1, fixture.reader.stalePolicyQueries)
    }

    @Test
    fun redirectInvariantIsReportedAndDegradesReachableCanonicalTargetWithoutRetryLoop() = runTest {
        val canonical = StoryId("story:canonical")
        val fixture = fixture()
        fixture.reader.redirectIssues = listOf(
            CanonicalMaintenanceInvariantIssue(canonical, "canonical.invariant.redirect_not_flattened"),
        )

        val report = fixture.service.runConsistencySafetyPass(limit = 8)

        assertEquals(1, report.failedInvariant)
        assertEquals(listOf(canonical), fixture.health.degraded)
        assertTrue(fixture.work.retries.isEmpty())
    }

    @Test
    fun reportExposesEarliestFutureQueueWakeupButIgnoresInvariantParkedRows() = runTest {
        val next = clock.nowEpochMillis() + 60_000L
        val fixture = fixture()
        fixture.work.nextAttempt = next

        val report = fixture.service.drainReady(limit = 8)

        assertEquals(next, report.nextAttemptAtEpochMillis)
        fixture.work.nextAttempt = null
        assertNull(fixture.service.drainReady(limit = 8).nextAttemptAtEpochMillis)
    }

    private fun currentPolicyState() = CanonicalMaintenancePolicyState(
        fusionPolicyVersion = app.openstory.catalog.fusion.FUSION_POLICY_VERSION,
        primarySelectionPolicyVersion = app.openstory.catalog.fusion.PRIMARY_SELECTION_POLICY_VERSION,
        reconciliationPolicyVersions = setOf(RECONCILIATION_POLICY_VERSION),
    )

    private fun fixture(
        ready: List<CanonicalEngineWorkItem> = emptyList(),
        fusionResult: CanonicalFusionResult = CanonicalFusionResult.Preparing(story),
        onReconcile: () -> Unit = {},
        onFusion: () -> Unit = {},
        identity: StoryIdentityRepository = FakeIdentity(),
    ): Fixture {
        val work = FakeWorkRepository(ready.toMutableList())
        val reconciliation = FakeReconciliation(onReconcile)
        val fusion = FakeFusion(fusionResult, onFusion)
        val reader = FakeMaintenanceReader()
        val derived = FakeDerivedDispatcher()
        val health = FakeHealthMarker()
        val service = CanonicalEngineMaintenanceService(
            work = work,
            reconciliation = reconciliation,
            fusion = fusion,
            identity = identity,
            reader = reader,
            derived = derived,
            health = health,
            clock = clock,
        )
        return Fixture(service, work, reconciliation, fusion, reader, derived, health)
    }

    private fun work(
        type: CanonicalEngineWorkType,
        attemptCount: Int = 0,
        reason: String = CanonicalEngineWorkReasons.SOURCE_SUMMARY_CHANGED,
        requiredPolicyVersion: Int? = null,
        nextAttemptAtEpochMillis: Long = 0L,
        lastFailureCode: String? = null,
    ) = CanonicalEngineWorkItem(
        storyId = story,
        type = type,
        reason = reason,
        requiredPolicyVersion = requiredPolicyVersion,
        attemptCount = attemptCount,
        nextAttemptAtEpochMillis = nextAttemptAtEpochMillis,
        lastFailureCode = lastFailureCode,
    )
}

private data class Fixture(
    val service: CanonicalEngineMaintenanceService,
    val work: FakeWorkRepository,
    val reconciliation: FakeReconciliation,
    val fusion: FakeFusion,
    val reader: FakeMaintenanceReader,
    val derived: FakeDerivedDispatcher,
    val health: FakeHealthMarker,
)

private data class WorkMarkRecord(
    val storyId: StoryId,
    val type: CanonicalEngineWorkType,
    val reason: String,
    val requiredPolicyVersion: Int?,
)

private data class RetryRecord(
    val item: CanonicalEngineWorkItem,
    val failureCode: String,
    val nextAttemptAtEpochMillis: Long,
)

private data class BlockRecord(
    val item: CanonicalEngineWorkItem,
    val failureCode: String,
)

private class FakeWorkRepository(
    val ready: MutableList<CanonicalEngineWorkItem> = mutableListOf(),
) : CanonicalEngineWorkRepository {
    val marks = mutableListOf<WorkMarkRecord>()
    val completed = mutableListOf<CanonicalEngineWorkItem>()
    val retries = mutableListOf<RetryRecord>()
    val blocked = mutableListOf<BlockRecord>()
    val parked = mutableListOf<CanonicalEngineWorkItem>()
    val requeued = mutableListOf<CanonicalEngineWorkItem>()
    var nextAttempt: Long? = null

    override suspend fun markDirty(
        storyId: StoryId,
        type: CanonicalEngineWorkType,
        reason: String,
        requiredPolicyVersion: Int?,
    ): CanonicalEngineWorkItem {
        marks += WorkMarkRecord(storyId, type, reason, requiredPolicyVersion)
        return CanonicalEngineWorkItem(
            storyId = storyId,
            type = type,
            reason = reason,
            requiredPolicyVersion = requiredPolicyVersion,
            attemptCount = 0,
            nextAttemptAtEpochMillis = 0L,
            lastFailureCode = null,
        )
    }

    override suspend fun claimReady(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkItem> =
        ready.take(limit)

    override suspend fun complete(item: CanonicalEngineWorkItem): Boolean {
        completed += item
        return ready.remove(item)
    }

    override suspend fun retry(
        item: CanonicalEngineWorkItem,
        failureCode: String,
        nextAttemptAtEpochMillis: Long,
    ) {
        retries += RetryRecord(item, failureCode, nextAttemptAtEpochMillis)
        ready.remove(item)
    }

    override suspend fun blockInvariant(item: CanonicalEngineWorkItem, failureCode: String) {
        blocked += BlockRecord(item, failureCode)
        ready.remove(item)
    }

    override suspend fun blocked(
        failureCodes: Set<String>,
        limit: Int,
    ): List<CanonicalEngineWorkItem> = parked.filter { it.lastFailureCode in failureCodes }.take(limit)

    override suspend fun requeueBlocked(item: CanonicalEngineWorkItem): CanonicalEngineWorkItem? {
        if (!parked.remove(item)) return null
        val queued = item.copy(
            attemptCount = 0,
            nextAttemptAtEpochMillis = 0L,
            lastFailureCode = null,
        )
        requeued += queued
        ready += queued
        return queued
    }

    override suspend fun nextAttemptAtEpochMillis(): Long? = nextAttempt

    override suspend fun supersede(storyId: StoryId, type: CanonicalEngineWorkType) = Unit
}

private class FakeReconciliation(
    private val onReconcile: () -> Unit,
) : CatalogReconciliationMaintenance {
    val reevaluated = mutableListOf<StoryId>()
    var results: List<ReconciliationRunResult> = listOf(ReconciliationRunResult.NoIdentityChange)
    val currentFingerprints = mutableMapOf<String, Boolean>()
    var fingerprintChecks = 0

    override suspend fun reevaluateStory(storyId: StoryId): List<ReconciliationRunResult> {
        onReconcile()
        reevaluated += storyId
        return results
    }

    override suspend fun isEvidenceFingerprintCurrent(case: ReconciliationMaintenanceCase): Boolean {
        fingerprintChecks += 1
        return currentFingerprints[case.caseId] ?: true
    }
}

private class FakeFusion(
    private val result: CanonicalFusionResult,
    private val onFusion: () -> Unit,
) : CanonicalGenerationRebuilder {
    val calls = mutableListOf<Pair<StoryId, CanonicalFusionReason>>()

    override suspend fun rebuild(storyId: StoryId, reason: CanonicalFusionReason): CanonicalFusionResult {
        onFusion()
        calls += storyId to reason
        return result
    }
}

private class FakeMaintenanceReader : CanonicalEngineMaintenanceReader {
    var stalePolicyStories: List<StoryId> = emptyList()
    val policyByStory = mutableMapOf<StoryId, CanonicalMaintenancePolicyState>()
    var pendingCases: List<ReconciliationMaintenanceCase> = emptyList()
    var redirectIssues: List<CanonicalMaintenanceInvariantIssue> = emptyList()
    var stalePolicyQueries = 0
    var pendingCaseQueries = 0
    var totalStoryCountForAssertion = 0

    override suspend fun stalePolicyStoryIds(
        fusionPolicyVersion: Int,
        primarySelectionPolicyVersion: Int,
        reconciliationPolicyVersion: Int,
        limit: Int,
    ): List<StoryId> {
        stalePolicyQueries += 1
        return stalePolicyStories.take(limit)
    }

    override suspend fun policyState(storyId: StoryId): CanonicalMaintenancePolicyState? = policyByStory[storyId]

    override suspend fun pendingReconciliationCases(limit: Int): List<ReconciliationMaintenanceCase> {
        pendingCaseQueries += 1
        return pendingCases.take(limit)
    }

    override suspend fun redirectInconsistencies(limit: Int): List<CanonicalMaintenanceInvariantIssue> =
        redirectIssues.take(limit)
}

private class FakeDerivedDispatcher : PostMergeDerivedWorkDispatcher {
    val calls = mutableListOf<Pair<StoryId, PostMergeDerivedRequirements>>()
    var result: PostMergeDerivedWorkResult = PostMergeDerivedWorkResult.Dispatched

    override suspend fun dispatch(
        storyId: StoryId,
        requirements: PostMergeDerivedRequirements,
    ): PostMergeDerivedWorkResult {
        calls += storyId to requirements
        return result
    }
}

private class FakeHealthMarker : CanonicalMaintenanceHealthMarker {
    val degraded = mutableListOf<StoryId>()

    override suspend fun markDegraded(storyId: StoryId) {
        degraded += storyId
    }
}

private class FakeIdentity(
    private val invariantStoryIds: Set<StoryId> = emptySet(),
    private val missingStoryIds: Set<StoryId> = emptySet(),
) : StoryIdentityRepository {
    override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(storyId)

    override suspend fun resolve(storyId: StoryId): StoryId {
        if (storyId in invariantStoryIds) {
            throw app.openstory.catalog.identity.StoryIdentityInvariantException("test identity invariant")
        }
        return storyId
    }

    override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? =
        if (storyId in missingStoryIds) {
            null
        } else {
            CanonicalIdentityState(storyId, identityRevision = 0L, createdAtEpochMillis = 0L)
        }
}
