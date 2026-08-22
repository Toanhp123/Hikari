package app.openstory.storage.room.catalog

import androidx.room.Dao
import androidx.room.Query

@Dao
internal interface CanonicalEngineMaintenanceDao {
    @Query(
        "SELECT r.retired_story_id AS retiredStoryId, r.canonical_story_id AS canonicalStoryId " +
            "FROM story_redirects r LEFT JOIN stories target " +
            "ON target.story_id = r.canonical_story_id LEFT JOIN story_redirects target_redirect " +
            "ON target_redirect.retired_story_id = r.canonical_story_id " +
            "WHERE target.story_id IS NULL OR target_redirect.retired_story_id IS NOT NULL " +
            "OR r.retired_story_id = r.canonical_story_id " +
            "ORDER BY r.retired_story_id LIMIT :limit",
    )
    suspend fun redirectInconsistencies(limit: Int): List<RedirectMaintenanceRow>

    @Query(
        "SELECT g.fusion_policy_version AS fusionPolicyVersion, " +
            "g.primary_policy_version AS primarySelectionPolicyVersion " +
            "FROM story_canonical_state s LEFT JOIN canonical_generations g " +
            "ON g.generation_id = s.active_generation_id WHERE s.story_id = :storyId",
    )
    suspend fun generationPolicy(storyId: String): CanonicalGenerationPolicyRow?

    @Query(
        "SELECT DISTINCT r.policy_version FROM reconciliation_cases c " +
            "JOIN reconciliation_case_revisions r ON r.revision_id = c.current_revision_id " +
            "WHERE (c.left_story_id = :storyId OR c.right_story_id = :storyId) " +
            "AND c.status IN ('PENDING', 'RESOLVED_SEPARATE') ORDER BY r.policy_version",
    )
    suspend fun reconciliationPolicyVersions(storyId: String): List<Int>

    @Query(
        "SELECT story_id FROM (" +
            "SELECT s.story_id AS story_id FROM story_canonical_state s " +
            "LEFT JOIN canonical_generations g ON g.generation_id = s.active_generation_id " +
            "WHERE s.active_generation_id IS NULL OR g.generation_id IS NULL " +
            "OR g.fusion_policy_version != :fusionPolicyVersion " +
            "OR g.primary_policy_version != :primarySelectionPolicyVersion " +
            "UNION SELECT c.left_story_id AS story_id FROM reconciliation_cases c " +
            "JOIN reconciliation_case_revisions r ON r.revision_id = c.current_revision_id " +
            "WHERE c.status IN ('PENDING', 'RESOLVED_SEPARATE') " +
            "AND r.policy_version != :reconciliationPolicyVersion " +
            "UNION SELECT c.right_story_id AS story_id FROM reconciliation_cases c " +
            "JOIN reconciliation_case_revisions r ON r.revision_id = c.current_revision_id " +
            "WHERE c.status IN ('PENDING', 'RESOLVED_SEPARATE') " +
            "AND r.policy_version != :reconciliationPolicyVersion" +
            ") ORDER BY story_id LIMIT :limit",
    )
    suspend fun stalePolicyStoryIds(
        fusionPolicyVersion: Int,
        primarySelectionPolicyVersion: Int,
        reconciliationPolicyVersion: Int,
        limit: Int,
    ): List<String>

    @Query(
        "SELECT c.case_id AS caseId, c.left_story_id AS leftStoryId, " +
            "c.right_story_id AS rightStoryId, r.identity_fingerprint AS evidenceFingerprint, " +
            "r.policy_version AS policyVersion FROM reconciliation_cases c " +
            "JOIN reconciliation_case_revisions r ON r.revision_id = c.current_revision_id " +
            "WHERE c.status = 'PENDING' ORDER BY c.updated_at_epoch_millis DESC, c.case_id ASC LIMIT :limit",
    )
    suspend fun pendingCases(limit: Int): List<PendingReconciliationMaintenanceRow>
}

internal data class CanonicalGenerationPolicyRow(
    val fusionPolicyVersion: Int?,
    val primarySelectionPolicyVersion: Int?,
)

internal data class RedirectMaintenanceRow(
    val retiredStoryId: String,
    val canonicalStoryId: String,
)

internal data class PendingReconciliationMaintenanceRow(
    val caseId: String,
    val leftStoryId: String,
    val rightStoryId: String,
    val evidenceFingerprint: String,
    val policyVersion: Int,
)
