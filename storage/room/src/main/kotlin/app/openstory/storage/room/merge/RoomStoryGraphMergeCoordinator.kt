package app.openstory.storage.room.merge

import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.common.Clock
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.RoomStoryIdentityResolver
import java.util.UUID

class RoomStoryGraphMergeCoordinator internal constructor(
    private val planner: RoomStoryGraphMergePlanner,
    private val writer: RoomStoryMergeWriter,
) : StoryMergeExecutor {
    constructor(
        database: OpenStoryDatabase,
        clock: Clock,
        mergeEventIdFactory: () -> String = { "merge:${UUID.randomUUID()}" },
        beforeAudit: suspend () -> Unit = {},
    ) : this(
        planner = RoomStoryGraphMergePlanner(
            identity = RoomStoryIdentityResolver(database),
            reader = RoomStoryMergeReaders(database),
        ),
        writer = RoomStoryMergeWriter(
            database = database,
            identity = RoomStoryIdentityResolver(database),
            readers = RoomStoryMergeReaders(database),
            clock = clock,
            mergeEventIdFactory = mergeEventIdFactory,
            beforeAudit = beforeAudit,
        ),
    )

    override suspend fun execute(request: StoryMergeRequest): StoryMergeResult =
        when (val preparation = planner.prepare(request)) {
            is StoryGraphMergePreparation.AlreadyCanonical ->
                StoryMergeResult.AlreadyMerged(preparation.survivorStoryId)
            is StoryGraphMergePreparation.ReviewRequired -> StoryMergeResult.ReviewRequired(
                reasons = preparation.reasons,
                protectedContentMappingConflicts = preparation.protectedContentMappingConflicts,
            )
            is StoryGraphMergePreparation.Ready -> writer.commit(preparation.plan)
        }
}
