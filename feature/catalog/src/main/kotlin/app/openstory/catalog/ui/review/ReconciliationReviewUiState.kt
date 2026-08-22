package app.openstory.catalog.ui.review

import app.openstory.catalog.reconciliation.ReconciliationReasonCode
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

data class ReconciliationReviewItemUiModel(
    val caseId: String,
    val caseRevision: Long,
    val leftStoryId: StoryId,
    val rightStoryId: StoryId,
    val leftTitle: String,
    val rightTitle: String,
    val leftCoverUrl: String?,
    val rightCoverUrl: String?,
    val confidence: Double,
    val reasonLabels: List<String>,
    val mergeAllowed: Boolean,
    val userStateImpact: Int,
    val isPostMergeCorrection: Boolean = false,
    val reverseAllowed: Boolean = false,
    val reversalBlockerLabels: List<String> = emptyList(),
)

data class ProtectedMappingConflictUiModel(
    val pluginId: PluginId,
    val candidateSourceStoryIds: List<String>,
    val selectedSourceStoryId: String? = null,
)

data class ProtectedConflictUiModel(
    val caseId: String,
    val expectedCaseRevision: Long,
    val conflicts: List<ProtectedMappingConflictUiModel>,
)

data class ReconciliationReviewUiState(
    val items: List<ReconciliationReviewItemUiModel> = emptyList(),
    val resolvingCaseId: String? = null,
    val protectedConflict: ProtectedConflictUiModel? = null,
    val domainConflictReasonLabels: List<String> = emptyList(),
    val failureMessage: String? = null,
)

internal object ReconciliationReviewPresentationPolicy {
    const val strongDuplicateConfidenceThreshold = 0.90
    const val contextualPromptConfidenceThreshold = 0.85
    const val deferDurationMillis = 24L * 60L * 60L * 1_000L

    fun suppressUntil(nowEpochMillis: Long): Long {
        require(nowEpochMillis >= 0L)
        return Math.addExact(nowEpochMillis, deferDurationMillis)
    }
}

internal fun ReconciliationReasonCode.reviewLabel(): String = when (this) {
    ReconciliationReasonCode.DIRECT_SOURCE_OWNER -> "Same source identity"
    ReconciliationReasonCode.WORK_IDENTIFIER_MATCH -> "Matching work identifier"
    ReconciliationReasonCode.WORK_IDENTIFIER_CONFLICT -> "Conflicting work identifier"
    ReconciliationReasonCode.CONTENT_TYPE_MATCH -> "Same content type"
    ReconciliationReasonCode.CONTENT_TYPE_CONFLICT -> "Different content types"
    ReconciliationReasonCode.LINEAGE_COMPATIBLE -> "Compatible source lineage"
    ReconciliationReasonCode.LINEAGE_CONFLICT -> "Conflicting source lineage"
    ReconciliationReasonCode.TITLE_EXACT -> "Titles match exactly"
    ReconciliationReasonCode.TITLE_SIMILAR -> "Titles are very similar"
    ReconciliationReasonCode.AUTHOR_MATCH -> "Authors match"
    ReconciliationReasonCode.AUTHOR_MISSING -> "Author evidence is incomplete"
    ReconciliationReasonCode.AUTHOR_CONFLICT -> "Authors conflict"
    ReconciliationReasonCode.WINNING_LEAD_TOO_SMALL -> "Evidence is too close to decide automatically"
    ReconciliationReasonCode.TITLE_ONLY_NOT_AUTO -> "Title evidence needs review"
    ReconciliationReasonCode.DURABLE_SEPARATION_BLOCK -> "Previously kept separate for this evidence"
}

internal fun String.domainConflictLabel(): String =
    lowercase()
        .split('_')
        .filter(String::isNotBlank)
        .joinToString(" ")
        .replaceFirstChar { it.uppercase() }
