package app.openstory.storage.room.merge

import app.openstory.catalog.diagnostics.CanonicalDecisionTrace
import app.openstory.catalog.diagnostics.CanonicalDiagnostics
import app.openstory.catalog.diagnostics.CanonicalTraceKind
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
    private val diagnostics: CanonicalDiagnostics = CanonicalDiagnostics(),
) : StoryMergeExecutor {
    constructor(
        database: OpenStoryDatabase,
        clock: Clock,
        mergeEventIdFactory: () -> String = { "merge:${UUID.randomUUID()}" },
        beforeAudit: suspend () -> Unit = {},
        diagnostics: CanonicalDiagnostics = CanonicalDiagnostics(),
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
        diagnostics = diagnostics,
    )

    override suspend fun execute(request: StoryMergeRequest): StoryMergeResult {
        val result = when (val preparation = planner.prepare(request)) {
            is StoryGraphMergePreparation.AlreadyCanonical ->
                StoryMergeResult.AlreadyMerged(preparation.survivorStoryId)
            is StoryGraphMergePreparation.ReviewRequired -> StoryMergeResult.ReviewRequired(
                reasons = preparation.reasons,
                protectedContentMappingConflicts = preparation.protectedContentMappingConflicts,
            )
            is StoryGraphMergePreparation.Ready -> writer.commit(preparation.plan)
        }
        recordMergeTrace(request, result)
        return result
    }

    private fun recordMergeTrace(request: StoryMergeRequest, result: StoryMergeResult) {
        val trace = when (result) {
            is StoryMergeResult.Merged -> CanonicalDecisionTrace(
                kind = CanonicalTraceKind.MERGE_COMMITTED,
                storyIds = setOf(request.leftStoryId, request.rightStoryId, result.survivorStoryId),
                policyVersions = mapOf("reconciliation" to request.reconciliationPolicyVersion),
                reasonCodes = listOf("story_merge.committed"),
                evidenceFingerprints = listOf(request.evidenceFingerprint),
            )
            is StoryMergeResult.ReviewRequired -> CanonicalDecisionTrace(
                kind = CanonicalTraceKind.MERGE_BLOCKED,
                storyIds = setOf(request.leftStoryId, request.rightStoryId),
                policyVersions = mapOf("reconciliation" to request.reconciliationPolicyVersion),
                reasonCodes = result.reasons.toList(),
                evidenceFingerprints = listOf(request.evidenceFingerprint),
            )
            is StoryMergeResult.StalePlan -> CanonicalDecisionTrace(
                kind = CanonicalTraceKind.MERGE_BLOCKED,
                storyIds = result.currentStoryIds + request.leftStoryId + request.rightStoryId,
                policyVersions = mapOf("reconciliation" to request.reconciliationPolicyVersion),
                reasonCodes = listOf("story_merge.stale_plan"),
                evidenceFingerprints = listOf(request.evidenceFingerprint),
            )
            is StoryMergeResult.AlreadyMerged -> null
        }
        trace?.let(diagnostics::record)
    }
}
