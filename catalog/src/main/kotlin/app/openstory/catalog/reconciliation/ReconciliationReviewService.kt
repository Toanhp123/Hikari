package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.NoOpStoryMergeReversalExecutor
import app.openstory.catalog.identity.NoOpStoryMergeReversalPlanner
import app.openstory.catalog.identity.ProtectedContentMappingConflict
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResolution
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.identity.StoryMergeReverseRequest
import app.openstory.catalog.identity.StoryMergeReverseResult
import app.openstory.catalog.identity.StoryMergeReversalAssessmentResult
import app.openstory.catalog.identity.StoryMergeReversalExecutor
import app.openstory.catalog.identity.StoryMergeReversalPlanner
import app.openstory.catalog.identity.StoryMergeReversibility
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.common.Clock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CancellationException

enum class ReconciliationReviewAction {
    MERGE,
    KEEP_SEPARATE,
    DEFER,
    REVERSE,
}

data class ProtectedMappingResolution(
    val pluginId: PluginId,
    val sourceStoryId: String,
) {
    init {
        require(sourceStoryId.isNotBlank())
    }
}

data class ReconciliationReviewCommand(
    val caseId: String,
    val expectedCaseRevision: Long,
    val action: ReconciliationReviewAction,
    val protectedMappingResolutions: List<ProtectedMappingResolution> = emptyList(),
    val suppressUntilEpochMillis: Long? = null,
) {
    init {
        require(caseId.isNotBlank())
        require(expectedCaseRevision > 0L)
        require(suppressUntilEpochMillis == null || suppressUntilEpochMillis >= 0L)
    }
}

data class ReconciliationReversalOption(
    val mergeEventId: String,
    val reversibility: StoryMergeReversibility,
    val reasonCodes: Set<String>,
) {
    init {
        require(mergeEventId.isNotBlank())
        require(reasonCodes.none(String::isBlank))
    }
}

sealed interface ReconciliationReviewResult {
    data class Merged(val survivorStoryId: StoryId) : ReconciliationReviewResult
    data class Reversed(
        val restoredStoryId: StoryId,
        val survivingStoryId: StoryId,
    ) : ReconciliationReviewResult
    data object KeptSeparate : ReconciliationReviewResult
    data class Deferred(val untilEpochMillis: Long) : ReconciliationReviewResult
    data class ConflictResolutionRequired(
        val conflicts: List<ProtectedContentMappingConflict>,
    ) : ReconciliationReviewResult
    data class DomainStateChangeRequired(
        val reasonCodes: Set<String>,
    ) : ReconciliationReviewResult
    data object InvariantBlocked : ReconciliationReviewResult
    data object StaleCase : ReconciliationReviewResult
}

