package app.openstory.catalog.reconciliation

import app.openstory.catalog.diagnostics.CanonicalDecisionTrace
import app.openstory.catalog.diagnostics.CanonicalDiagnostics
import app.openstory.catalog.diagnostics.CanonicalTraceKind
import app.openstory.common.id.StoryId

internal fun CanonicalDiagnostics.recordReconciliationSelection(
    currentStoryId: StoryId,
    incoming: ReconciliationEvidence,
    candidates: List<ReconciliationEvidence>,
    selection: ReconciliationCandidateSelection,
) {
    val best = selection.ranked.firstOrNull()
    val bestStoryId = best?.storyId
    val bestSources = if (bestStoryId == null) {
        emptyList()
    } else {
        candidates.filter { it.currentStoryId == bestStoryId }
    }
    record(
        CanonicalDecisionTrace(
            kind = CanonicalTraceKind.RECONCILIATION,
            storyIds = listOfNotNull(currentStoryId, bestStoryId).toCollection(linkedSetOf()),
            sourceKeys = (listOf(incoming.sourceKey) + bestSources.map { it.sourceKey })
                .toCollection(linkedSetOf()),
            policyVersions = best?.assessment?.let { mapOf("reconciliation" to it.policyVersion) }.orEmpty(),
            reasonCodes = buildList {
                add(selection.semanticDecision.diagnosticDecisionCode())
                add("eligibility.${selection.mergeEligibility.name.lowercase()}")
                selection.reasons.mapTo(this) { it.name }
            },
            evidenceFingerprints = buildList {
                add(incoming.identityEvidenceFingerprint)
                best?.assessment?.identityEvidenceFingerprint?.let(::add)
            },
        ),
    )
}

internal fun CanonicalDiagnostics.recordPostMergeCorrection(
    lineage: StoryMergeLineage,
    incoming: ReconciliationEvidence,
    assessment: ReconciliationAssessment,
) {
    record(
        CanonicalDecisionTrace(
            kind = CanonicalTraceKind.RECONCILIATION,
            storyIds = setOf(lineage.historicalCaseKey().left, lineage.historicalCaseKey().right),
            sourceKeys = (setOf(incoming.sourceKey) + lineage.oppositeSourceKeys(incoming.sourceKey))
                .toCollection(linkedSetOf()),
            policyVersions = mapOf("reconciliation" to assessment.policyVersion),
            reasonCodes = buildList {
                add("decision.review")
                add("eligibility.invariant_blocked")
                assessment.reasons.mapTo(this) { it.name }
            },
            evidenceFingerprints = listOf(assessment.identityEvidenceFingerprint),
        ),
    )
}

private fun ReconciliationSemanticDecision.diagnosticDecisionCode(): String = when (this) {
    ReconciliationSemanticDecision.SAME_WORK -> "decision.auto_merge"
    ReconciliationSemanticDecision.REVIEW -> "decision.review"
    ReconciliationSemanticDecision.DIFFERENT_WORK -> "decision.separate"
    ReconciliationSemanticDecision.NO_MATCH -> "decision.no_match"
}
