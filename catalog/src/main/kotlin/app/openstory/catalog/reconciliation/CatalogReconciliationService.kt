package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ReconciliationRunResult {
    data object NoIdentityChange : ReconciliationRunResult
    data class AutoMergeObserved(val left: StoryId, val right: StoryId) : ReconciliationRunResult
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
) {
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
        return persistObserveOnlyDecision(canonicalStoryId, ranked)
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

    private suspend fun persistObserveOnlyDecision(
        currentStoryId: StoryId,
        selection: ReconciliationCandidateSelection,
    ): ReconciliationRunResult {
        val best = selection.ranked.firstOrNull() ?: return ReconciliationRunResult.NoIdentityChange
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
            ReconciliationSemanticDecision.REVIEW -> {
                val recorded = cases.recordAssessment(key, assessment, clock.nowEpochMillis())
                when {
                    recorded == null -> ReconciliationRunResult.NoIdentityChange
                    recorded.status == ReconciliationCaseStatus.RESOLVED_SEPARATE ->
                        ReconciliationRunResult.Separated
                    else -> ReconciliationRunResult.ReviewRecorded(recorded.id)
                }
            }
            ReconciliationSemanticDecision.SAME_WORK -> {
                val recorded = cases.recordAssessment(key, assessment, clock.nowEpochMillis())
                if (recorded?.status == ReconciliationCaseStatus.RESOLVED_SEPARATE) {
                    ReconciliationRunResult.Separated
                } else {
                    val ordered = ReconciliationCaseKey.of(currentStoryId, best.storyId)
                    ReconciliationRunResult.AutoMergeObserved(ordered.left, ordered.right)
                }
            }
        }
    }
}
