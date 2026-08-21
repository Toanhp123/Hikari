package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.matching.TitleNormalizer
import app.openstory.common.id.StoryId
import java.security.MessageDigest

class CatalogReconciliationEngine(
    private val policy: ReconciliationPolicy,
) {
    fun assessPair(
        left: ReconciliationEvidence,
        right: ReconciliationEvidence,
    ): ReconciliationAssessment {
        if (left.sourceKey == right.sourceKey) {
            return ReconciliationAssessment(
                policyVersion = policy.version,
                semanticDecision = ReconciliationSemanticDecision.SAME_WORK,
                mergeEligibility = ReconciliationMergeEligibility.MERGEABLE,
                confidence = 1.0,
                titleSimilarity = maxTitleSimilarity(left, right),
                authorSimilarity = authorSimilarity(left, right),
                winningLead = null,
                matchedIdentifiers = matchingWorkIdentifiers(left, right),
                conflictingIdentifiers = emptySet(),
                reasons = setOf(ReconciliationReasonCode.DIRECT_SOURCE_OWNER),
                identityEvidenceFingerprint = pairFingerprint(left, right),
            )
        }

        val titleSimilarity = maxTitleSimilarity(left, right)
        val authorSimilarity = authorSimilarity(left, right)
        val matchedIdentifiers = matchingWorkIdentifiers(left, right)
        val conflictingIdentifiers = conflictingWorkIdentifiers(left, right)
        val contentConflict = left.contentType != right.contentType
        val lineageConflict = lineageConflict(left, right)
        val workMatch = matchedIdentifiers.isNotEmpty()
        val workConflict = conflictingIdentifiers.isNotEmpty()
        val reasons = buildReasons(
            titleSimilarity = titleSimilarity,
            authorSimilarity = authorSimilarity,
            contentConflict = contentConflict,
            lineageConflict = lineageConflict,
            workMatch = workMatch,
            workConflict = workConflict,
        )
        val legacyConfidence = legacyConfidence(titleSimilarity, authorSimilarity)

        val decision = when {
            workConflict -> Decision(
                ReconciliationSemanticDecision.REVIEW,
                ReconciliationMergeEligibility.INVARIANT_BLOCKED,
            )
            contentConflict || lineageConflict -> if (workMatch) {
                Decision(
                    ReconciliationSemanticDecision.REVIEW,
                    ReconciliationMergeEligibility.INVARIANT_BLOCKED,
                )
            } else {
                Decision(
                    ReconciliationSemanticDecision.DIFFERENT_WORK,
                    ReconciliationMergeEligibility.INVARIANT_BLOCKED,
                )
            }
            workMatch -> Decision(
                ReconciliationSemanticDecision.SAME_WORK,
                ReconciliationMergeEligibility.MERGEABLE,
            )
            titleSimilarity >= policy.autoTitleSimilarityAt &&
                authorSimilarity != null &&
                authorSimilarity >= policy.autoAuthorSimilarityAt ->
                Decision(
                    ReconciliationSemanticDecision.SAME_WORK,
                    ReconciliationMergeEligibility.MERGEABLE,
                )
            titleSimilarity >= policy.reviewTitleSimilarityAt ->
                Decision(ReconciliationSemanticDecision.REVIEW, ReconciliationMergeEligibility.MERGEABLE)
            else -> Decision(ReconciliationSemanticDecision.NO_MATCH, ReconciliationMergeEligibility.MERGEABLE)
        }
        val confidence = if (workMatch || workConflict) 1.0 else legacyConfidence

        return ReconciliationAssessment(
            policyVersion = policy.version,
            semanticDecision = decision.semantic,
            mergeEligibility = decision.eligibility,
            confidence = confidence,
            titleSimilarity = titleSimilarity,
            authorSimilarity = authorSimilarity,
            winningLead = null,
            matchedIdentifiers = matchedIdentifiers,
            conflictingIdentifiers = conflictingIdentifiers,
            reasons = reasons,
            identityEvidenceFingerprint = pairFingerprint(left, right),
        )
    }

    fun rankCandidates(
        incoming: ReconciliationEvidence,
        candidates: List<ReconciliationEvidence>,
    ): ReconciliationCandidateSelection {
        val strongestByStory = candidates
            .asSequence()
            .mapNotNull { candidate -> candidate.currentStoryId?.let { it to assessPair(incoming, candidate) } }
            .groupBy(Pair<StoryId, ReconciliationAssessment>::first)
            .map { (storyId, assessments) ->
                RankedReconciliationCandidate(
                    storyId = storyId,
                    assessment = assessments.map(Pair<StoryId, ReconciliationAssessment>::second)
                        .sortedWith(assessmentOrdering)
                        .first(),
                )
            }
            .sortedWith(candidateOrdering)

        if (strongestByStory.isEmpty()) {
            return ReconciliationCandidateSelection(
                ranked = emptyList(),
                semanticDecision = ReconciliationSemanticDecision.NO_MATCH,
                mergeEligibility = ReconciliationMergeEligibility.MERGEABLE,
                winningLead = null,
                reasons = emptySet(),
            )
        }

        val top = strongestByStory.first()
        val runnerUp = strongestByStory.getOrNull(1)
        val lead = runnerUp?.let { (top.assessment.confidence - it.assessment.confidence).coerceAtLeast(0.0) }
        val leadTooSmall = top.assessment.semanticDecision == ReconciliationSemanticDecision.SAME_WORK &&
            lead != null && lead < policy.minimumWinningLead
        val semantic = if (leadTooSmall) ReconciliationSemanticDecision.REVIEW else top.assessment.semanticDecision
        val reasons = if (leadTooSmall) {
            top.assessment.reasons + ReconciliationReasonCode.WINNING_LEAD_TOO_SMALL
        } else {
            top.assessment.reasons
        }
        val ranked = strongestByStory.mapIndexed { index, candidate ->
            if (index == 0) {
                candidate.copy(assessment = candidate.assessment.copy(winningLead = lead))
            } else {
                candidate
            }
        }
        return ReconciliationCandidateSelection(
            ranked = ranked,
            semanticDecision = semantic,
            mergeEligibility = top.assessment.mergeEligibility,
            winningLead = lead,
            reasons = reasons,
        )
    }

    private fun buildReasons(
        titleSimilarity: Double,
        authorSimilarity: Double?,
        contentConflict: Boolean,
        lineageConflict: Boolean,
        workMatch: Boolean,
        workConflict: Boolean,
    ): Set<ReconciliationReasonCode> = buildSet {
        add(
            if (contentConflict) ReconciliationReasonCode.CONTENT_TYPE_CONFLICT
            else ReconciliationReasonCode.CONTENT_TYPE_MATCH,
        )
        if (workMatch) add(ReconciliationReasonCode.WORK_IDENTIFIER_MATCH)
        if (workConflict) add(ReconciliationReasonCode.WORK_IDENTIFIER_CONFLICT)
        if (lineageConflict) add(ReconciliationReasonCode.LINEAGE_CONFLICT)
        if (titleSimilarity == 1.0) {
            add(ReconciliationReasonCode.TITLE_EXACT)
        } else if (titleSimilarity >= policy.reviewTitleSimilarityAt) {
            add(ReconciliationReasonCode.TITLE_SIMILAR)
        }
        when {
            authorSimilarity == null -> add(ReconciliationReasonCode.AUTHOR_MISSING)
            authorSimilarity >= policy.autoAuthorSimilarityAt -> add(ReconciliationReasonCode.AUTHOR_MATCH)
            else -> add(ReconciliationReasonCode.AUTHOR_CONFLICT)
        }
        if (!workMatch && titleSimilarity >= policy.reviewTitleSimilarityAt && authorSimilarity == null) {
            add(ReconciliationReasonCode.TITLE_ONLY_NOT_AUTO)
        }
    }

    private fun maxTitleSimilarity(left: ReconciliationEvidence, right: ReconciliationEvidence): Double {
        var best = 0.0
        left.comparisonTitles.forEach { leftTitle ->
            right.comparisonTitles.forEach { rightTitle ->
                best = maxOf(best, TitleNormalizer.similarity(leftTitle, rightTitle))
            }
        }
        return best
    }

    private fun authorSimilarity(left: ReconciliationEvidence, right: ReconciliationEvidence): Double? =
        TitleNormalizer.setSimilarity(left.comparisonAuthors, right.comparisonAuthors)

    private fun legacyConfidence(title: Double, author: Double?): Double =
        if (author == null) title else title * TITLE_WEIGHT + author * AUTHOR_WEIGHT

    private fun matchingWorkIdentifiers(
        left: ReconciliationEvidence,
        right: ReconciliationEvidence,
    ): Set<ExternalIdentifier> = workIdentifiers(left).intersect(workIdentifiers(right))

    private fun conflictingWorkIdentifiers(
        left: ReconciliationEvidence,
        right: ReconciliationEvidence,
    ): Set<ExternalIdentifier> {
        val leftByNamespace = workIdentifiers(left).groupBy(ExternalIdentifier::namespace)
        val rightByNamespace = workIdentifiers(right).groupBy(ExternalIdentifier::namespace)
        return leftByNamespace.keys.intersect(rightByNamespace.keys).asSequence()
            .filter { namespace ->
                leftByNamespace.getValue(namespace)
                    .intersect(rightByNamespace.getValue(namespace).toSet())
                    .isEmpty()
            }
            .flatMap { namespace ->
                (leftByNamespace.getValue(namespace) + rightByNamespace.getValue(namespace)).asSequence()
            }
            .toSortedSet(identifierOrdering)
    }

    private fun workIdentifiers(evidence: ReconciliationEvidence): Set<ExternalIdentifier> = evidence.identifiers
        .filterTo(linkedSetOf()) { it.scope == ExternalIdentifierScope.WORK }

    private fun lineageConflict(left: ReconciliationEvidence, right: ReconciliationEvidence): Boolean =
        left.lineageTokens.isNotEmpty() && right.lineageTokens.isNotEmpty() &&
            left.lineageTokens.intersect(right.lineageTokens).isEmpty()

    private fun pairFingerprint(left: ReconciliationEvidence, right: ReconciliationEvidence): String {
        val semantic = listOf(left.identityEvidenceFingerprint, right.identityEvidenceFingerprint)
            .sorted()
            .joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(semantic.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class Decision(
        val semantic: ReconciliationSemanticDecision,
        val eligibility: ReconciliationMergeEligibility,
    )

    private companion object {
        const val TITLE_WEIGHT = 0.8
        const val AUTHOR_WEIGHT = 0.2
        const val SAME_WORK_PRIORITY = 3

        val identifierOrdering: Comparator<ExternalIdentifier> = compareBy<ExternalIdentifier> { it.namespace }
            .thenBy { it.scope.name }
            .thenBy { it.value }

        val assessmentOrdering: Comparator<ReconciliationAssessment> =
            compareByDescending<ReconciliationAssessment> { semanticPriority(it.semanticDecision) }
                .thenByDescending { it.confidence }
                .thenBy { it.identityEvidenceFingerprint }

        val candidateOrdering: Comparator<RankedReconciliationCandidate> =
            compareByDescending<RankedReconciliationCandidate> { semanticPriority(it.assessment.semanticDecision) }
                .thenByDescending { it.assessment.confidence }
                .thenBy { it.storyId.value }

        fun semanticPriority(decision: ReconciliationSemanticDecision): Int = when (decision) {
            ReconciliationSemanticDecision.SAME_WORK -> SAME_WORK_PRIORITY
            ReconciliationSemanticDecision.REVIEW -> 2
            ReconciliationSemanticDecision.DIFFERENT_WORK -> 1
            ReconciliationSemanticDecision.NO_MATCH -> 0
        }
    }
}

data class RankedReconciliationCandidate(
    val storyId: StoryId,
    val assessment: ReconciliationAssessment,
)

data class ReconciliationCandidateSelection(
    val ranked: List<RankedReconciliationCandidate>,
    val semanticDecision: ReconciliationSemanticDecision,
    val mergeEligibility: ReconciliationMergeEligibility,
    val winningLead: Double?,
    val reasons: Set<ReconciliationReasonCode>,
)
