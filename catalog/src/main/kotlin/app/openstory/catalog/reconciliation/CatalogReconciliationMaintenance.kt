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
        val leftEvidence = catalog.sourceRecords(left).map(ReconciliationEvidenceFactory::fromRecord)
        val rightEvidence = catalog.sourceRecords(right).map(ReconciliationEvidenceFactory::fromRecord)
        return leftEvidence.any { leftRecord ->
            rightEvidence.any { rightRecord ->
                engine.assessPair(leftRecord, rightRecord).identityEvidenceFingerprint == case.evidenceFingerprint
            }
        }
    }
}
