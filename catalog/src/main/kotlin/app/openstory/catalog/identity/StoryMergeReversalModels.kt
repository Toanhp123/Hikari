package app.openstory.catalog.identity

import app.openstory.common.id.StoryId

enum class StoryMergeReversibility {
    REVERSIBLE,
    REQUIRES_REVIEW_TO_REVERSE,
    NOT_AUTOMATICALLY_REVERSIBLE,
}

data class StoryMergeReverseRequest(
    val mergeEventId: String,
    val expectedSurvivorIdentityRevision: Long,
    val expectedReconciliationCaseId: String? = null,
    val expectedReconciliationCaseRevision: Long? = null,
) {
    init {
        require(mergeEventId.isNotBlank())
        require(expectedSurvivorIdentityRevision >= 0L)
        require((expectedReconciliationCaseId == null) == (expectedReconciliationCaseRevision == null))
        require(expectedReconciliationCaseId == null || expectedReconciliationCaseId.isNotBlank())
        require(expectedReconciliationCaseRevision == null || expectedReconciliationCaseRevision > 0L)
    }
}

data class StoryMergeReversalAssessment(
    val mergeEventId: String,
    val survivingStoryId: StoryId,
    val restoredStoryId: StoryId,
    val reversibility: StoryMergeReversibility,
    val reasonCodes: Set<String>,
) {
    init {
        require(mergeEventId.isNotBlank())
        require(survivingStoryId != restoredStoryId)
        require(reasonCodes.none(String::isBlank))
        require(reversibility == StoryMergeReversibility.REVERSIBLE || reasonCodes.isNotEmpty())
    }
}

sealed interface StoryMergeReversalAssessmentResult {
    data class Assessed(val assessment: StoryMergeReversalAssessment) : StoryMergeReversalAssessmentResult
    data object NotAutomaticallyReversible : StoryMergeReversalAssessmentResult
    data object StalePlan : StoryMergeReversalAssessmentResult
    data object NotFound : StoryMergeReversalAssessmentResult
}

sealed interface StoryMergeReverseResult {
    data class Reversed(
        val restoredStoryId: StoryId,
        val survivingStoryId: StoryId,
        val reversalEventId: String,
    ) : StoryMergeReverseResult {
        init {
            require(restoredStoryId != survivingStoryId)
            require(reversalEventId.isNotBlank())
        }
    }

    data class ReviewRequired(val reasons: Set<String>) : StoryMergeReverseResult {
        init {
            require(reasons.isNotEmpty())
            require(reasons.none(String::isBlank))
        }
    }

    data object NotAutomaticallyReversible : StoryMergeReverseResult
    data object StalePlan : StoryMergeReverseResult
    data object NotFound : StoryMergeReverseResult
}

fun interface StoryMergeReversalPlanner {
    suspend fun assess(request: StoryMergeReverseRequest): StoryMergeReversalAssessmentResult
}

fun interface StoryMergeReversalExecutor {
    suspend fun reverse(request: StoryMergeReverseRequest): StoryMergeReverseResult
}

object NoOpStoryMergeReversalPlanner : StoryMergeReversalPlanner {
    override suspend fun assess(request: StoryMergeReverseRequest): StoryMergeReversalAssessmentResult =
        StoryMergeReversalAssessmentResult.NotFound
}

object NoOpStoryMergeReversalExecutor : StoryMergeReversalExecutor {
    override suspend fun reverse(request: StoryMergeReverseRequest): StoryMergeReverseResult =
        StoryMergeReverseResult.NotFound
}
