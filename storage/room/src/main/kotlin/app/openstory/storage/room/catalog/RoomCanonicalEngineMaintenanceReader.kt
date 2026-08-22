package app.openstory.storage.room.catalog

import app.openstory.catalog.orchestration.CanonicalEngineMaintenanceReader
import app.openstory.catalog.orchestration.CanonicalMaintenanceInvariantIssue
import app.openstory.catalog.orchestration.CanonicalMaintenancePolicyState
import app.openstory.catalog.reconciliation.ReconciliationMaintenanceCase
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase

class RoomCanonicalEngineMaintenanceReader internal constructor(
    private val dao: CanonicalEngineMaintenanceDao,
) : CanonicalEngineMaintenanceReader {
    constructor(database: OpenStoryDatabase) : this(database.canonicalEngineMaintenanceDao())

    override suspend fun stalePolicyStoryIds(
        fusionPolicyVersion: Int,
        primarySelectionPolicyVersion: Int,
        reconciliationPolicyVersion: Int,
        limit: Int,
    ): List<StoryId> {
        require(fusionPolicyVersion > 0)
        require(primarySelectionPolicyVersion > 0)
        require(reconciliationPolicyVersion > 0)
        require(limit > 0)
        return dao.stalePolicyStoryIds(
            fusionPolicyVersion,
            primarySelectionPolicyVersion,
            reconciliationPolicyVersion,
            limit,
        ).map(::StoryId)
    }

    override suspend fun policyState(storyId: StoryId): CanonicalMaintenancePolicyState? {
        val generation = dao.generationPolicy(storyId.value) ?: return null
        return CanonicalMaintenancePolicyState(
            fusionPolicyVersion = generation.fusionPolicyVersion,
            primarySelectionPolicyVersion = generation.primarySelectionPolicyVersion,
            reconciliationPolicyVersions = dao.reconciliationPolicyVersions(storyId.value).toSet(),
        )
    }

    override suspend fun pendingReconciliationCases(limit: Int): List<ReconciliationMaintenanceCase> {
        require(limit > 0)
        return dao.pendingCases(limit).map { row ->
            ReconciliationMaintenanceCase(
                caseId = row.caseId,
                leftStoryId = StoryId(row.leftStoryId),
                rightStoryId = StoryId(row.rightStoryId),
                evidenceFingerprint = row.evidenceFingerprint,
                policyVersion = row.policyVersion,
            )
        }
    }

    override suspend fun redirectInconsistencies(limit: Int): List<CanonicalMaintenanceInvariantIssue> {
        require(limit > 0)
        return dao.redirectInconsistencies(limit).map { row ->
            CanonicalMaintenanceInvariantIssue(
                storyId = StoryId(row.canonicalStoryId),
                code = "$REDIRECT_TARGET_INVALID:${row.retiredStoryId}->${row.canonicalStoryId}",
            )
        }
    }

    private companion object {
        const val REDIRECT_TARGET_INVALID = "canonical.invariant.redirect_target_invalid"
    }
}
