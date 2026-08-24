package app.openstory.catalog.reconciliation

import app.openstory.catalog.CatalogStoreFailure
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
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
import app.openstory.common.Clock
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RetroactiveReconciliationTest {
    private val survivor = StoryId("story:a")
    private val retired = StoryId("story:b")
    private val sourceA = SourceKey(PluginId("provider.one"), "a")
    private val sourceB = SourceKey(PluginId("provider.two"), "b")

    @Test
    fun contradictoryIdentityEvidenceAfterMergeReopensHistoricalCaseForReviewWithoutSplit() = runTest {
        val records = listOf(
            record(sourceA, survivor, "work-1", "identity:a"),
            record(sourceB, survivor, "work-2", "identity:b:changed"),
        )
        val catalog = RetroactiveCatalogRepository(records)
        val cases = RetroactiveCaseRepository(mergedCase())
        val index = RecordingCandidateIndex()
        val service = service(catalog, cases, index)

        val result = service.reconcile(sourceB)

        val review = assertIs<ReconciliationRunResult.ReviewRecorded>(result)
        val current = cases.current
        assertEquals(current.id, review.caseId)
        assertEquals(ReconciliationCaseKey.of(survivor, retired), current.key)
        assertEquals(ReconciliationCaseStatus.PENDING, current.status)
        assertEquals(ReconciliationSemanticDecision.REVIEW, current.assessment.semanticDecision)
        assertEquals(ReconciliationMergeEligibility.INVARIANT_BLOCKED, current.assessment.mergeEligibility)
        assertTrue(ReconciliationReasonCode.WORK_IDENTIFIER_CONFLICT in current.assessment.reasons)
        assertEquals(2, catalog.sourceRecords(survivor).size)
        assertEquals(1, cases.revisionsAdded)
        assertEquals(1, index.rebuilds)
    }

    @Test
    fun sameCorrectionFingerprintDoesNotCreateRevisionChurnAndCandidateIndexStillUpserts() = runTest {
        val records = listOf(
            record(sourceA, survivor, "work-1", "identity:a"),
            record(sourceB, survivor, "work-2", "identity:b:changed"),
        )
        val catalog = RetroactiveCatalogRepository(records)
        val cases = RetroactiveCaseRepository(mergedCase())
        val index = RecordingCandidateIndex()
        val service = service(catalog, cases, index)

        assertIs<ReconciliationRunResult.ReviewRecorded>(service.reconcile(sourceB))
        assertIs<ReconciliationRunResult.ReviewRecorded>(service.reconcile(sourceB))

        assertEquals(1, cases.revisionsAdded)
        assertEquals(1, index.rebuilds)
        assertEquals(1, index.upserts)
    }

    @Test
    fun compatibleEvidenceInsideMergedStoryDoesNotCreateCorrectionCase() = runTest {
        val records = listOf(
            record(sourceA, survivor, "work-1", "identity:a"),
            record(sourceB, survivor, "work-1", "identity:b:compatible"),
        )
        val cases = RetroactiveCaseRepository(mergedCase())
        val service = service(RetroactiveCatalogRepository(records), cases, RecordingCandidateIndex())

        val result = service.reconcile(sourceB)

        assertEquals(ReconciliationRunResult.NoIdentityChange, result)
        assertEquals(0, cases.revisionsAdded)
        assertEquals(ReconciliationCaseStatus.RESOLVED_MERGED, cases.current.status)
    }

    private fun service(
        catalog: CatalogRepository,
        cases: RetroactiveCaseRepository,
        index: CatalogCandidateIndex,
    ) = CatalogReconciliationService(
        catalog = catalog,
        identity = IdentityRepository,
        candidateIndex = index,
        engine = CatalogReconciliationEngine(ReconciliationPolicy()),
        cases = cases,
        clock = Clock { 2_000L },
        lineageReader = FixedLineageReader(
            StoryMergeLineage(
                mergeEventId = "merge:1",
                survivorStoryId = survivor,
                retiredStoryId = retired,
                reconciliationCaseId = mergedCase().id,
                survivorSourceKeysBefore = setOf(sourceA),
                retiredSourceKeysBefore = setOf(sourceB),
                mergedAtEpochMillis = 1_000L,
            ),
        ),
    )

    private fun record(
        key: SourceKey,
        storyId: StoryId,
        workId: String,
        identityFingerprint: String,
    ): CatalogSourceRecord {
        val entry = CatalogEntry(
            storyId = storyId,
            pluginId = key.pluginId,
            sourceId = key.sourceId,
            title = "Same title",
            authors = setOf("Same author"),
            contentType = ContentType.MANGA,
            externalIdentifiers = setOf(
                ExternalIdentifier("work", workId, ExternalIdentifierScope.WORK),
            ),
        )
        return CatalogSourceRecord(
            key = key,
            storyId = storyId,
            entry = entry,
            summary = CatalogMetadataStamp("1", 1L),
            full = CatalogMetadataStamp("1", 2L),
            identityFingerprint = identityFingerprint,
            fusionFingerprint = "fusion:${key.pluginId.value}:${key.sourceId}",
        )
    }

    private fun mergedCase(): ReconciliationCase {
        val key = ReconciliationCaseKey.of(survivor, retired)
        val assessment = ReconciliationAssessment(
            policyVersion = RECONCILIATION_POLICY_VERSION,
            semanticDecision = ReconciliationSemanticDecision.SAME_WORK,
            mergeEligibility = ReconciliationMergeEligibility.MERGEABLE,
            confidence = 1.0,
            titleSimilarity = 1.0,
            authorSimilarity = 1.0,
            winningLead = null,
            matchedIdentifiers = emptySet(),
            conflictingIdentifiers = emptySet(),
            reasons = setOf(ReconciliationReasonCode.TITLE_EXACT, ReconciliationReasonCode.AUTHOR_MATCH),
            identityEvidenceFingerprint = "merged:fingerprint",
        )
        return ReconciliationCase(
            id = "case:historical",
            key = key,
            status = ReconciliationCaseStatus.RESOLVED_MERGED,
            assessment = assessment,
            evidenceFingerprint = assessment.identityEvidenceFingerprint,
            policyVersion = assessment.policyVersion,
            resolutionOrigin = ReconciliationResolutionOrigin.ENGINE,
            contextualPromptSuppressedUntilEpochMillis = null,
            revision = 1L,
            createdAtEpochMillis = 1_000L,
            lastEvaluatedAtEpochMillis = 1_000L,
        )
    }
}

