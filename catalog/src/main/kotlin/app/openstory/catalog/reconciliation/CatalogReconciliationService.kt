package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.orchestration.CanonicalEngineWorkRepository
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ReconciliationExecutionMode {
    OBSERVE_ONLY,
    APPLY_ELIGIBLE_AUTO_MERGES,
}

sealed interface ReconciliationRunResult {
    data object NoIdentityChange : ReconciliationRunResult
    data class AutoMergeObserved(val left: StoryId, val right: StoryId) : ReconciliationRunResult
    data class AutoMergeApplied(val survivorStoryId: StoryId) : ReconciliationRunResult
    data class ReevaluationScheduled(val storyIds: Set<StoryId>) : ReconciliationRunResult {
        init {
            require(storyIds.isNotEmpty())
        }
    }
    data class ReviewRecorded(val caseId: String) : ReconciliationRunResult
    data object Separated : ReconciliationRunResult
}

class CatalogReconciliationService(
    private val catalog: CatalogRepository,
    private val identity: StoryIdentityRepository,
    private val candidateIndex: CatalogCandidateIndex,
    private val engine: CatalogReconciliationEngine,
    private val cases: ReconciliationCaseRepository,
    private val clock: Clock,
    private val executionMode: ReconciliationExecutionMode = ReconciliationExecutionMode.OBSERVE_ONLY,
    private val mergeExecutor: StoryMergeExecutor? = null,
    private val work: CanonicalEngineWorkRepository? = null,
) {
    init {
        require(
            executionMode == ReconciliationExecutionMode.OBSERVE_ONLY ||
                (mergeExecutor != null && work != null),
        ) { "Apply mode requires a merge executor and durable work repository" }
    }

    private val candidateIndexMutex = Mutex()
    private var candidateIndexInitialized = false

    suspend fun reconcile(sourceKey: SourceKey): ReconciliationRunResult {
        val changedRecord = catalog.sourceRecord(CatalogMetadataKey(sourceKey.pluginId, sourceKey.sourceId))
            ?: return ReconciliationRunResult.NoIdentityChange
        val canonicalStoryId = identity.resolve(changedRecord.storyId)
        val incoming = ReconciliationEvidenceFactory.fromRecord(changedRecord)
        val candidateStoryIds = shortlist(incoming)
        val resolvedCandidateStoryIds = linkedSetOf<StoryId>()
        candidateStoryIds.forEach { candidateStoryId ->
            val resolved = identity.resolve(candidateStoryId)
            if (resolved != canonicalStoryId) resolvedCandidateStoryIds += resolved
        }
        val candidates = resolvedCandidateStoryIds
            .sortedBy(StoryId::value)
            .flatMap { candidateStoryId -> catalog.sourceRecords(candidateStoryId) }
            .map(ReconciliationEvidenceFactory::fromRecord)
        val ranked = engine.rankCandidates(incoming, candidates)
        return persistDecision(canonicalStoryId, ranked)
    }

    suspend fun reevaluateStory(storyId: StoryId): List<ReconciliationRunResult> {
        val resolved = identity.resolve(storyId)
        return catalog.sourceRecords(resolved)
            .sortedBy { record -> "${record.key.pluginId.value}:${record.key.sourceId}" }
            .map { record -> reconcile(record.key) }
    }

    suspend fun invalidateCandidateIndex() {
        candidateIndexMutex.withLock {
            candidateIndexInitialized = false
        }
    }

    private suspend fun shortlist(incoming: ReconciliationEvidence): List<StoryId> = candidateIndexMutex.withLock {
        if (!candidateIndexInitialized) {
            candidateIndex.rebuild(
                catalog.sourceRecords().map(ReconciliationEvidenceFactory::fromRecord),
            )
            candidateIndexInitialized = true
        } else {
            candidateIndex.upsert(incoming)
        }
        candidateIndex.candidatesFor(incoming)
    }

    private suspend fun persistDecision(
        currentStoryId: StoryId,
        selection: ReconciliationCandidateSelection,
    ): ReconciliationRunResult {
        val best = selection.ranked.firstOrNull()
        return if (best == null) {
            ReconciliationRunResult.NoIdentityChange
        } else {
            persistRankedDecision(currentStoryId, selection, best)
        }
    }

    private suspend fun persistRankedDecision(
        currentStoryId: StoryId,
        selection: ReconciliationCandidateSelection,
        best: RankedReconciliationCandidate,
    ): ReconciliationRunResult {
        val key = ReconciliationCaseKey.of(currentStoryId, best.storyId)
        val assessment = best.assessment.copy(
            semanticDecision = selection.semanticDecision,
            mergeEligibility = selection.mergeEligibility,
            winningLead = selection.winningLead,
            reasons = selection.reasons,
        )
        return when (selection.semanticDecision) {
            ReconciliationSemanticDecision.NO_MATCH -> ReconciliationRunResult.NoIdentityChange
            ReconciliationSemanticDecision.DIFFERENT_WORK -> {
                cases.recordAssessment(key, assessment, clock.nowEpochMillis())
                ReconciliationRunResult.Separated
            }
            ReconciliationSemanticDecision.REVIEW -> persistReviewDecision(key, assessment)
            ReconciliationSemanticDecision.SAME_WORK -> persistSameWorkDecision(
                currentStoryId = currentStoryId,
                bestStoryId = best.storyId,
                key = key,
                assessment = assessment,
                mergeEligibility = selection.mergeEligibility,
            )
        }
    }

    private suspend fun persistReviewDecision(
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
    ): ReconciliationRunResult {
        val recorded = cases.recordAssessment(key, assessment, clock.nowEpochMillis())
        return when {
            recorded == null -> ReconciliationRunResult.NoIdentityChange
            recorded.status == ReconciliationCaseStatus.RESOLVED_SEPARATE -> ReconciliationRunResult.Separated
            recorded.status == ReconciliationCaseStatus.RESOLVED_MERGED ||
                recorded.status == ReconciliationCaseStatus.SUPERSEDED -> ReconciliationRunResult.NoIdentityChange
            else -> ReconciliationRunResult.ReviewRecorded(recorded.id)
        }
    }

    private suspend fun persistSameWorkDecision(
        currentStoryId: StoryId,
        bestStoryId: StoryId,
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
        mergeEligibility: ReconciliationMergeEligibility,
    ): ReconciliationRunResult {
        val recorded = cases.recordAssessment(key, assessment, clock.nowEpochMillis())
        return if (recorded == null) {
            ReconciliationRunResult.NoIdentityChange
        } else {
            resultForSameWorkCase(currentStoryId, bestStoryId, recorded, mergeEligibility)
        }
    }

    private suspend fun resultForSameWorkCase(
        currentStoryId: StoryId,
        bestStoryId: StoryId,
        recorded: ReconciliationCase,
        mergeEligibility: ReconciliationMergeEligibility,
    ): ReconciliationRunResult = when (recorded.status) {
        ReconciliationCaseStatus.RESOLVED_SEPARATE -> ReconciliationRunResult.Separated
        ReconciliationCaseStatus.RESOLVED_MERGED,
        ReconciliationCaseStatus.SUPERSEDED,
        -> ReconciliationRunResult.NoIdentityChange
        ReconciliationCaseStatus.PENDING -> when {
            mergeEligibility != ReconciliationMergeEligibility.MERGEABLE ->
                ReconciliationRunResult.ReviewRecorded(recorded.id)
            executionMode == ReconciliationExecutionMode.OBSERVE_ONLY -> {
                val ordered = ReconciliationCaseKey.of(currentStoryId, bestStoryId)
                ReconciliationRunResult.AutoMergeObserved(ordered.left, ordered.right)
            }
            else -> applyEligibleMerge(recorded)
        }
    }

    private suspend fun applyEligibleMerge(case: ReconciliationCase): ReconciliationRunResult {
        val executor = requireNotNull(mergeExecutor)
        val result = executor.execute(
            StoryMergeRequest(
                requestId = "reconciliation:${case.id}:${case.revision}",
                leftStoryId = case.key.left,
                rightStoryId = case.key.right,
                origin = StoryMergeOrigin.AUTO_RECONCILIATION,
                reconciliationCaseId = case.id,
                evidenceFingerprint = case.evidenceFingerprint,
                reconciliationPolicyVersion = case.policyVersion,
            ),
        )
        return when (result) {
            is StoryMergeResult.Merged -> {
                invalidateCandidateIndex()
                ReconciliationRunResult.AutoMergeApplied(result.survivorStoryId)
            }
            is StoryMergeResult.AlreadyMerged -> {
                invalidateCandidateIndex()
                ReconciliationRunResult.AutoMergeApplied(result.survivorStoryId)
            }
            is StoryMergeResult.ReviewRequired -> ReconciliationRunResult.ReviewRecorded(case.id)
            is StoryMergeResult.StalePlan -> scheduleReevaluation(result.currentStoryIds, case.policyVersion)
        }
    }

    private suspend fun scheduleReevaluation(
        storyIds: Set<StoryId>,
        policyVersion: Int,
    ): ReconciliationRunResult {
        val durableWork = requireNotNull(work)
        storyIds.sortedBy(StoryId::value).forEach { storyId ->
            durableWork.markDirty(
                storyId = storyId,
                type = CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                reason = "merge-plan-stale",
                requiredPolicyVersion = policyVersion,
            )
        }
        invalidateCandidateIndex()
        return ReconciliationRunResult.ReevaluationScheduled(storyIds)
    }
}
