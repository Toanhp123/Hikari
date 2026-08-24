package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.model.ContentType
import app.openstory.common.id.StoryId

enum class ReconciliationSemanticDecision {
    SAME_WORK,
    REVIEW,
    DIFFERENT_WORK,
    NO_MATCH,
}

enum class ReconciliationMergeEligibility {
    MERGEABLE,
    INVARIANT_BLOCKED,
}

enum class ReconciliationReasonCode {
    DIRECT_SOURCE_OWNER,
    WORK_IDENTIFIER_MATCH,
    WORK_IDENTIFIER_CONFLICT,
    CONTENT_TYPE_MATCH,
    CONTENT_TYPE_CONFLICT,
    LINEAGE_COMPATIBLE,
    LINEAGE_CONFLICT,
    TITLE_EXACT,
    TITLE_SIMILAR,
    AUTHOR_MATCH,
    AUTHOR_MISSING,
    AUTHOR_CONFLICT,
    WINNING_LEAD_TOO_SMALL,
    TITLE_ONLY_NOT_AUTO,
    DURABLE_SEPARATION_BLOCK,
}

data class ReconciliationCaseKey(
    val left: StoryId,
    val right: StoryId,
) {
    init {
        require(left.value < right.value) { "Reconciliation case StoryIds must be distinct and ordered" }
    }

    companion object {
        fun of(left: StoryId, right: StoryId): ReconciliationCaseKey {
            require(left != right) { "A Story cannot be reconciled with itself" }
            return if (left.value < right.value) {
                ReconciliationCaseKey(left, right)
            } else {
                ReconciliationCaseKey(right, left)
            }
        }
    }
}

data class ReconciliationEvidence(
    val sourceKey: SourceKey,
    val currentStoryId: StoryId?,
    val contentType: ContentType,
    val comparisonTitles: Set<String>,
    val comparisonAuthors: Set<String>,
    val identifiers: Set<ExternalIdentifier>,
    val lineageTokens: Set<String>,
    val identityEvidenceFingerprint: String,
) {
    init {
        require(comparisonTitles.none(String::isBlank))
        require(comparisonAuthors.none(String::isBlank))
        require(lineageTokens.none(String::isBlank))
        require(identityEvidenceFingerprint.isNotBlank())
    }
}

data class ReconciliationAssessment(
    val policyVersion: Int,
    val semanticDecision: ReconciliationSemanticDecision,
    val mergeEligibility: ReconciliationMergeEligibility,
    val confidence: Double,
    val titleSimilarity: Double?,
    val authorSimilarity: Double?,
    val winningLead: Double?,
    val matchedIdentifiers: Set<ExternalIdentifier>,
    val conflictingIdentifiers: Set<ExternalIdentifier>,
    val reasons: Set<ReconciliationReasonCode>,
    val identityEvidenceFingerprint: String,
) {
    init {
        require(policyVersion > 0)
        require(confidence in 0.0..1.0)
        require(titleSimilarity == null || titleSimilarity in 0.0..1.0)
        require(authorSimilarity == null || authorSimilarity in 0.0..1.0)
        require(winningLead == null || winningLead in 0.0..1.0)
        require(identityEvidenceFingerprint.isNotBlank())
    }
}
