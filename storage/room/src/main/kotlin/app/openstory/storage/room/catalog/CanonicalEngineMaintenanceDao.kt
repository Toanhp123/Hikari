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
        "SELECT e.plugin_id AS pluginId, e.source_id AS sourceId, e.story_id AS storyId, " +
            "r.canonical_story_id AS canonicalStoryId " +
            "FROM catalog_entries e LEFT JOIN story_canonical_state s ON s.story_id = e.story_id " +
            "LEFT JOIN story_redirects r ON r.retired_story_id = e.story_id " +
            "WHERE s.story_id IS NULL OR r.retired_story_id IS NOT NULL " +
            "ORDER BY e.plugin_id, e.source_id LIMIT :limit",
    )
    suspend fun invalidSourceOwners(limit: Int): List<InvalidSourceOwnerMaintenanceRow>

    @Query(
        "SELECT e.plugin_id AS pluginId, e.source_id AS sourceId, COUNT(DISTINCT e.story_id) AS ownerCount " +
            "FROM catalog_entries e GROUP BY e.plugin_id, e.source_id " +
            "HAVING COUNT(DISTINCT e.story_id) > 1 " +
            "ORDER BY e.plugin_id, e.source_id LIMIT :limit",
    )
    suspend fun duplicateSourceOwnership(limit: Int): List<DuplicateSourceOwnershipMaintenanceRow>

    @Query(
        "SELECT g.story_id AS storyId, p.field_key AS fieldKey, " +
            "p.contributor_plugin_id AS pluginId, p.contributor_source_id AS sourceId " +
            "FROM story_canonical_state s JOIN canonical_generations g " +
            "ON g.generation_id = s.active_generation_id JOIN canonical_field_provenance p " +
            "ON p.generation_id = g.generation_id LEFT JOIN catalog_entries e " +
            "ON e.plugin_id = p.contributor_plugin_id AND e.source_id = p.contributor_source_id " +
            "WHERE e.story_id IS NULL OR e.story_id != g.story_id " +
            "ORDER BY g.story_id, p.field_key, p.contributor_plugin_id, p.contributor_source_id LIMIT :limit",
    )
    suspend fun provenanceOutsideStorySources(limit: Int): List<ProvenanceOutsideStoryMaintenanceRow>

    @Query(
        "SELECT w.story_id AS retiredStoryId, r.canonical_story_id AS canonicalStoryId " +
            "FROM canonical_engine_work w JOIN story_redirects r ON r.retired_story_id = w.story_id " +
            "ORDER BY w.story_id, w.work_type LIMIT :limit",
    )
    suspend fun orphanedRedirectWork(limit: Int): List<OrphanedRedirectWorkMaintenanceRow>

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

internal data class InvalidSourceOwnerMaintenanceRow(
    val pluginId: String,
    val sourceId: String,
    val storyId: String,
    val canonicalStoryId: String?,
)

internal data class DuplicateSourceOwnershipMaintenanceRow(
    val pluginId: String,
    val sourceId: String,
    val ownerCount: Int,
)

internal data class ProvenanceOutsideStoryMaintenanceRow(
    val storyId: String,
    val fieldKey: String,
    val pluginId: String,
    val sourceId: String,
)

internal data class OrphanedRedirectWorkMaintenanceRow(
    val retiredStoryId: String,
    val canonicalStoryId: String,
)
