package app.openstory.catalog.reconciliation

import app.openstory.catalog.identity.ProtectedContentMappingConflict
import app.openstory.catalog.identity.StoryMergeExecutor
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResolution
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.common.Clock
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId

enum class ReconciliationReviewAction {
    MERGE,
    KEEP_SEPARATE,
    DEFER,
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

sealed interface ReconciliationReviewResult {
    data class Merged(val survivorStoryId: StoryId) : ReconciliationReviewResult
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
            }
        }
    }

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
    ): Boolean =
        protectedMappingResolutions
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
            is StoryMergeResult.Merged -> ReconciliationReviewResult.Merged(result.survivorStoryId)
            is StoryMergeResult.AlreadyMerged -> ReconciliationReviewResult.Merged(result.survivorStoryId)
            is StoryMergeResult.StalePlan -> ReconciliationReviewResult.StaleCase
            is StoryMergeResult.ReviewRequired -> result.toReviewResult()
        }
    }

    private suspend fun keepSeparate(case: ReconciliationCase): ReconciliationReviewResult {
        if (case.status == ReconciliationCaseStatus.RESOLVED_SEPARATE &&
            case.resolutionOrigin == ReconciliationResolutionOrigin.USER
        ) {
            return ReconciliationReviewResult.KeptSeparate
        }
        val resolved = cases.resolveSeparate(
            caseId = case.id,
            expectedRevision = case.revision,
            origin = ReconciliationResolutionOrigin.USER,
            resolvedAtEpochMillis = clock.nowEpochMillis().also { require(it >= 0L) },
        )
        return if (resolved) ReconciliationReviewResult.KeptSeparate else ReconciliationReviewResult.StaleCase
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
            ReconciliationReviewResult.Deferred(
                requireNotNull(contextualPromptSuppressedUntilEpochMillis),
            )
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
}
