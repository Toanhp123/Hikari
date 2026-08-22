package app.openstory.catalog.orchestration

import app.openstory.catalog.fusion.CanonicalFusionReason
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.fusion.CanonicalGenerationRebuilder
import app.openstory.catalog.fusion.FUSION_POLICY_VERSION
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.reconciliation.CatalogReconciliationRunner
import app.openstory.catalog.reconciliation.RECONCILIATION_POLICY_VERSION
import app.openstory.catalog.reconciliation.ReconciliationRunResult
import app.openstory.common.id.StoryId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

interface CanonicalEngineEventSink {
    suspend fun onEvidenceChanged(change: CatalogEvidenceChange)
    suspend fun onSourceLinked(storyId: StoryId, sourceKey: SourceKey)
    suspend fun onSourceUnlinked(storyId: StoryId, sourceKey: SourceKey)
    suspend fun onSourcePreferenceChanged(storyId: StoryId): CanonicalFusionResult
    suspend fun onStoryMerged(storyId: StoryId): CanonicalFusionResult
}

@Singleton
class CanonicalEngineOrchestrator @Inject constructor(
    private val reconciliation: CatalogReconciliationRunner,
    private val fusion: CanonicalGenerationRebuilder,
    private val work: CanonicalEngineWorkRepository,
    private val identity: StoryIdentityRepository,
    private val scheduler: CanonicalEngineWorkScheduler = NoOpCanonicalEngineWorkScheduler,
) : CanonicalEngineEventSink {
    override suspend fun onEvidenceChanged(change: CatalogEvidenceChange) = runCommittedEvent {
        val reconciliationResult = if (change.identityFingerprintChanged) {
            reconcileOrSchedule(change.sourceKey, change.storyId, change.workReason())
        } else {
            null
        }
        val merged = reconciliationResult as? ReconciliationRunResult.AutoMergeApplied
        if (change.fusionFingerprintChanged || change.availabilityChanged || merged != null) {
            val owner = identity.resolve(merged?.survivorStoryId ?: change.storyId)
            val reason = if (merged != null) CanonicalEngineWorkReasons.STORY_MERGED else change.workReason()
            val fusionReason = when {
                merged != null -> CanonicalFusionReason.POST_MERGE
                change.availabilityChanged && !change.fusionFingerprintChanged ->
                    CanonicalFusionReason.SOURCE_AVAILABILITY_CHANGED
                else -> CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED
            }
            rebuildFusion(owner, reason, fusionReason)
        }
    }

    override suspend fun onSourceLinked(storyId: StoryId, sourceKey: SourceKey) = runCommittedEvent {
        val reconciliationResult = reconcileOrSchedule(
            sourceKey,
            storyId,
            CanonicalEngineWorkReasons.SOURCE_LINKED,
        )
        val merged = reconciliationResult as? ReconciliationRunResult.AutoMergeApplied
        val owner = identity.resolve(merged?.survivorStoryId ?: storyId)
        rebuildFusion(
            owner,
            if (merged != null) CanonicalEngineWorkReasons.STORY_MERGED else CanonicalEngineWorkReasons.SOURCE_LINKED,
            if (merged != null) CanonicalFusionReason.POST_MERGE else CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED,
        )
    }

    override suspend fun onSourceUnlinked(storyId: StoryId, sourceKey: SourceKey) = runCommittedEvent {
        invalidateCandidateIndexBestEffort()
        val owner = identity.resolve(storyId)
        markDirtyBestEffort(
            storyId = owner,
            type = CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
            reason = CanonicalEngineWorkReasons.SOURCE_UNLINKED,
            requiredPolicyVersion = RECONCILIATION_POLICY_VERSION,
        )
        rebuildFusion(
            owner,
            CanonicalEngineWorkReasons.SOURCE_UNLINKED,
            CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED,
        )
    }

    override suspend fun onSourcePreferenceChanged(storyId: StoryId): CanonicalFusionResult =
        runFusionEvent(storyId) {
            val owner = identity.resolve(storyId)
            rebuildFusion(
                owner,
                CanonicalEngineWorkReasons.SOURCE_PREFERENCE_CHANGED,
                CanonicalFusionReason.SOURCE_PREFERENCE_CHANGED,
            )
        }

    override suspend fun onStoryMerged(storyId: StoryId): CanonicalFusionResult =
        runFusionEvent(storyId) {
            invalidateCandidateIndexBestEffort()
            val owner = identity.resolve(storyId)
            rebuildFusion(
                owner,
                CanonicalEngineWorkReasons.STORY_MERGED,
                CanonicalFusionReason.POST_MERGE,
            )
        }

    private suspend fun reconcileOrSchedule(
        sourceKey: SourceKey,
        storyId: StoryId,
        reason: String,
    ): ReconciliationRunResult? = try {
        reconciliation.reconcile(sourceKey).also { result ->
            if (result is ReconciliationRunResult.ReevaluationScheduled) {
                scheduleDrainBestEffort()
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        runCommittedEvent {
            val owner = identity.resolve(storyId)
            markDirtyBestEffort(
                storyId = owner,
                type = CanonicalEngineWorkType.RECONCILIATION_REEVALUATION,
                reason = reason,
                requiredPolicyVersion = RECONCILIATION_POLICY_VERSION,
            )
        }
        null
    }

    private suspend fun rebuildFusion(
        storyId: StoryId,
        workReason: String,
        fusionReason: CanonicalFusionReason,
    ): CanonicalFusionResult {
        val durable = markDirtyBestEffort(
            storyId = storyId,
            type = CanonicalEngineWorkType.FUSION_REBUILD,
            reason = workReason,
            requiredPolicyVersion = FUSION_POLICY_VERSION,
            scheduleDrain = false,
        )
        val result = try {
            fusion.rebuild(storyId, fusionReason)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            CanonicalFusionResult.Failed(
                storyId = storyId,
                code = FUSION_EXCEPTION_CODE,
                retryable = true,
            )
        }
        val completed = when (result) {
            is CanonicalFusionResult.Promoted,
            is CanonicalFusionResult.Unchanged,
            is CanonicalFusionResult.Preparing,
            -> durable?.let { completeBestEffort(it) } ?: true

            is CanonicalFusionResult.Failed -> false
        }
        if (!completed || workReason == CanonicalEngineWorkReasons.STORY_MERGED) {
            scheduleDrainBestEffort()
        }
        return result
    }

    private suspend fun markDirtyBestEffort(
        storyId: StoryId,
        type: CanonicalEngineWorkType,
        reason: String,
        requiredPolicyVersion: Int?,
        scheduleDrain: Boolean = true,
    ): CanonicalEngineWorkItem? = try {
        work.markDirty(storyId, type, reason, requiredPolicyVersion).also {
            if (scheduleDrain) scheduleDrainBestEffort()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        // Foreground engine work can still succeed; a future fact change can recreate the durable row.
        null
    }

    private suspend fun completeBestEffort(item: CanonicalEngineWorkItem): Boolean = try {
        work.complete(item)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        false
    }

    private fun scheduleDrainBestEffort() {
        try {
            scheduler.scheduleDrain()
        } catch (_: RuntimeException) {
            // Durable work is already committed; app start/daily safety can recover scheduling.
        }
    }

    private suspend fun invalidateCandidateIndexBestEffort() {
        try {
            reconciliation.invalidateCandidateIndex()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A later reconciliation run rebuilds the index from durable source records.
        }
    }

    private suspend fun runCommittedEvent(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // The provider/repository commit already succeeded; orchestration must not rewrite that result.
        }
    }

    private suspend fun runFusionEvent(
        storyId: StoryId,
        block: suspend () -> CanonicalFusionResult,
    ): CanonicalFusionResult = try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        CanonicalFusionResult.Failed(storyId, ORCHESTRATION_EXCEPTION_CODE, retryable = true)
    }

    private fun CatalogEvidenceChange.workReason(): String = when {
        availabilityChanged && !identityFingerprintChanged && !fusionFingerprintChanged ->
            CanonicalEngineWorkReasons.SOURCE_AVAILABILITY_CHANGED
        level == CatalogEvidenceLevel.FULL -> CanonicalEngineWorkReasons.SOURCE_FULL_CHANGED
        else -> CanonicalEngineWorkReasons.SOURCE_SUMMARY_CHANGED
    }

    private companion object {
        const val FUSION_EXCEPTION_CODE = "canonical.orchestration.fusion_exception"
        const val ORCHESTRATION_EXCEPTION_CODE = "canonical.orchestration.exception"
    }
}
