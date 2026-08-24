package app.openstory.storage.room.catalog

import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.orchestration.CanonicalEngineMaintenanceReader
import app.openstory.catalog.orchestration.CanonicalMaintenanceInvariantIssue
import app.openstory.catalog.orchestration.CanonicalMaintenancePolicyState
import app.openstory.catalog.reconciliation.ReconciliationMaintenanceCase
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase

class RoomCanonicalEngineMaintenanceReader internal constructor(
    private val dao: CanonicalEngineMaintenanceDao,
) : CanonicalEngineMaintenanceReader {
    constructor(database: OpenStoryDatabase) : this(database.canonicalEngineMaintenanceDao())

    override suspend fun stalePolicyStoryIds(
        fusionPolicyVersion: Int,
        primarySelectionPolicyVersion: Int,
        reconciliationPolicyVersion: Int,
        limit: Int,
    ): List<StoryId> {
        require(fusionPolicyVersion > 0)
        require(primarySelectionPolicyVersion > 0)
        require(reconciliationPolicyVersion > 0)
        require(limit > 0)
        return dao.stalePolicyStoryIds(
            fusionPolicyVersion,
            primarySelectionPolicyVersion,
            reconciliationPolicyVersion,
            limit,
        ).map(::StoryId)
    }

    override suspend fun policyState(storyId: StoryId): CanonicalMaintenancePolicyState? {
        val generation = dao.generationPolicy(storyId.value) ?: return null
        return CanonicalMaintenancePolicyState(
            fusionPolicyVersion = generation.fusionPolicyVersion,
            primarySelectionPolicyVersion = generation.primarySelectionPolicyVersion,
            reconciliationPolicyVersions = dao.reconciliationPolicyVersions(storyId.value).toSet(),
        )
    }

    override suspend fun pendingReconciliationCases(limit: Int): List<ReconciliationMaintenanceCase> {
        require(limit > 0)
        return dao.pendingCases(limit).map { row ->
            ReconciliationMaintenanceCase(
                caseId = row.caseId,
                leftStoryId = StoryId(row.leftStoryId),
                rightStoryId = StoryId(row.rightStoryId),
                evidenceFingerprint = row.evidenceFingerprint,
                policyVersion = row.policyVersion,
            )
        }
    }

    override suspend fun redirectInconsistencies(limit: Int): List<CanonicalMaintenanceInvariantIssue> {
        require(limit > 0)
        return dao.redirectInconsistencies(limit).map { row ->
            CanonicalMaintenanceInvariantIssue(
                storyId = StoryId(row.canonicalStoryId),
                code = REDIRECT_TARGET_INVALID,
                relatedStoryIds = setOf(StoryId(row.retiredStoryId)),
            )
        }
    }

    override suspend fun invariantIssues(limit: Int): List<CanonicalMaintenanceInvariantIssue> {
        require(limit > 0)
        val issues = mutableListOf<CanonicalMaintenanceInvariantIssue>()
        fun remaining(): Int = (limit - issues.size).coerceAtLeast(0)

        issues += redirectInconsistencies(remaining()).take(remaining())
        if (remaining() > 0) {
            issues += dao.invalidSourceOwners(remaining()).map { row ->
                CanonicalMaintenanceInvariantIssue(
                    storyId = row.canonicalStoryId?.let(::StoryId) ?: StoryId(row.storyId),
                    code = SOURCE_OWNER_INVALID,
                    relatedStoryIds = row.canonicalStoryId?.let { setOf(StoryId(row.storyId)) }.orEmpty(),
                    sourceKeys = setOf(SourceKey(PluginId(row.pluginId), row.sourceId)),
                )
            }
        }
        if (remaining() > 0) {
            issues += dao.duplicateSourceOwnership(remaining()).map { row ->
                CanonicalMaintenanceInvariantIssue(
                    storyId = null,
                    code = DUPLICATE_SOURCE_OWNERSHIP,
                    sourceKeys = setOf(SourceKey(PluginId(row.pluginId), row.sourceId)),
                )
            }
        }
        if (remaining() > 0) {
            issues += dao.provenanceOutsideStorySources(remaining()).map { row ->
                CanonicalMaintenanceInvariantIssue(
                    storyId = StoryId(row.storyId),
                    code = PROVENANCE_SOURCE_OUTSIDE_STORY,
                    sourceKeys = setOf(SourceKey(PluginId(row.pluginId), row.sourceId)),
                    field = runCatching { CanonicalFieldKey.valueOf(row.fieldKey) }.getOrNull(),
                )
            }
        }
        if (remaining() > 0) {
            issues += dao.orphanedRedirectWork(remaining()).map { row ->
                CanonicalMaintenanceInvariantIssue(
                    storyId = StoryId(row.canonicalStoryId),
                    code = ORPHANED_REDIRECT_WORK,
                    relatedStoryIds = setOf(StoryId(row.retiredStoryId)),
                )
            }
        }
        return issues.take(limit)
    }

    private companion object {
        const val REDIRECT_TARGET_INVALID = "canonical.invariant.redirect_target_invalid"
        const val SOURCE_OWNER_INVALID = "canonical.invariant.source_owner_invalid"
        const val DUPLICATE_SOURCE_OWNERSHIP = "canonical.invariant.duplicate_source_ownership"
        const val PROVENANCE_SOURCE_OUTSIDE_STORY = "canonical.invariant.provenance_source_outside_story"
        const val ORPHANED_REDIRECT_WORK = "canonical.invariant.orphaned_redirect_work"
    }
}
