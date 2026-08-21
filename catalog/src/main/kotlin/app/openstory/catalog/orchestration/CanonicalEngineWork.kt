package app.openstory.catalog.orchestration

import app.openstory.common.id.StoryId

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
