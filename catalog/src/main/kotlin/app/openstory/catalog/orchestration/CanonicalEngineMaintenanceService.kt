package app.openstory.catalog.orchestration

import app.openstory.catalog.diagnostics.CanonicalDecisionTrace
import app.openstory.catalog.diagnostics.CanonicalTraceKind
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.fusion.FUSION_POLICY_VERSION
import app.openstory.catalog.fusion.PRIMARY_SELECTION_POLICY_VERSION
import app.openstory.catalog.identity.StoryIdentityInvariantException
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.reconciliation.CatalogReconciliationMaintenance
import app.openstory.catalog.reconciliation.RECONCILIATION_POLICY_VERSION
import app.openstory.catalog.reconciliation.ReconciliationMaintenanceCase
import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class CanonicalEngineMaintenanceService @Inject constructor(
    private val work: CanonicalEngineWorkRepository,
    private val reconciliation: CatalogReconciliationMaintenance,
    fusion: CanonicalGenerationRebuilder,
    private val identity: StoryIdentityRepository,
    private val reader: CanonicalEngineMaintenanceReader,
    derived: PostMergeDerivedWorkDispatcher,
    observability: CanonicalMaintenanceObservability,
    clock: Clock,
) {
    private val mutex = Mutex()
    private val queue = CanonicalEngineWorkProcessor(
        work = work,
        reconciliation = reconciliation,
        fusion = fusion,
        identity = identity,
        reader = reader,
        derived = derived,
        health = observability.health,
        clock = clock,
    )
    private val healthMarker = observability.health
    private val diagnostics = observability.diagnostics

    suspend fun drainReady(limit: Int): CanonicalMaintenanceReport {
        require(limit > 0)
        return mutex.withLock { queue.drainReady(limit) }
    }

    suspend fun enqueuePolicyReevaluationIfNeeded(limit: Int): Int {
        require(limit > 0)
        return mutex.withLock { enqueuePolicyReevaluationLocked(limit).markedStories }
    }

    suspend fun runConsistencySafetyPass(limit: Int): CanonicalMaintenanceReport {
        require(limit > 0)
        return mutex.withLock {
            requeueRecoverablePolicyBlocksLocked(limit)
            val invariantIssues = reader.invariantIssues(limit)
            invariantIssues.forEach { issue ->
                issue.storyId?.let { storyId -> markResolvedOwnerDegradedBestEffort(storyId) }
                recordInvariantViolation(issue)
            }
            val policyResult = enqueuePolicyReevaluationLocked(limit)
            val pendingCaseInvariantFailures = enqueueChangedPendingCasesLocked(limit)
            val drained = queue.drainReady(limit)
            drained.copy(
                failedInvariant = drained.failedInvariant + invariantIssues.size +
                    policyResult.invariantFailures + pendingCaseInvariantFailures,
            )
        }
    }

    private suspend fun requeueRecoverablePolicyBlocksLocked(limit: Int) {
        work.blocked(RECOVERABLE_POLICY_BLOCK_CODES, limit).forEach { item ->
            val recoverable = when (item.lastFailureCode) {
                CanonicalMaintenanceFailureCodes.UNSUPPORTED_REQUIRED_POLICY_VERSION ->
                    item.requiredPolicyVersion?.let { required ->
                        supportedPolicyVersion(item.type)?.let { supported -> required <= supported }
                    } == true

                CanonicalMaintenanceFailureCodes.UNSUPPORTED_PERSISTED_POLICY_VERSION -> {
                    val owner = try {
                        identity.resolve(item.storyId)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: StoryIdentityInvariantException) {
                        null
                    }
                    val state = owner?.let { reader.policyState(it) }
                    state != null && !state.hasFuturePolicyVersion()
                }

                else -> false
            }
            if (recoverable) {
                work.requeueBlocked(item)
            }
        }
    }

    private fun supportedPolicyVersion(type: CanonicalEngineWorkType): Int? = when (type) {
        CanonicalEngineWorkType.FUSION_REBUILD -> FUSION_POLICY_VERSION
        CanonicalEngineWorkType.RECONCILIATION_REEVALUATION -> RECONCILIATION_POLICY_VERSION
        CanonicalEngineWorkType.POST_MERGE_DERIVED,
        CanonicalEngineWorkType.POLICY_REEVALUATION,
        -> null
    }

    private suspend fun enqueuePolicyReevaluationLocked(limit: Int): PolicyEnqueueResult {
        val storyIds = reader.stalePolicyStoryIds(
            fusionPolicyVersion = FUSION_POLICY_VERSION,
            primarySelectionPolicyVersion = PRIMARY_SELECTION_POLICY_VERSION,
            reconciliationPolicyVersion = RECONCILIATION_POLICY_VERSION,
            limit = limit,
        ).distinct().take(limit)
        var markedStories = 0
        var invariantFailures = 0
        storyIds.forEach { staleStoryId ->
            val storyId = try {
                identity.resolve(staleStoryId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: StoryIdentityInvariantException) {
                invariantFailures += 1
                return@forEach
            }
            val state = reader.policyState(storyId)
            if (state == null) {
                invariantFailures += 1
                return@forEach
            }
            if (state.hasFuturePolicyVersion()) {
                val parked = work.markDirty(
                    storyId = storyId,
                    type = CanonicalEngineWorkType.FUSION_REBUILD,
                    reason = CanonicalEngineWorkReasons.POLICY_VERSION_CHANGED,
                    requiredPolicyVersion = null,
                )
                work.blockInvariant(
                    parked,
                    CanonicalMaintenanceFailureCodes.UNSUPPORTED_PERSISTED_POLICY_VERSION,
                )
                markResolvedOwnerDegradedBestEffort(storyId)
                invariantFailures += 1
                return@forEach
            }
            val reconciliationStale = state.reconciliationPolicyVersions.any {
                it != RECONCILIATION_POLICY_VERSION
            }
            val fusionStale = state.fusionPolicyVersion != FUSION_POLICY_VERSION ||
                state.primarySelectionPolicyVersion != PRIMARY_SELECTION_POLICY_VERSION
            if (reconciliationStale) {
                work.markDirty(
                    storyId = storyId,
                    type = CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                    reason = CanonicalEngineWorkReasons.POLICY_VERSION_CHANGED,
                    requiredPolicyVersion = RECONCILIATION_POLICY_VERSION,
                )
            }
            if (fusionStale) {
                work.markDirty(
                    storyId = storyId,
                    type = CanonicalEngineWorkType.FUSION_REBUILD,
                    reason = CanonicalEngineWorkReasons.POLICY_VERSION_CHANGED,
                    requiredPolicyVersion = FUSION_POLICY_VERSION,
                )
            }
            if (reconciliationStale || fusionStale) markedStories += 1
        }
        return PolicyEnqueueResult(markedStories, invariantFailures)
    }

    private suspend fun enqueueChangedPendingCasesLocked(limit: Int): Int {
        var invariantFailures = 0
        reader.pendingReconciliationCases(limit).forEach { case ->
            when (val result = changedPendingCaseOwners(case)) {
                ChangedPendingCaseResult.InvariantFailure -> invariantFailures += 1
                ChangedPendingCaseResult.NoChange -> Unit
                is ChangedPendingCaseResult.Owners -> result.storyIds.forEach { storyId ->
                    work.markDirty(
                        storyId = storyId,
                        type = CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                        reason = CanonicalEngineWorkReasons.EVIDENCE_REVISION_CHANGED,
                        requiredPolicyVersion = RECONCILIATION_POLICY_VERSION,
                    )
                }
            }
        }
        return invariantFailures
    }

    private suspend fun changedPendingCaseOwners(
        case: ReconciliationMaintenanceCase,
    ): ChangedPendingCaseResult {
        val current = try {
            reconciliation.isEvidenceFingerprintCurrent(case)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: StoryIdentityInvariantException) {
            return ChangedPendingCaseResult.InvariantFailure
        }
        return if (current) {
            ChangedPendingCaseResult.NoChange
        } else {
            try {
                ChangedPendingCaseResult.Owners(
                    linkedSetOf(identity.resolve(case.leftStoryId), identity.resolve(case.rightStoryId)),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: StoryIdentityInvariantException) {
                ChangedPendingCaseResult.InvariantFailure
            }
        }
    }

    private sealed interface ChangedPendingCaseResult {
        data object NoChange : ChangedPendingCaseResult
        data object InvariantFailure : ChangedPendingCaseResult
        data class Owners(val storyIds: Set<StoryId>) : ChangedPendingCaseResult
    }

    private fun CanonicalMaintenancePolicyState.hasFuturePolicyVersion(): Boolean =
        fusionPolicyVersion?.let { it > FUSION_POLICY_VERSION } == true ||
            primarySelectionPolicyVersion?.let { it > PRIMARY_SELECTION_POLICY_VERSION } == true ||
            reconciliationPolicyVersions.any { it > RECONCILIATION_POLICY_VERSION }

    private fun recordInvariantViolation(issue: CanonicalMaintenanceInvariantIssue) {
        diagnostics.record(
            CanonicalDecisionTrace(
                kind = CanonicalTraceKind.INVARIANT_VIOLATION,
                storyIds = buildSet {
                    issue.storyId?.let(::add)
                    addAll(issue.relatedStoryIds)
                },
                sourceKeys = issue.sourceKeys,
                reasonCodes = listOf(issue.code),
                field = issue.field,
            ),
        )
    }

    private suspend fun markResolvedOwnerDegradedBestEffort(storyId: StoryId) {
        val owner = try {
            identity.resolve(storyId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return
        }
        try {
            healthMarker.markDegraded(owner)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Durable redirect evidence remains available for the next safety pass.
        }
    }
    private companion object {
        val RECOVERABLE_POLICY_BLOCK_CODES = setOf(
            CanonicalMaintenanceFailureCodes.UNSUPPORTED_REQUIRED_POLICY_VERSION,
            CanonicalMaintenanceFailureCodes.UNSUPPORTED_PERSISTED_POLICY_VERSION,
        )
    }

    private data class PolicyEnqueueResult(
        val markedStories: Int,
        val invariantFailures: Int,
    )

}
