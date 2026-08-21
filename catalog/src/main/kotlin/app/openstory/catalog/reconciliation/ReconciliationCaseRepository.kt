package app.openstory.catalog.reconciliation

import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow

enum class ReconciliationCaseStatus {
    PENDING,
    RESOLVED_MERGED,
    RESOLVED_SEPARATE,
    SUPERSEDED,
}

enum class ReconciliationResolutionOrigin { ENGINE, USER }

data class ReconciliationCase(
    val id: String,
    val key: ReconciliationCaseKey,
    val status: ReconciliationCaseStatus,
    val assessment: ReconciliationAssessment,
    val evidenceFingerprint: String,
    val policyVersion: Int,
    val resolutionOrigin: ReconciliationResolutionOrigin?,
    val contextualPromptSuppressedUntilEpochMillis: Long?,
    val revision: Long,
) {
    init {
        require(evidenceFingerprint.isNotBlank())
        require(policyVersion > 0)
        require(revision > 0L)
        require(contextualPromptSuppressedUntilEpochMillis == null || contextualPromptSuppressedUntilEpochMillis >= 0L)
    }
}

interface ReconciliationCaseRepository {
    fun observePending(): Flow<List<ReconciliationCase>>
    fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>>
    suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase?
    suspend fun recordAssessment(
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCase?
    suspend fun resolveSeparate(
        caseId: String,
        expectedRevision: Long,
        origin: ReconciliationResolutionOrigin,
        resolvedAtEpochMillis: Long,
    ): Boolean
    suspend fun defer(
        caseId: String,
        expectedRevision: Long,
        suppressUntilEpochMillis: Long,
    ): Boolean
}
