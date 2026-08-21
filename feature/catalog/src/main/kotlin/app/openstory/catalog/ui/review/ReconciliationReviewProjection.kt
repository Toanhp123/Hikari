package app.openstory.catalog.ui.review

import app.openstory.catalog.identity.UserStateFootprint
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.reconciliation.ReconciliationCase
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.common.id.StoryId

internal fun projectReviewQueue(
    cases: List<ReconciliationCase>,
    catalog: List<CatalogStoryProjection>,
    footprints: Map<StoryId, UserStateFootprint>,
): List<ReconciliationReviewItemUiModel> {
    val catalogByStory = catalog.associateBy(CatalogStoryProjection::storyId)
    return cases.map { case ->
        val left = catalogByStory[case.key.left]
        val right = catalogByStory[case.key.right]
        RankedReviewItem(
            case = case,
            ui = ReconciliationReviewItemUiModel(
                caseId = case.id,
                caseRevision = case.revision,
                leftStoryId = case.key.left,
                rightStoryId = case.key.right,
                leftTitle = left?.title ?: case.key.left.value,
                rightTitle = right?.title ?: case.key.right.value,
                leftCoverUrl = left?.coverUrl,
                rightCoverUrl = right?.coverUrl,
                confidence = case.assessment.confidence,
                reasonLabels = case.assessment.reasons.sortedBy { it.name }.map { it.reviewLabel() },
                mergeAllowed = case.assessment.mergeEligibility == ReconciliationMergeEligibility.MERGEABLE,
                userStateImpact = footprints.impact(case.key.left) + footprints.impact(case.key.right),
            ),
        )
    }.sortedWith(
        compareByDescending<RankedReviewItem> { it.case.assessment.confidence }
            .thenByDescending { it.ui.userStateImpact }
            .thenByDescending { it.case.lastEvaluatedAtEpochMillis }
            .thenBy { it.case.createdAtEpochMillis }
            .thenBy { it.case.id },
    ).map(RankedReviewItem::ui)
}

private data class RankedReviewItem(
    val case: ReconciliationCase,
    val ui: ReconciliationReviewItemUiModel,
)

private fun Map<StoryId, UserStateFootprint>.impact(storyId: StoryId): Int =
    get(storyId)?.meaningfulStateTotal ?: 0
