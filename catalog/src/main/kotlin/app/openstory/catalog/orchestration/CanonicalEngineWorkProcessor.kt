package app.openstory.catalog.orchestration

import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.fusion.FUSION_POLICY_VERSION
import app.openstory.catalog.fusion.PRIMARY_SELECTION_POLICY_VERSION
import app.openstory.catalog.identity.StoryIdentityInvariantException
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.reconciliation.CatalogReconciliationMaintenance
import app.openstory.catalog.reconciliation.RECONCILIATION_POLICY_VERSION
import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CancellationException

private const val BASE_RETRY_DELAY_MILLIS = 5 * 60 * 1000L
private const val MAX_RETRY_DELAY_MILLIS = 6 * 60 * 60 * 1000L
private const val MAINTENANCE_EXCEPTION_PREFIX = "canonical.maintenance"
private const val MAX_RETRY_DOUBLINGS = 63
private const val RECONCILIATION_WORK_PRIORITY = 0
private const val FUSION_WORK_PRIORITY = 1
private const val POLICY_WORK_PRIORITY = 2
private const val POST_MERGE_DERIVED_WORK_PRIORITY = 3

internal class CanonicalEngineWorkProcessor(
    private val work: CanonicalEngineWorkRepository,
    private val reconciliation: CatalogReconciliationMaintenance,
    private val fusion: CanonicalGenerationRebuilder,
    private val identity: StoryIdentityRepository,
    private val reader: CanonicalEngineMaintenanceReader,
    private val derived: PostMergeDerivedWorkDispatcher,
    private val health: CanonicalMaintenanceHealthMarker,
    private val clock: Clock,
) {
    suspend fun drainReady(limit: Int): CanonicalMaintenanceReport {
        val now = clock.nowEpochMillis()
        val items = work.claimReady(now, limit)
            .sortedWith(
                compareBy<CanonicalEngineWorkItem> { workPriority(it.type) }
                    .thenBy { it.storyId.value }
                    .thenBy { it.type.name },
            )
        var succeeded = 0
        var retried = 0
        var failedInvariant = 0
        val outcomes = items.map { item -> item to process(item) }
        val transitions = outcomes.map { (item, outcome) ->
            when (outcome) {
                WorkOutcome.Success -> CanonicalEngineWorkTransition.Complete(item)
                is WorkOutcome.Retry -> CanonicalEngineWorkTransition.Retry(
                    item = item,
                    failureCode = outcome.code,
                    nextAttemptAtEpochMillis = retryAt(now, item.attemptCount),
                )
                is WorkOutcome.InvariantFailure -> CanonicalEngineWorkTransition.BlockInvariant(item, outcome.code)
            }
        }
        val applied = work.transitionClaimed(transitions)
        check(applied.size == outcomes.size)
        outcomes.zip(applied).forEach { (entry, wasApplied) ->
            if (!wasApplied) return@forEach
            when (val outcome = entry.second) {
                WorkOutcome.Success -> succeeded += 1
                is WorkOutcome.Retry -> retried += 1
                is WorkOutcome.InvariantFailure -> {
                    markDegradedBestEffort(outcome.storyId)
                    failedInvariant += 1
                }
            }
        }

        return CanonicalMaintenanceReport(
            processed = applied.count { it },
            succeeded = succeeded,
            retried = retried,
            failedInvariant = failedInvariant,
            nextAttemptAtEpochMillis = work.nextAttemptAtEpochMillis(),
        )
    }

    private suspend fun process(item: CanonicalEngineWorkItem): WorkOutcome = try {
        val owner = identity.resolve(item.storyId)
        if (identity.identityState(owner) == null) {
            return WorkOutcome.InvariantFailure(
                owner,
                "$MAINTENANCE_EXCEPTION_PREFIX.missing_story_owner",
            )
        }
        if (requiresUnsupportedPolicy(item)) {
            return WorkOutcome.InvariantFailure(
                owner,
                CanonicalMaintenanceFailureCodes.UNSUPPORTED_REQUIRED_POLICY_VERSION,
            )
        }
        when (item.type) {
            CanonicalEngineWorkType.FUSION_REBUILD -> fusionOutcome(
                owner,
                fusion.rebuild(owner, item.fusionReason()),
            )

            CanonicalEngineWorkType.RECONCILIATION_REEVALUATION -> {
                reconciliation.reevaluateStory(owner)
                WorkOutcome.Success
            }

            CanonicalEngineWorkType.POST_MERGE_DERIVED -> when (
                val result = derived.dispatch(
                    owner,
                    CanonicalEngineWorkReasons.postMergeDerivedRequirements(item.reason),
                )
            ) {
                PostMergeDerivedWorkResult.Dispatched -> WorkOutcome.Success
                is PostMergeDerivedWorkResult.Failed -> derivedFailure(owner, result)
            }

            CanonicalEngineWorkType.POLICY_REEVALUATION -> processPolicyReevaluation(owner)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: StoryIdentityInvariantException) {
        WorkOutcome.InvariantFailure(item.storyId, "$MAINTENANCE_EXCEPTION_PREFIX.identity_invariant")
    } catch (_: Exception) {
        WorkOutcome.Retry("$MAINTENANCE_EXCEPTION_PREFIX.${item.type.name.lowercase()}.exception")
    }

    private suspend fun processPolicyReevaluation(storyId: StoryId): WorkOutcome {
        val state = reader.policyState(storyId)
        return when {
            state == null -> WorkOutcome.InvariantFailure(
                storyId,
                "$MAINTENANCE_EXCEPTION_PREFIX.policy_state_missing",
            )

            state.hasFuturePolicyVersion() -> WorkOutcome.InvariantFailure(
                storyId,
                CanonicalMaintenanceFailureCodes.UNSUPPORTED_PERSISTED_POLICY_VERSION,
            )

            else -> processSupportedPolicyReevaluation(storyId, state)
        }
    }

    private suspend fun processSupportedPolicyReevaluation(
        storyId: StoryId,
        state: CanonicalMaintenancePolicyState,
    ): WorkOutcome {
        val reconciliationStale = state.reconciliationPolicyVersions.any {
            it != RECONCILIATION_POLICY_VERSION
        }
        val fusionStale = state.fusionPolicyVersion != FUSION_POLICY_VERSION ||
            state.primarySelectionPolicyVersion != PRIMARY_SELECTION_POLICY_VERSION

        var currentOwner = storyId
        if (reconciliationStale) {
            reconciliation.reevaluateStory(currentOwner)
            currentOwner = identity.resolve(currentOwner)
        }
        return if (fusionStale) {
            fusionOutcome(
                currentOwner,
                fusion.rebuild(currentOwner, CanonicalFusionReason.POLICY_REEVALUATION),
            )
        } else {
            WorkOutcome.Success
        }
    }

    private fun fusionOutcome(
        storyId: StoryId,
        result: CanonicalFusionResult,
    ): WorkOutcome = when (result) {
        is CanonicalFusionResult.Promoted,
        is CanonicalFusionResult.Unchanged,
        is CanonicalFusionResult.Preparing,
        -> WorkOutcome.Success

        is CanonicalFusionResult.Failed -> if (result.retryable) {
            WorkOutcome.Retry(result.code)
        } else {
            WorkOutcome.InvariantFailure(storyId, result.code)
        }
    }

    private suspend fun markDegradedBestEffort(storyId: StoryId) {
        try {
            health.markDegraded(storyId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The parked queue row remains the durable stop signal.
        }
    }

    private fun retryAt(nowEpochMillis: Long, attemptCount: Int): Long {
        val delay = retryDelayMillis(attemptCount)
        return if (Long.MAX_VALUE - nowEpochMillis < delay) Long.MAX_VALUE else nowEpochMillis + delay
    }

    private fun retryDelayMillis(attemptCount: Int): Long {
        var delay = BASE_RETRY_DELAY_MILLIS
        repeat(attemptCount.coerceAtMost(MAX_RETRY_DOUBLINGS)) {
            if (delay >= MAX_RETRY_DELAY_MILLIS) return MAX_RETRY_DELAY_MILLIS
            delay = (delay * 2).coerceAtMost(MAX_RETRY_DELAY_MILLIS)
        }
        return delay
    }

    private fun requiresUnsupportedPolicy(item: CanonicalEngineWorkItem): Boolean {
        val required = item.requiredPolicyVersion
        val supported = when (item.type) {
            CanonicalEngineWorkType.FUSION_REBUILD -> FUSION_POLICY_VERSION
            CanonicalEngineWorkType.RECONCILIATION_REEVALUATION -> RECONCILIATION_POLICY_VERSION
            CanonicalEngineWorkType.POST_MERGE_DERIVED,
            CanonicalEngineWorkType.POLICY_REEVALUATION,
            -> null
        }
        return required != null && (supported == null || required > supported)
    }

    private fun CanonicalMaintenancePolicyState.hasFuturePolicyVersion(): Boolean =
        fusionPolicyVersion?.let { it > FUSION_POLICY_VERSION } == true ||
            primarySelectionPolicyVersion?.let { it > PRIMARY_SELECTION_POLICY_VERSION } == true ||
            reconciliationPolicyVersions.any { it > RECONCILIATION_POLICY_VERSION }

    private fun workPriority(type: CanonicalEngineWorkType): Int = when (type) {
        CanonicalEngineWorkType.RECONCILIATION_REEVALUATION -> RECONCILIATION_WORK_PRIORITY
        CanonicalEngineWorkType.FUSION_REBUILD -> FUSION_WORK_PRIORITY
        CanonicalEngineWorkType.POLICY_REEVALUATION -> POLICY_WORK_PRIORITY
        CanonicalEngineWorkType.POST_MERGE_DERIVED -> POST_MERGE_DERIVED_WORK_PRIORITY
    }

    private fun CanonicalEngineWorkItem.fusionReason(): CanonicalFusionReason = when (reason) {
        CanonicalEngineWorkReasons.SOURCE_AVAILABILITY_CHANGED -> CanonicalFusionReason.SOURCE_AVAILABILITY_CHANGED
        CanonicalEngineWorkReasons.SOURCE_PREFERENCE_CHANGED -> CanonicalFusionReason.SOURCE_PREFERENCE_CHANGED
        CanonicalEngineWorkReasons.STORY_MERGED -> CanonicalFusionReason.POST_MERGE
        CanonicalEngineWorkReasons.POLICY_VERSION_CHANGED -> CanonicalFusionReason.POLICY_REEVALUATION
        else -> CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED
    }

    private fun derivedFailure(
        storyId: StoryId,
        result: PostMergeDerivedWorkResult.Failed,
    ): WorkOutcome = if (result.retryable) {
        WorkOutcome.Retry(result.code)
    } else {
        WorkOutcome.InvariantFailure(storyId, result.code)
    }

    private sealed interface WorkOutcome {
        data object Success : WorkOutcome
        data class Retry(val code: String) : WorkOutcome
        data class InvariantFailure(val storyId: StoryId, val code: String) : WorkOutcome
    }
}
