package app.openstory.storage.room.merge

import androidx.room.withTransaction
import app.openstory.catalog.identity.StoryMergeReverseRequest
import app.openstory.catalog.identity.StoryMergeReverseResult
import app.openstory.catalog.identity.StoryMergeReversalAssessmentResult
import app.openstory.catalog.identity.StoryMergeReversalExecutor
import app.openstory.catalog.identity.StoryMergeReversalPlanner
import app.openstory.common.Clock
import app.openstory.storage.room.OpenStoryDatabase
import java.util.UUID

class RoomStoryMergeReversalCoordinator internal constructor(
    private val database: OpenStoryDatabase,
    private val planner: RoomStoryMergeReversalPlanner,
    private val writer: RoomStoryMergeReversalWriter,
) : StoryMergeReversalPlanner, StoryMergeReversalExecutor {
    constructor(
        database: OpenStoryDatabase,
        clock: Clock,
        reversalEventIdFactory: () -> String = { "reversal:${UUID.randomUUID()}" },
        beforeAudit: suspend () -> Unit = {},
    ) : this(
        database = database,
        planner = RoomStoryMergeReversalPlanner(database),
        writer = RoomStoryMergeReversalWriter(
            database = database,
            clock = clock,
            reversalEventIdFactory = reversalEventIdFactory,
            beforeAudit = beforeAudit,
        ),
    )

    override suspend fun assess(request: StoryMergeReverseRequest): StoryMergeReversalAssessmentResult =
        planner.prepare(request).toAssessmentResult()

    override suspend fun reverse(
        request: StoryMergeReverseRequest,
    ): StoryMergeReverseResult = database.withTransaction {
        when (val preparation = planner.prepare(request)) {
            is StoryMergeReversalPreparation.Ready -> writer.commit(preparation.plan).toResult()
            is StoryMergeReversalPreparation.ReviewRequired ->
                StoryMergeReverseResult.ReviewRequired(preparation.assessment.reasonCodes)
            is StoryMergeReversalPreparation.AlreadyReversed -> StoryMergeReverseResult.Reversed(
                restoredStoryId = app.openstory.common.id.StoryId(preparation.event.restoredStoryId),
                survivingStoryId = app.openstory.common.id.StoryId(preparation.event.survivingStoryId),
                reversalEventId = preparation.event.reversalEventId,
            )
            StoryMergeReversalPreparation.NotAutomaticallyReversible ->
                StoryMergeReverseResult.NotAutomaticallyReversible
            StoryMergeReversalPreparation.StalePlan -> StoryMergeReverseResult.StalePlan
            StoryMergeReversalPreparation.NotFound -> StoryMergeReverseResult.NotFound
        }
    }

    private fun StoryMergeReversalPreparation.toAssessmentResult(): StoryMergeReversalAssessmentResult = when (this) {
        is StoryMergeReversalPreparation.Ready -> StoryMergeReversalAssessmentResult.Assessed(assessment)
        is StoryMergeReversalPreparation.ReviewRequired -> StoryMergeReversalAssessmentResult.Assessed(assessment)
        is StoryMergeReversalPreparation.AlreadyReversed -> StoryMergeReversalAssessmentResult.Assessed(
            app.openstory.catalog.identity.StoryMergeReversalAssessment(
                mergeEventId = event.mergeEventId,
                survivingStoryId = app.openstory.common.id.StoryId(event.survivingStoryId),
                restoredStoryId = app.openstory.common.id.StoryId(event.restoredStoryId),
                reversibility = app.openstory.catalog.identity.StoryMergeReversibility.NOT_AUTOMATICALLY_REVERSIBLE,
                reasonCodes = setOf(STORY_MERGE_REVERSAL_ALREADY_APPLIED),
            ),
        )
        StoryMergeReversalPreparation.NotAutomaticallyReversible ->
            StoryMergeReversalAssessmentResult.NotAutomaticallyReversible
        StoryMergeReversalPreparation.StalePlan -> StoryMergeReversalAssessmentResult.StalePlan
        StoryMergeReversalPreparation.NotFound -> StoryMergeReversalAssessmentResult.NotFound
    }

    private fun StoryMergeReversalWriteResult.toResult() = StoryMergeReverseResult.Reversed(
        restoredStoryId = restoredStoryId,
        survivingStoryId = survivingStoryId,
        reversalEventId = reversalEventId,
    )
}