class ReconciliationReviewService(
    private val cases: ReconciliationCaseRepository,
    private val mergeExecutor: StoryMergeExecutor,
    private val clock: Clock,
    private val orchestrator: CanonicalEngineEventSink,
    private val reversalPlanner: StoryMergeReversalPlanner = NoOpStoryMergeReversalPlanner,
    private val reversalExecutor: StoryMergeReversalExecutor = NoOpStoryMergeReversalExecutor,
    private val identity: StoryIdentityRepository? = null,
    private val lineages: StoryMergeLineageReader = EmptyStoryMergeLineageReader,
) {
    suspend fun resolve(command: ReconciliationReviewCommand): ReconciliationReviewResult {
        val case = cases.find(command.caseId)
        return if (case == null || !case.accepts(command)) {
            ReconciliationReviewResult.StaleCase
        } else {
            when (command.action) {
                ReconciliationReviewAction.MERGE -> merge(case, command.protectedMappingResolutions)
                ReconciliationReviewAction.KEEP_SEPARATE -> keepSeparate(case)
                ReconciliationReviewAction.DEFER -> defer(case, requireNotNull(command.suppressUntilEpochMillis))
                ReconciliationReviewAction.REVERSE -> reverse(case)
            }
        }
    }

    suspend fun reversalOption(caseId: String, expectedRevision: Long): ReconciliationReversalOption? {
        val case = cases.find(caseId)
        val context = case
            ?.takeIf { it.revision == expectedRevision && it.status == ReconciliationCaseStatus.PENDING }
            ?.let { reversalContext(it) }
        return context?.let { it.toReversalOption(reversalPlanner.assess(it.request)) }
    }

    private fun ReversalContext.toReversalOption(
        result: StoryMergeReversalAssessmentResult,
    ): ReconciliationReversalOption = when (result) {
        is StoryMergeReversalAssessmentResult.Assessed -> ReconciliationReversalOption(
            mergeEventId = result.assessment.mergeEventId,
            reversibility = result.assessment.reversibility,
            reasonCodes = result.assessment.reasonCodes,
        )
        StoryMergeReversalAssessmentResult.StalePlan -> unavailableOption(
            StoryMergeReversibility.REQUIRES_REVIEW_TO_REVERSE,
            REVERSAL_STALE_REASON,
        )
        StoryMergeReversalAssessmentResult.NotAutomaticallyReversible,
        StoryMergeReversalAssessmentResult.NotFound,
        -> unavailableOption(
            StoryMergeReversibility.NOT_AUTOMATICALLY_REVERSIBLE,
            REVERSAL_UNAVAILABLE_REASON,
        )
    }

    private fun ReversalContext.unavailableOption(
        reversibility: StoryMergeReversibility,
        reasonCode: String,
    ): ReconciliationReversalOption = ReconciliationReversalOption(
        mergeEventId = lineage.mergeEventId,
        reversibility = reversibility,
        reasonCodes = setOf(reasonCode),
    )

    private fun ReconciliationCase.accepts(command: ReconciliationReviewCommand): Boolean =
        if (revision == command.expectedCaseRevision) {
            acceptsCurrentRevision(command)
        } else {
            acceptsRepeatedKeepSeparate(command)
        }

    private fun ReconciliationCase.acceptsCurrentRevision(command: ReconciliationReviewCommand): Boolean =
        when (command.action) {
            ReconciliationReviewAction.MERGE ->
                (status == ReconciliationCaseStatus.PENDING || status == ReconciliationCaseStatus.RESOLVED_MERGED) &&
                    command.suppressUntilEpochMillis == null
            ReconciliationReviewAction.KEEP_SEPARATE ->
                (status == ReconciliationCaseStatus.PENDING ||
                    (status == ReconciliationCaseStatus.RESOLVED_SEPARATE &&
                        resolutionOrigin == ReconciliationResolutionOrigin.USER)) &&
                    command.protectedMappingResolutions.isEmpty() &&
                    command.suppressUntilEpochMillis == null
            ReconciliationReviewAction.DEFER ->
                status == ReconciliationCaseStatus.PENDING &&
                    command.protectedMappingResolutions.isEmpty() &&
                    command.suppressUntilEpochMillis != null
            ReconciliationReviewAction.REVERSE ->
                status == ReconciliationCaseStatus.PENDING &&
                    command.protectedMappingResolutions.isEmpty() &&
                    command.suppressUntilEpochMillis == null
        }

    private fun ReconciliationCase.acceptsRepeatedKeepSeparate(command: ReconciliationReviewCommand): Boolean =
        command.action == ReconciliationReviewAction.KEEP_SEPARATE &&
            status == ReconciliationCaseStatus.RESOLVED_SEPARATE &&
            resolutionOrigin == ReconciliationResolutionOrigin.USER &&
            revision == command.expectedCaseRevision + 1L

    private suspend fun merge(
        case: ReconciliationCase,
        protectedMappingResolutions: List<ProtectedMappingResolution>,
    ): ReconciliationReviewResult {
        val validationFailure = mergeValidationFailure(case, protectedMappingResolutions)
        return validationFailure ?: executeMerge(case, protectedMappingResolutions)
    }

    private fun mergeValidationFailure(
        case: ReconciliationCase,
        protectedMappingResolutions: List<ProtectedMappingResolution>,
    ): ReconciliationReviewResult? =
        when {
            case.status == ReconciliationCaseStatus.PENDING &&
                case.assessment.mergeEligibility != ReconciliationMergeEligibility.MERGEABLE ->
                ReconciliationReviewResult.InvariantBlocked
            hasDuplicatePluginSelection(protectedMappingResolutions) -> ReconciliationReviewResult.StaleCase
            else -> null
        }

    private fun hasDuplicatePluginSelection(
        protectedMappingResolutions: List<ProtectedMappingResolution>,
    ): Boolean = protectedMappingResolutions
        .groupingBy(ProtectedMappingResolution::pluginId)
        .eachCount()
        .values
        .any { it > 1 }

    private suspend fun executeMerge(
        case: ReconciliationCase,
        protectedMappingResolutions: List<ProtectedMappingResolution>,
    ): ReconciliationReviewResult {
        val request = StoryMergeRequest(
            requestId = "reconciliation-review:${case.id}:${case.revision}",
            leftStoryId = case.key.left,
            rightStoryId = case.key.right,
            origin = StoryMergeOrigin.USER_REVIEW_APPROVAL,
            reconciliationCaseId = case.id,
            evidenceFingerprint = case.evidenceFingerprint,
            reconciliationPolicyVersion = case.policyVersion,
            resolutions = protectedMappingResolutions
                .sortedWith(compareBy<ProtectedMappingResolution> { it.pluginId.value }.thenBy { it.sourceStoryId })
                .map { StoryMergeResolution.ContentMappingTarget(it.pluginId, it.sourceStoryId) },
        )
        return when (val result = mergeExecutor.execute(request)) {
            is StoryMergeResult.Merged -> committedMerge(result.survivorStoryId)
            is StoryMergeResult.AlreadyMerged -> committedMerge(result.survivorStoryId)
            is StoryMergeResult.StalePlan -> ReconciliationReviewResult.StaleCase
            is StoryMergeResult.ReviewRequired -> result.toReviewResult()
        }
    }

    private suspend fun committedMerge(storyId: StoryId): ReconciliationReviewResult {
        try {
            orchestrator.onStoryMerged(storyId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Merge is already durable; Room-owned engine work remains the recovery path.
        }
        return ReconciliationReviewResult.Merged(storyId)
    }

    private suspend fun reverse(case: ReconciliationCase): ReconciliationReviewResult {
        val context = reversalContext(case) ?: return ReconciliationReviewResult.InvariantBlocked
        return when (val result = reversalExecutor.reverse(context.request)) {
            is StoryMergeReverseResult.Reversed -> committedReversal(result)
            is StoryMergeReverseResult.ReviewRequired ->
                ReconciliationReviewResult.DomainStateChangeRequired(result.reasons)
            StoryMergeReverseResult.NotAutomaticallyReversible -> ReconciliationReviewResult.InvariantBlocked
            StoryMergeReverseResult.StalePlan,
            StoryMergeReverseResult.NotFound,
            -> ReconciliationReviewResult.StaleCase
        }
    }

    private suspend fun committedReversal(
        result: StoryMergeReverseResult.Reversed,
    ): ReconciliationReviewResult {
        try {
            orchestrator.onStorySplit(result.survivingStoryId, result.restoredStoryId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Split and durable work are already committed; background maintenance can recover the wakeup.
        }
        return ReconciliationReviewResult.Reversed(result.restoredStoryId, result.survivingStoryId)
    }

    private suspend fun keepSeparate(case: ReconciliationCase): ReconciliationReviewResult = when {
        case.status == ReconciliationCaseStatus.RESOLVED_SEPARATE &&
            case.resolutionOrigin == ReconciliationResolutionOrigin.USER ->
            ReconciliationReviewResult.KeptSeparate
        reversalContext(case) != null ->
            ReconciliationReviewResult.DomainStateChangeRequired(setOf(REVERSAL_REQUIRED_REASON))
        else -> persistSeparateResolution(case)
    }

    private suspend fun persistSeparateResolution(case: ReconciliationCase): ReconciliationReviewResult {
        val resolved = cases.resolveSeparate(
            caseId = case.id,
            expectedRevision = case.revision,
            origin = ReconciliationResolutionOrigin.USER,
            resolvedAtEpochMillis = clock.nowEpochMillis().also { require(it >= 0L) },
        )
        return if (resolved) ReconciliationReviewResult.KeptSeparate else ReconciliationReviewResult.StaleCase
    }

    private suspend fun reversalContext(case: ReconciliationCase): ReversalContext? =
        identity
            ?.takeIf { case.assessment.mergeEligibility == ReconciliationMergeEligibility.INVARIANT_BLOCKED }
            ?.let { repository ->
                latestReversalLineage(case)?.let { lineage ->
                    reversalContext(case, lineage, repository)
                }
            }

    private suspend fun latestReversalLineage(case: ReconciliationCase): StoryMergeLineage? =
        lineages.lineagesFor(case.key.left)
            .asSequence()
            .filter { it.historicalCaseKey() == case.key }
            .sortedWith(compareByDescending<StoryMergeLineage> { it.mergedAtEpochMillis }.thenBy { it.mergeEventId })
            .firstOrNull()

    private suspend fun reversalContext(
        case: ReconciliationCase,
        lineage: StoryMergeLineage,
        repository: StoryIdentityRepository,
    ): ReversalContext? = repository.identityState(lineage.survivorStoryId)?.let { identityState ->
        ReversalContext(
            lineage = lineage,
            request = StoryMergeReverseRequest(
                mergeEventId = lineage.mergeEventId,
                expectedSurvivorIdentityRevision = identityState.identityRevision,
                expectedReconciliationCaseId = case.id,
                expectedReconciliationCaseRevision = case.revision,
            ),
        )
    }

    private suspend fun defer(
        case: ReconciliationCase,
        suppressUntilEpochMillis: Long,
    ): ReconciliationReviewResult {
        val now = clock.nowEpochMillis()
        require(now >= 0L)
        val currentSuppression = case.contextualPromptSuppressedUntilEpochMillis
        return when {
            suppressUntilEpochMillis <= now -> ReconciliationReviewResult.StaleCase
            currentSuppression != null && currentSuppression >= suppressUntilEpochMillis ->
                ReconciliationReviewResult.Deferred(currentSuppression)
            else -> persistDeferral(case, suppressUntilEpochMillis)
        }
    }

    private suspend fun persistDeferral(
        case: ReconciliationCase,
        suppressUntilEpochMillis: Long,
    ): ReconciliationReviewResult {
        val deferred = cases.defer(
            caseId = case.id,
            expectedRevision = case.revision,
            suppressUntilEpochMillis = suppressUntilEpochMillis,
        )
        val persisted = if (deferred) cases.find(case.id) else null
        return persisted.toDeferredResult(expectedRevision = case.revision)
    }

    private fun ReconciliationCase?.toDeferredResult(expectedRevision: Long): ReconciliationReviewResult =
        if (this == null || status != ReconciliationCaseStatus.PENDING || revision != expectedRevision) {
            ReconciliationReviewResult.StaleCase
        } else {
            ReconciliationReviewResult.Deferred(requireNotNull(contextualPromptSuppressedUntilEpochMillis))
        }

    private fun StoryMergeResult.ReviewRequired.toReviewResult(): ReconciliationReviewResult =
        if (protectedContentMappingConflicts.isNotEmpty()) {
            ReconciliationReviewResult.ConflictResolutionRequired(
                conflicts = protectedContentMappingConflicts
                    .map { conflict ->
                        ProtectedContentMappingConflict(
                            pluginId = conflict.pluginId,
                            candidateSourceStoryIds = conflict.candidateSourceStoryIds.toSortedSet(),
                        )
                    }
                    .sortedBy { it.pluginId.value },
            )
        } else {
            ReconciliationReviewResult.DomainStateChangeRequired(reasons.toSortedSet())
        }

    private data class ReversalContext(
        val lineage: StoryMergeLineage,
        val request: StoryMergeReverseRequest,
    )

    private companion object {
        const val REVERSAL_REQUIRED_REASON = "story_merge.reversal_required"
        const val REVERSAL_STALE_REASON = "story_merge.reversal_stale"
        const val REVERSAL_UNAVAILABLE_REASON = "story_merge.reversal_unavailable"
    }
}
