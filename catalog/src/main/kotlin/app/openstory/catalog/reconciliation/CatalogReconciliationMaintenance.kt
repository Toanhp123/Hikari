package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.id.StoryId

data class ReconciliationMaintenanceCase(
    val caseId: String,
    val leftStoryId: StoryId,
    val rightStoryId: StoryId,
    val evidenceFingerprint: String,
    val policyVersion: Int,
) {
    init {
        require(caseId.isNotBlank())
        require(leftStoryId != rightStoryId)
        require(evidenceFingerprint.isNotBlank())
        require(policyVersion > 0)
    }
}

interface CatalogReconciliationMaintenance {
    suspend fun reevaluateStory(storyId: StoryId): List<ReconciliationRunResult>
    suspend fun isEvidenceFingerprintCurrent(case: ReconciliationMaintenanceCase): Boolean
}

class CatalogReconciliationMaintenanceService(
    private val reconciliation: CatalogReconciliationService,
    private val catalog: CatalogRepository,
    private val identity: StoryIdentityRepository,
    private val engine: CatalogReconciliationEngine,
) : CatalogReconciliationMaintenance {
    override suspend fun reevaluateStory(storyId: StoryId): List<ReconciliationRunResult> =
        reconciliation.reevaluateStory(storyId)

    override suspend fun isEvidenceFingerprintCurrent(case: ReconciliationMaintenanceCase): Boolean {
        val left = identity.resolve(case.leftStoryId)
        val right = identity.resolve(case.rightStoryId)
        if (left == right) return false
        val allEvidence = catalog.sourceRecords().map(ReconciliationEvidenceFactory::fromRecord)
        val index = InMemoryCatalogCandidateIndex().apply { rebuild(allEvidence) }
        val pair = ReconciliationCaseKey.of(left, right)
        return allEvidence.asSequence()
            .filter { it.currentStoryId == left || it.currentStoryId == right }
            .any { incoming ->
                val incomingStoryId = requireNotNull(incoming.currentStoryId)
                val candidateStoryIds = index.candidatesFor(incoming)
                    .filterNot { it == incomingStoryId }
                    .toSet()
                val selection = engine.rankCandidates(incoming, index.evidenceFor(candidateStoryIds))
                val bestStoryId = selection.ranked.firstOrNull()?.storyId
                bestStoryId != null && ReconciliationCaseKey.of(incomingStoryId, bestStoryId) == pair &&
                    selection.identityEvidenceFingerprint == case.evidenceFingerprint
        }
    }
}
