package app.openstory.catalog.orchestration

import app.openstory.common.id.StoryId

object CanonicalEngineWorkReasons {
    const val SOURCE_SUMMARY_CHANGED = "source_summary_changed"
    const val SOURCE_FULL_CHANGED = "source_full_changed"
    const val SOURCE_AVAILABILITY_CHANGED = "source_availability_changed"
    const val SOURCE_LINKED = "source_linked"
    const val SOURCE_UNLINKED = "source_unlinked"
    const val SOURCE_PREFERENCE_CHANGED = "source_preference_changed"
    const val REVIEW_RESOLVED = "review_resolved"
    const val STORY_MERGED = "story_merged"
    const val POLICY_VERSION_CHANGED = "policy_version_changed"
    const val RETRY = "retry"
}

enum class CanonicalEngineWorkType {
    FUSION_REBUILD,
    RECONCILIATION_REEVALUATION,
    POST_MERGE_DERIVED,
    POLICY_REEVALUATION,
}

data class CanonicalEngineWorkItem(
    val storyId: StoryId,
    val type: CanonicalEngineWorkType,
    val reason: String,
    val requiredPolicyVersion: Int?,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long,
    val lastFailureCode: String?,
) {
    init {
        require(reason.isNotBlank())
        require(requiredPolicyVersion == null || requiredPolicyVersion > 0)
        require(attemptCount >= 0)
        require(nextAttemptAtEpochMillis >= 0L)
        require(lastFailureCode == null || lastFailureCode.isNotBlank())
    }
}

interface CanonicalEngineWorkRepository {
    suspend fun markDirty(
        storyId: StoryId,
        type: CanonicalEngineWorkType,
        reason: String,
        requiredPolicyVersion: Int? = null,
    )

    suspend fun claimReady(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkItem>
    suspend fun complete(item: CanonicalEngineWorkItem)
    suspend fun retry(item: CanonicalEngineWorkItem, failureCode: String, nextAttemptAtEpochMillis: Long)
    suspend fun supersede(storyId: StoryId, type: CanonicalEngineWorkType)
}
