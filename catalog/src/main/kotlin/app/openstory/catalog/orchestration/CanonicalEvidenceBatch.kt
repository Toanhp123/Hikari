package app.openstory.catalog.orchestration

import app.openstory.catalog.fusion.FUSION_POLICY_VERSION
import app.openstory.catalog.reconciliation.RECONCILIATION_POLICY_VERSION
import app.openstory.common.id.StoryId

fun List<CatalogEvidenceChange>.toDeferredCanonicalWorkRequests(): List<CanonicalEngineWorkRequest> =
    groupBy(CatalogEvidenceChange::storyId)
        .toSortedMap(compareBy<StoryId> { storyId -> storyId.value })
        .flatMap { (storyId, storyChanges) ->
            val reason = storyChanges.deferredWorkReason()
            buildList {
                if (storyChanges.any(CatalogEvidenceChange::identityFingerprintChanged)) {
                    add(
                        CanonicalEngineWorkRequest(
                            storyId = storyId,
                            type = CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                            reason = reason,
                            requiredPolicyVersion = RECONCILIATION_POLICY_VERSION,
                        ),
                    )
                }
                if (storyChanges.any(CatalogEvidenceChange::requiresDeferredFusion)) {
                    add(
                        CanonicalEngineWorkRequest(
                            storyId = storyId,
                            type = CanonicalEngineWorkType.FUSION_REBUILD,
                            reason = reason,
                            requiredPolicyVersion = FUSION_POLICY_VERSION,
                        ),
                    )
                }
            }
        }

private fun CatalogEvidenceChange.requiresDeferredFusion(): Boolean =
    identityFingerprintChanged || fusionFingerprintChanged || availabilityChanged

private fun List<CatalogEvidenceChange>.deferredWorkReason(): String = when {
    any { change -> change.level == CatalogEvidenceLevel.FULL } ->
        CanonicalEngineWorkReasons.SOURCE_FULL_CHANGED
    any { change ->
        change.availabilityChanged &&
            !change.identityFingerprintChanged &&
            !change.fusionFingerprintChanged
    } -> CanonicalEngineWorkReasons.SOURCE_AVAILABILITY_CHANGED
    else -> CanonicalEngineWorkReasons.SOURCE_SUMMARY_CHANGED
}
