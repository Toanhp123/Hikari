package app.openstory.catalog.reconciliation

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogMatchSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.repository.CatalogSearchSummaryCommitResult
import app.openstory.catalog.repository.CatalogSearchSummaryMutation
import app.openstory.catalog.orchestration.CanonicalEngineWorkItem
import app.openstory.catalog.orchestration.CanonicalEngineWorkRepository
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CatalogReconciliationServiceTest {
    private val clock = Clock { 1_000L }

    @Test
    fun reviewCreatesExactlyOneActiveCaseAndDuplicateFingerprintDoesNotRevise() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Exact")
        val b = record("b", "story:b", "Exact")
        val fixture = fixture(listOf(a, b))

        val first = fixture.service.reconcile(a.key)
        val second = fixture.service.reconcile(a.key)

        assertIs<ReconciliationRunResult.ReviewRecorded>(first)
        assertIs<ReconciliationRunResult.ReviewRecorded>(second)
        assertEquals(1, fixture.cases.revisions)
        assertEquals(1, fixture.cases.active.size)
    }

    @Test
    fun sameWorkIsObservedDurablyWithoutExecutingMerge() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Exact", authors = setOf("writer"))
        val b = record("b", "story:b", "Exact", authors = setOf("writer"))
        val fixture = fixture(listOf(a, b))

        val result = fixture.service.reconcile(a.key)

        assertEquals(ReconciliationRunResult.AutoMergeObserved(StoryId("story:a"), StoryId("story:b")), result)
        assertEquals(1, fixture.cases.revisions)
        assertEquals(ReconciliationSemanticDecision.SAME_WORK, fixture.cases.active.values.single().assessment.semanticDecision)
    }

    @Test
    fun engineConfirmedDifferentWorkIsDurablySeparated() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Same", contentType = ContentType.MANGA)
        val b = record("b", "story:b", "Same", contentType = ContentType.ANIME)
        val fixture = fixture(listOf(a, b))

        assertEquals(ReconciliationRunResult.Separated, fixture.service.reconcile(a.key))
        val stored = fixture.cases.active.values.single()
        assertEquals(ReconciliationCaseStatus.RESOLVED_SEPARATE, stored.status)
        assertEquals(ReconciliationResolutionOrigin.ENGINE, stored.resolutionOrigin)
    }

    @Test
    fun noMatchCreatesNoDurableCase() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(
            listOf(
                record("a", "story:a", "Alpha"),
                record("b", "story:b", "Completely Different"),
            ),
        )

        assertEquals(ReconciliationRunResult.NoIdentityChange, fixture.service.reconcile(SourceKey(PluginId("p"), "a")))
        assertEquals(0, fixture.cases.revisions)
        assertEquals(0, fixture.cases.active.size)
    }

    @Test
    fun keepSeparateWithSameFingerprintDoesNotReopenOnRefresh() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Exact")
        val b = record("b", "story:b", "Exact")
        val fixture = fixture(listOf(a, b))
        val first = assertIs<ReconciliationRunResult.ReviewRecorded>(fixture.service.reconcile(a.key))
        val current = fixture.cases.active.values.single()
        assertEquals(true, fixture.cases.resolveSeparate(first.caseId, current.revision, ReconciliationResolutionOrigin.USER, 2_000L))

        val refreshed = fixture.service.reconcile(a.key)

        assertEquals(ReconciliationRunResult.Separated, refreshed)
        assertEquals(2, fixture.cases.revisions)
        assertEquals(ReconciliationCaseStatus.RESOLVED_SEPARATE, fixture.cases.active.values.single().status)
    }

    @Test
    fun policyVersionChangeCreatesNewAssessmentRevision() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Exact")
        val b = record("b", "story:b", "Exact")
        val catalog = FakeCatalogRepository(listOf(a, b))
        val cases = RecordingCaseRepository()
        service(catalog, cases, ReconciliationPolicy(version = 1)).reconcile(a.key)

        service(catalog, cases, ReconciliationPolicy(version = 2)).reconcile(a.key)

        assertEquals(2, cases.revisions)
        assertEquals(2, cases.active.values.single().policyVersion)
    }

    @Test
    fun multipleSourcesForOneCandidateStoryDoNotArtificiallyReduceWinningLead() = kotlinx.coroutines.test.runTest {
        val incoming = record("incoming", "story:incoming", "Exact", authors = setOf("writer"))
        val candidateA = record("a1", "story:a", "Exact", authors = setOf("writer"))
        val candidateASecond = record("a2", "story:a", "Exact", authors = setOf("writer"))
        val fixture = fixture(listOf(incoming, candidateA, candidateASecond))

        val result = fixture.service.reconcile(incoming.key)

        assertEquals(
            ReconciliationRunResult.AutoMergeObserved(StoryId("story:a"), StoryId("story:incoming")),
            result,
        )
    }

    @Test
    fun candidateIndexBuildsOnceThenUpsertsChangedEvidence() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Exact")
        val b = record("b", "story:b", "Exact")
        val fixture = fixture(listOf(a, b))

        fixture.service.reconcile(a.key)
        fixture.catalog.put(record("a", "story:a", "Exact", authors = setOf("writer")))
        fixture.service.reconcile(a.key)

        assertEquals(1, fixture.catalog.globalReads)
    }

    @Test
    fun invalidationCausesOneLazyGlobalRebuild() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Exact")
        val b = record("b", "story:b", "Exact")
        val fixture = fixture(listOf(a, b))

        fixture.service.reconcile(a.key)
        fixture.service.invalidateCandidateIndex()
        fixture.service.reconcile(a.key)
        fixture.service.reconcile(a.key)

        assertEquals(2, fixture.catalog.globalReads)
    }

    @Test
    fun applyModeExecutesOnlyEligibleSameWorkAndReturnsApplied() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Exact", authors = setOf("writer"))
        val b = record("b", "story:b", "Exact", authors = setOf("writer"))
        val mergeExecutor = RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "merge:1"))
        val work = RecordingWorkRepository()
        val fixture = fixture(
            records = listOf(a, b),
            executionMode = ReconciliationExecutionMode.APPLY_ELIGIBLE_AUTO_MERGES,
            mergeExecutor = mergeExecutor,
            work = work,
        )

        val result = fixture.service.reconcile(a.key)

        assertEquals(ReconciliationRunResult.AutoMergeApplied(StoryId("story:a")), result)
        assertEquals(1, mergeExecutor.requests.size)
        val request = mergeExecutor.requests.single()
        assertEquals(StoryId("story:a"), request.leftStoryId)
        assertEquals(StoryId("story:b"), request.rightStoryId)
        assertEquals(fixture.cases.active.values.single().id, request.reconciliationCaseId)
        assertEquals(emptyList(), work.dirty)
    }

    @Test
    fun observeModeNeverCallsMergeExecutor() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Exact", authors = setOf("writer"))
        val b = record("b", "story:b", "Exact", authors = setOf("writer"))
        val mergeExecutor = RecordingMergeExecutor(StoryMergeResult.Merged(StoryId("story:a"), "merge:1"))
        val fixture = fixture(listOf(a, b), mergeExecutor = mergeExecutor)

        assertIs<ReconciliationRunResult.AutoMergeObserved>(fixture.service.reconcile(a.key))
        assertEquals(0, mergeExecutor.requests.size)
    }

    @Test
    fun mergeReviewRequiredKeepsCasePendingWithoutRetryWork() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Exact", authors = setOf("writer"))
        val b = record("b", "story:b", "Exact", authors = setOf("writer"))
        val mergeExecutor = RecordingMergeExecutor(StoryMergeResult.ReviewRequired(setOf("protected-mapping-conflict")))
        val work = RecordingWorkRepository()
        val fixture = fixture(
            records = listOf(a, b),
            executionMode = ReconciliationExecutionMode.APPLY_ELIGIBLE_AUTO_MERGES,
            mergeExecutor = mergeExecutor,
            work = work,
        )

        val result = fixture.service.reconcile(a.key)

        assertIs<ReconciliationRunResult.ReviewRecorded>(result)
        assertEquals(ReconciliationCaseStatus.PENDING, fixture.cases.active.values.single().status)
        assertEquals(emptyList(), work.dirty)
    }

    @Test
    fun staleMergePlanDurablySchedulesReconciliationReevaluation() = kotlinx.coroutines.test.runTest {
        val a = record("a", "story:a", "Exact", authors = setOf("writer"))
        val b = record("b", "story:b", "Exact", authors = setOf("writer"))
        val current = setOf(StoryId("story:a"), StoryId("story:c"))
        val mergeExecutor = RecordingMergeExecutor(StoryMergeResult.StalePlan(current))
        val work = RecordingWorkRepository()
        val fixture = fixture(
            records = listOf(a, b),
            executionMode = ReconciliationExecutionMode.APPLY_ELIGIBLE_AUTO_MERGES,
            mergeExecutor = mergeExecutor,
            work = work,
        )

        val result = fixture.service.reconcile(a.key)

        assertEquals(ReconciliationRunResult.ReevaluationScheduled(current), result)
        assertEquals(
            current,
            work.dirty.map { it.storyId }.toSet(),
        )
        assertEquals(
            setOf(CanonicalEngineWorkType.RECONCILIATION_REEVALUATION),
            work.dirty.map { it.type }.toSet(),
        )
    }

    @Test
    fun missingSourceRecordIsNoIdentityChange() = kotlinx.coroutines.test.runTest {
        val fixture = fixture(emptyList())
        assertEquals(
            ReconciliationRunResult.NoIdentityChange,
            fixture.service.reconcile(SourceKey(PluginId("p"), "missing")),
        )
        assertNull(fixture.cases.active.values.firstOrNull())
    }

    private fun fixture(
        records: List<CatalogSourceRecord>,
        executionMode: ReconciliationExecutionMode = ReconciliationExecutionMode.OBSERVE_ONLY,
        mergeExecutor: StoryMergeExecutor? = null,
        work: CanonicalEngineWorkRepository? = null,
    ): Fixture {
        val catalog = FakeCatalogRepository(records)
        val cases = RecordingCaseRepository()
        return Fixture(
            service(catalog, cases, ReconciliationPolicy(), executionMode, mergeExecutor, work),
            catalog,
            cases,
        )
    }

    private fun service(
        catalog: CatalogRepository,
        cases: ReconciliationCaseRepository,
        policy: ReconciliationPolicy,
        executionMode: ReconciliationExecutionMode = ReconciliationExecutionMode.OBSERVE_ONLY,
        mergeExecutor: StoryMergeExecutor? = null,
        work: CanonicalEngineWorkRepository? = null,
    ) = CatalogReconciliationService(
        catalog = catalog,
        identity = IdentityRepository(),
        candidateIndex = InMemoryCatalogCandidateIndex(),
        engine = CatalogReconciliationEngine(policy),
        cases = cases,
        clock = clock,
        executionMode = executionMode,
        mergeExecutor = mergeExecutor,
        work = work,
    )

    private fun record(
        source: String,
        story: String,
        title: String,
        contentType: ContentType = ContentType.MANGA,
        authors: Set<String> = emptySet(),
    ): CatalogSourceRecord {
        val key = SourceKey(PluginId("p"), source)
        val entry = CatalogEntry(
            storyId = StoryId(story),
            pluginId = key.pluginId,
            sourceId = key.sourceId,
            title = title,
            authors = authors,
            contentType = contentType,
        )
        return CatalogSourceRecord(
            key = key,
            storyId = entry.storyId,
            entry = entry,
            summary = CatalogMetadataStamp("1", 1),
            full = null,
            identityFingerprint = "identity:$source:$story:$title:${authors.sorted()}:$contentType",
            fusionFingerprint = "fusion:$source",
        )
    }

    private data class Fixture(
        val service: CatalogReconciliationService,
        val catalog: FakeCatalogRepository,
        val cases: RecordingCaseRepository,
    )

    private class IdentityRepository : StoryIdentityRepository {
        override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(storyId)
        override suspend fun resolve(storyId: StoryId): StoryId = storyId
        override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? = null
    }

    private class RecordingCaseRepository : ReconciliationCaseRepository {
        val active = linkedMapOf<ReconciliationCaseKey, ReconciliationCase>()
        var revisions = 0

        override fun observePending(): Flow<List<ReconciliationCase>> = flowOf(active.values.filter { it.status == ReconciliationCaseStatus.PENDING })
        override fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>> = flowOf(
            active.values.filter { it.key.left == storyId || it.key.right == storyId },
        )
        override suspend fun find(caseId: String): ReconciliationCase? = active.values.firstOrNull { it.id == caseId }
        override suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase? = active[key]

        override suspend fun recordAssessment(
            key: ReconciliationCaseKey,
            assessment: ReconciliationAssessment,
            evaluatedAtEpochMillis: Long,
        ): ReconciliationCase? {
            if (assessment.semanticDecision == ReconciliationSemanticDecision.NO_MATCH) return null
            val current = active[key]
            if (current != null && current.evidenceFingerprint == assessment.identityEvidenceFingerprint &&
                current.policyVersion == assessment.policyVersion
            ) return current
            revisions += 1
            val status = if (assessment.semanticDecision == ReconciliationSemanticDecision.DIFFERENT_WORK) {
                ReconciliationCaseStatus.RESOLVED_SEPARATE
            } else {
                ReconciliationCaseStatus.PENDING
            }
            return ReconciliationCase(
                id = current?.id ?: "case:${key.left.value}:${key.right.value}",
                key = key,
                status = status,
                assessment = assessment,
                evidenceFingerprint = assessment.identityEvidenceFingerprint,
                policyVersion = assessment.policyVersion,
                resolutionOrigin = if (status == ReconciliationCaseStatus.RESOLVED_SEPARATE) {
                    ReconciliationResolutionOrigin.ENGINE
                } else {
                    null
                },
                contextualPromptSuppressedUntilEpochMillis = null,
                revision = revisions.toLong(),
                createdAtEpochMillis = current?.createdAtEpochMillis ?: evaluatedAtEpochMillis,
                lastEvaluatedAtEpochMillis = evaluatedAtEpochMillis,
            ).also { active[key] = it }
        }

        override suspend fun resolveSeparate(
            caseId: String,
            expectedRevision: Long,
            origin: ReconciliationResolutionOrigin,
            resolvedAtEpochMillis: Long,
        ): Boolean {
            val entry = active.entries.firstOrNull { it.value.id == caseId } ?: return false
            val current = entry.value
            if (current.revision != expectedRevision) return false
            revisions += 1
            active[entry.key] = current.copy(
                status = ReconciliationCaseStatus.RESOLVED_SEPARATE,
                resolutionOrigin = origin,
                revision = revisions.toLong(),
                lastEvaluatedAtEpochMillis = resolvedAtEpochMillis,
            )
            return true
        }

        override suspend fun defer(caseId: String, expectedRevision: Long, suppressUntilEpochMillis: Long): Boolean = false
    }

    private class RecordingMergeExecutor(private val result: StoryMergeResult) : StoryMergeExecutor {
        val requests = mutableListOf<app.openstory.catalog.identity.StoryMergeRequest>()

        override suspend fun execute(request: app.openstory.catalog.identity.StoryMergeRequest): StoryMergeResult {
            requests += request
            return result
        }
    }

    private class RecordingWorkRepository : CanonicalEngineWorkRepository {
        data class Dirty(
            val storyId: StoryId,
            val type: CanonicalEngineWorkType,
            val reason: String,
            val requiredPolicyVersion: Int?,
        )

        val dirty = mutableListOf<Dirty>()

        override suspend fun markDirty(
            storyId: StoryId,
            type: CanonicalEngineWorkType,
            reason: String,
            requiredPolicyVersion: Int?,
        ) {
            dirty += Dirty(storyId, type, reason, requiredPolicyVersion)
        }

        override suspend fun claimReady(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkItem> = emptyList()
        override suspend fun complete(item: CanonicalEngineWorkItem) = Unit
        override suspend fun retry(item: CanonicalEngineWorkItem, failureCode: String, nextAttemptAtEpochMillis: Long) = Unit
        override suspend fun supersede(storyId: StoryId, type: CanonicalEngineWorkType) = Unit
    }

    private class FakeCatalogRepository(records: List<CatalogSourceRecord>) : CatalogRepository {
        private val recordsByKey = records.associateByTo(linkedMapOf(), CatalogSourceRecord::key)
        var globalReads = 0

        fun put(record: CatalogSourceRecord) {
            recordsByKey[record.key] = record
        }

        override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = flowOf(emptyList())
        override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
        override suspend fun matchSnapshot(): CatalogMatchSnapshot = CatalogMatchSnapshot(emptyList())
        override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = null
        override suspend fun sourceRecord(key: CatalogMetadataKey): CatalogSourceRecord? = recordsByKey[SourceKey(key.pluginId, key.sourceId)]
        override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = recordsByKey.values.filter { it.storyId == storyId }
        override suspend fun sourceRecords(): List<CatalogSourceRecord> {
            globalReads += 1
            return recordsByKey.values.toList()
        }
        override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<app.openstory.catalog.repository.CatalogHomeCommitResult, CatalogStoreFailure> = Outcome.Success(app.openstory.catalog.repository.CatalogHomeCommitResult(emptyList()))
        override suspend fun commitSearchSummaries(mutation: CatalogSearchSummaryMutation): Outcome<CatalogSearchSummaryCommitResult, CatalogStoreFailure> = Outcome.Success(CatalogSearchSummaryCommitResult(emptyMap()))
        override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<app.openstory.catalog.repository.CatalogDetailsCommitResult, CatalogStoreFailure> = Outcome.Success(app.openstory.catalog.repository.CatalogDetailsCommitResult(mutation.storyId, emptyList()))
    }
}
