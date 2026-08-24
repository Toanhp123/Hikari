package app.openstory.storage.room.merge

import app.openstory.catalog.identity.StoryMergeReverseRequest
import app.openstory.catalog.identity.StoryMergeReversalAssessment
import app.openstory.catalog.identity.StoryMergeReversibility
import app.openstory.storage.room.catalog.StoryMergeEventEntity
import app.openstory.storage.room.catalog.StoryMergeReversalEventEntity

internal const val STORY_MERGE_REVERSAL_SOURCE_OWNERSHIP_CHANGED = "story_merge.reversal.source_ownership_changed"
internal const val STORY_MERGE_REVERSAL_CANONICAL_STATE_CHANGED = "story_merge.reversal.canonical_state_changed"
internal const val STORY_MERGE_REVERSAL_CANONICAL_DEGRADED = "story_merge.reversal.canonical_degraded"
internal const val STORY_MERGE_REVERSAL_PARKED_INVARIANT = "story_merge.reversal.parked_invariant"
internal const val STORY_MERGE_REVERSAL_UNSUPPORTED_POLICY = "story_merge.reversal.unsupported_policy"
internal const val STORY_MERGE_REVERSAL_AUDIT_REQUIRES_REVIEW = "story_merge.reversal.audit_requires_review"
internal const val STORY_MERGE_REVERSAL_NESTED_REDIRECT_LINEAGE = "story_merge.reversal.nested_redirect_lineage"
internal const val STORY_MERGE_REVERSAL_REDIRECT_CHANGED = "story_merge.reversal.redirect_changed"
internal const val STORY_MERGE_REVERSAL_ALREADY_APPLIED = "story_merge.reversal.already_applied"

internal data class PreparedStoryMergeReversal(
    val request: StoryMergeReverseRequest,
    val event: StoryMergeEventEntity,
    val audit: StoryMergeReversalAudit,
)

internal sealed interface StoryMergeReversalPreparation {
    data class Ready(
        val plan: PreparedStoryMergeReversal,
        val assessment: StoryMergeReversalAssessment,
    ) : StoryMergeReversalPreparation

    data class ReviewRequired(val assessment: StoryMergeReversalAssessment) : StoryMergeReversalPreparation
    data class AlreadyReversed(val event: StoryMergeReversalEventEntity) : StoryMergeReversalPreparation
    data object NotAutomaticallyReversible : StoryMergeReversalPreparation
    data object StalePlan : StoryMergeReversalPreparation
    data object NotFound : StoryMergeReversalPreparation
}
