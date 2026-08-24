package app.openstory.catalog.orchestration

import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.diagnostics.CanonicalDiagnostics
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.reconciliation.ReconciliationMaintenanceCase
import app.openstory.common.id.StoryId
import javax.inject.Inject

/**
 * Read-only persistence facts used by the maintenance engine. Implementations may use bounded SQL
 * queries, but semantic policy decisions remain in :catalog.
 */
interface CanonicalEngineMaintenanceReader {
    suspend fun stalePolicyStoryIds(
        fusionPolicyVersion: Int,
        primarySelectionPolicyVersion: Int,
        reconciliationPolicyVersion: Int,
        limit: Int,
    ): List<StoryId>

    suspend fun policyState(storyId: StoryId): CanonicalMaintenancePolicyState?

    suspend fun pendingReconciliationCases(limit: Int): List<ReconciliationMaintenanceCase>

    suspend fun redirectInconsistencies(limit: Int): List<CanonicalMaintenanceInvariantIssue>

    suspend fun invariantIssues(limit: Int): List<CanonicalMaintenanceInvariantIssue> =
        redirectInconsistencies(limit)
}

data class CanonicalMaintenancePolicyState(
    val fusionPolicyVersion: Int?,
    val primarySelectionPolicyVersion: Int?,
    val reconciliationPolicyVersions: Set<Int>,
) {
    init {
        // Maintenance reads persisted facts, including legacy/unversioned rows (0), so the
        // safety pass can repair them with the current positive policy version. Executable
        // policy configs and required work versions remain strictly > 0.
        require(fusionPolicyVersion == null || fusionPolicyVersion >= 0)
        require(primarySelectionPolicyVersion == null || primarySelectionPolicyVersion >= 0)
        require(reconciliationPolicyVersions.all { it >= 0 })
    }
}

data class CanonicalMaintenanceInvariantIssue(
    val storyId: StoryId?,
    val code: String,
    val relatedStoryIds: Set<StoryId> = emptySet(),
    val sourceKeys: Set<SourceKey> = emptySet(),
    val field: CanonicalFieldKey? = null,
) {
    init {
        require(code.isNotBlank())
    }
}

fun interface CanonicalMaintenanceHealthMarker {
    suspend fun markDegraded(storyId: StoryId)
}

class CanonicalMaintenanceObservability @Inject constructor(
    val health: CanonicalMaintenanceHealthMarker,
    val diagnostics: CanonicalDiagnostics,
)

sealed interface PostMergeDerivedWorkResult {
    data object Dispatched : PostMergeDerivedWorkResult

    data class Failed(
        val code: String,
        val retryable: Boolean,
    ) : PostMergeDerivedWorkResult {
        init {
            require(code.isNotBlank())
        }
    }
}

data class PostMergeDerivedRequirements(
    val reaggregateChapters: Boolean,
    val recomputeMappings: Boolean,
    val refreshChapterSync: Boolean,
) {
    init {
        require(reaggregateChapters || recomputeMappings || refreshChapterSync) {
            "Post-merge derived work must require at least one operation"
        }
    }
}

fun interface PostMergeDerivedWorkDispatcher {
    suspend fun dispatch(
        storyId: StoryId,
        requirements: PostMergeDerivedRequirements,
    ): PostMergeDerivedWorkResult
}


internal object CanonicalMaintenanceFailureCodes {
    const val UNSUPPORTED_REQUIRED_POLICY_VERSION =
        "canonical.maintenance.unsupported_required_policy_version"
    const val UNSUPPORTED_PERSISTED_POLICY_VERSION =
        "canonical.maintenance.unsupported_persisted_policy_version"
}

data class CanonicalMaintenanceReport(
    val processed: Int,
    val succeeded: Int,
    val retried: Int,
    val failedInvariant: Int,
    val nextAttemptAtEpochMillis: Long?,
) {
    init {
        require(processed >= 0)
        require(succeeded >= 0)
        require(retried >= 0)
        require(failedInvariant >= 0)
        require(succeeded + retried <= processed)
        require(nextAttemptAtEpochMillis == null || nextAttemptAtEpochMillis >= 0L)
    }
}