private class FixedLineageReader(private val lineage: StoryMergeLineage) : StoryMergeLineageReader {
    override suspend fun lineagesFor(storyId: StoryId): List<StoryMergeLineage> = listOf(lineage)
}

private object IdentityRepository : StoryIdentityRepository {
    override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(storyId)
    override suspend fun resolve(storyId: StoryId): StoryId = storyId
    override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? = null
}

private class RecordingCandidateIndex : CatalogCandidateIndex {
    var rebuilds = 0
    var upserts = 0
    private val storyIds = linkedSetOf<StoryId>()

    override fun rebuild(records: Collection<ReconciliationEvidence>) {
        rebuilds += 1
        storyIds.clear()
        records.mapNotNullTo(storyIds) { it.currentStoryId }
    }
    override fun upsert(record: ReconciliationEvidence) {
        upserts += 1
        record.currentStoryId?.let(storyIds::add)
    }
    override fun remove(sourceKey: SourceKey) = Unit
    override fun candidatesFor(incoming: ReconciliationEvidence): List<StoryId> = storyIds.toList()
}

private class RetroactiveCaseRepository(initial: ReconciliationCase) : ReconciliationCaseRepository {
    var current = initial
    var revisionsAdded = 0

    override fun observePending(): Flow<List<ReconciliationCase>> = flowOf(listOf(current).filter { it.status == ReconciliationCaseStatus.PENDING })
    override fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>> = flowOf(listOf(current))
    override suspend fun find(caseId: String): ReconciliationCase? = current.takeIf { it.id == caseId }
    override suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase? = current.takeIf { it.key == key }
    override suspend fun recordAssessment(
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCase? {
        if (current.key == key && current.evidenceFingerprint == assessment.identityEvidenceFingerprint &&
            current.policyVersion == assessment.policyVersion
        ) return current
        revisionsAdded += 1
        current = current.copy(
            key = key,
            status = ReconciliationCaseStatus.PENDING,
            assessment = assessment,
            evidenceFingerprint = assessment.identityEvidenceFingerprint,
            policyVersion = assessment.policyVersion,
            resolutionOrigin = null,
            contextualPromptSuppressedUntilEpochMillis = null,
            revision = current.revision + 1,
            lastEvaluatedAtEpochMillis = evaluatedAtEpochMillis,
        )
        return current
    }
    override suspend fun resolveSeparate(caseId: String, expectedRevision: Long, origin: ReconciliationResolutionOrigin, resolvedAtEpochMillis: Long): Boolean = false
    override suspend fun defer(caseId: String, expectedRevision: Long, suppressUntilEpochMillis: Long): Boolean = false
}

private class RetroactiveCatalogRepository(records: List<CatalogSourceRecord>) : CatalogRepository {
    private val byKey = records.associateBy(CatalogSourceRecord::key)
    override fun observeHomes(): Flow<List<CatalogHomeSnapshot>> = flowOf(emptyList())
    override fun observeStory(storyId: StoryId): Flow<StoryCatalogSnapshot?> = flowOf(null)
    override suspend fun matchSnapshot(): CatalogMatchSnapshot = CatalogMatchSnapshot(emptyList())
    override suspend fun metadataSnapshot(key: CatalogMetadataKey): CatalogMetadataSnapshot? = null
    override suspend fun sourceRecord(key: CatalogMetadataKey): CatalogSourceRecord? = byKey[SourceKey(key.pluginId, key.sourceId)]
    override suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord> = byKey.values.filter { it.storyId == storyId }
    override suspend fun sourceRecords(): List<CatalogSourceRecord> = byKey.values.toList()
    override suspend fun commitHomeRefresh(mutation: CatalogHomeMutation): Outcome<app.openstory.catalog.repository.CatalogHomeCommitResult, CatalogStoreFailure> = error("unused")
    override suspend fun commitSearchSummaries(mutation: CatalogSearchSummaryMutation): Outcome<CatalogSearchSummaryCommitResult, CatalogStoreFailure> = error("unused")
    override suspend fun commitDetails(mutation: CatalogDetailsMutation): Outcome<app.openstory.catalog.repository.CatalogDetailsCommitResult, CatalogStoreFailure> = error("unused")
}
