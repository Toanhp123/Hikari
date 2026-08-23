package app.openstory.storage.room.catalog

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal interface CanonicalCatalogDao {
    @Query("SELECT * FROM story_canonical_state WHERE story_id = :storyId")
    suspend fun canonicalState(storyId: String): StoryCanonicalStateEntity?

    @Query("SELECT * FROM story_canonical_state WHERE story_id = :storyId")
    fun observeCanonicalState(storyId: String): Flow<StoryCanonicalStateEntity?>

    @Query("SELECT * FROM story_canonical_state ORDER BY story_id")
    fun observeCanonicalStates(): Flow<List<StoryCanonicalStateEntity>>

    @Query("SELECT * FROM story_canonical_state WHERE story_id IN (:storyIds) ORDER BY story_id")
    fun observeCanonicalStates(storyIds: List<String>): Flow<List<StoryCanonicalStateEntity>>

    suspend fun upsertCanonicalState(state: StoryCanonicalStateEntity) {
        val hasPinnedPair = state.pinnedPluginId != null && state.pinnedSourceId != null
        val hasNoPin = state.pinnedPluginId == null && state.pinnedSourceId == null
        require(
            (state.preferenceMode == "AUTO" && hasNoPin) ||
                (state.preferenceMode == "PINNED" && hasPinnedPair),
        ) { "Canonical source preference must be AUTO without a pin or PINNED with a complete SourceKey" }
        upsertCanonicalStateRow(state)
    }

    @Upsert
    suspend fun upsertCanonicalStateRow(state: StoryCanonicalStateEntity)

    @Upsert
    suspend fun upsertGeneration(generation: CanonicalGenerationEntity)

    @Query("SELECT * FROM canonical_generations WHERE generation_id = :generationId")
    suspend fun generation(generationId: String): CanonicalGenerationEntity?

    @Query("SELECT * FROM canonical_generations WHERE generation_id IN (:generationIds)")
    suspend fun generations(generationIds: Collection<String>): List<CanonicalGenerationEntity>

    @Query(
        "SELECT generation_id FROM canonical_generations " +
            "WHERE story_id = :storyId AND valid = 1 ORDER BY created_at_epoch_millis DESC, generation_id DESC",
    )
    suspend fun validGenerationIds(storyId: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProvenance(rows: List<CanonicalFieldProvenanceEntity>)

    @Query("DELETE FROM canonical_field_provenance WHERE generation_id = :generationId")
    suspend fun deleteProvenance(generationId: String)

    @Query(
        "SELECT * FROM canonical_field_provenance WHERE generation_id = :generationId " +
            "ORDER BY field_key, contributor_plugin_id, contributor_source_id",
    )
    suspend fun provenance(generationId: String): List<CanonicalFieldProvenanceEntity>

    @Query(
        "SELECT * FROM canonical_field_provenance WHERE generation_id IN (:generationIds) " +
            "ORDER BY generation_id, field_key, contributor_plugin_id, contributor_source_id",
    )
    suspend fun provenanceForGenerations(generationIds: Collection<String>): List<CanonicalFieldProvenanceEntity>

    @Query("UPDATE canonical_generations SET valid = 1 WHERE generation_id = :generationId")
    suspend fun markGenerationValid(generationId: String)

    @Query(
        "UPDATE story_canonical_state SET active_generation_id = :generationId, health = :health " +
            "WHERE story_id = :storyId",
    )
    suspend fun activateGeneration(storyId: String, generationId: String, health: String): Int

    @Query("UPDATE story_canonical_state SET health = :health WHERE story_id = :storyId")
    suspend fun updateHealth(storyId: String, health: String): Int

    @Query(
        "DELETE FROM canonical_generations WHERE story_id = :storyId AND valid = 1 " +
            "AND generation_id NOT IN (SELECT generation_id FROM canonical_generations " +
            "WHERE story_id = :storyId AND valid = 1 " +
            "ORDER BY created_at_epoch_millis DESC, generation_id DESC LIMIT 2)",
    )
    suspend fun deleteObsoleteSuccessfulGenerations(storyId: String)

    @Query("SELECT * FROM story_redirects WHERE retired_story_id = :storyId")
    suspend fun redirect(storyId: String): StoryRedirectEntity?

    @Query("SELECT * FROM story_redirects ORDER BY retired_story_id")
    suspend fun redirects(): List<StoryRedirectEntity>

    @Query("SELECT * FROM story_redirects ORDER BY retired_story_id")
    fun observeRedirects(): Flow<List<StoryRedirectEntity>>

    suspend fun upsertRedirect(redirect: StoryRedirectEntity) {
        require(redirect.retiredStoryId != redirect.canonicalStoryId) {
            "A retired StoryId cannot redirect to itself"
        }
        upsertRedirectRow(redirect)
    }

    @Upsert
    suspend fun upsertRedirectRow(redirect: StoryRedirectEntity)

    @Upsert
    suspend fun upsertMergeEvent(event: StoryMergeEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMergeEvent(event: StoryMergeEventEntity)

    @Query(
        "UPDATE story_redirects SET canonical_story_id = :survivorStoryId " +
            "WHERE canonical_story_id = :retiredStoryId",
    )
    suspend fun flattenRedirectTargets(retiredStoryId: String, survivorStoryId: String): Int

    @Upsert
    suspend fun upsertWork(item: CanonicalEngineWorkEntity)

    @Query(
        "SELECT * FROM canonical_engine_work WHERE next_attempt_at_epoch_millis <= :nowEpochMillis " +
            "AND (lease_token IS NULL OR lease_expires_at_epoch_millis <= :nowEpochMillis) " +
            "ORDER BY CASE work_type WHEN 'RECONCILIATION_REEVALUATION' THEN 0 " +
            "WHEN 'FUSION_REBUILD' THEN 1 WHEN 'POLICY_REEVALUATION' THEN 2 ELSE 3 END ASC, " +
            "next_attempt_at_epoch_millis ASC, story_id ASC, work_type ASC LIMIT :limit",
    )
    suspend fun readyWork(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkEntity>

    @Query(
        "UPDATE canonical_engine_work SET lease_token = :leaseToken, " +
            "lease_expires_at_epoch_millis = :leaseExpiresAtEpochMillis " +
            "WHERE story_id = :storyId AND work_type = :workType " +
            "AND next_attempt_at_epoch_millis = :expectedNextAttemptAtEpochMillis " +
            "AND (lease_token IS NULL OR lease_expires_at_epoch_millis <= :nowEpochMillis)",
    )
    suspend fun claimWork(
        storyId: String,
        workType: String,
        expectedNextAttemptAtEpochMillis: Long,
        nowEpochMillis: Long,
        leaseToken: String,
        leaseExpiresAtEpochMillis: Long,
    ): Int

    @Query("SELECT * FROM canonical_engine_work WHERE lease_token = :leaseToken ORDER BY story_id, work_type")
    suspend fun workByLeaseToken(leaseToken: String): List<CanonicalEngineWorkEntity>

    @Query("SELECT * FROM canonical_engine_work WHERE story_id = :storyId AND work_type = :workType")
    suspend fun work(storyId: String, workType: String): CanonicalEngineWorkEntity?

    @Query("SELECT * FROM canonical_engine_work WHERE story_id = :storyId ORDER BY work_type")
    suspend fun workForStory(storyId: String): List<CanonicalEngineWorkEntity>

    @Query("SELECT * FROM canonical_engine_work WHERE story_id IN (:storyIds) ORDER BY story_id, work_type")
    suspend fun workForStories(storyIds: Collection<String>): List<CanonicalEngineWorkEntity>

    @Query(
        "SELECT * FROM canonical_engine_work WHERE next_attempt_at_epoch_millis = :blockedEpochMillis " +
            "AND last_error_code IN (:failureCodes) ORDER BY story_id, work_type LIMIT :limit",
    )
    suspend fun blockedWork(
        blockedEpochMillis: Long,
        failureCodes: List<String>,
        limit: Int,
    ): List<CanonicalEngineWorkEntity>

    @Query("DELETE FROM canonical_engine_work WHERE story_id = :storyId AND work_type = :workType")
    suspend fun deleteWork(storyId: String, workType: String)

    @Query(
        "SELECT MIN(CASE WHEN lease_token IS NOT NULL AND lease_expires_at_epoch_millis > :nowEpochMillis " +
            "THEN lease_expires_at_epoch_millis ELSE next_attempt_at_epoch_millis END) " +
            "FROM canonical_engine_work WHERE next_attempt_at_epoch_millis < :blockedEpochMillis",
    )
    suspend fun earliestRunnableWorkAttempt(blockedEpochMillis: Long, nowEpochMillis: Long): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOutbox(events: List<CatalogChangeOutboxEntity>): List<Long>

    @Query(
        "SELECT * FROM catalog_change_outbox " +
            "ORDER BY event_id ASC LIMIT :limit",
    )
    suspend fun pendingOutbox(limit: Int): List<CatalogChangeOutboxEntity>

    @Query("DELETE FROM catalog_change_outbox WHERE event_id <= :maxEventId")
    suspend fun deleteOutboxThrough(maxEventId: Long): Int

    suspend fun upsertReconciliationCase(case: ReconciliationCaseEntity) {
        val normalized = if (case.leftStoryId <= case.rightStoryId) {
            case
        } else {
            case.copy(leftStoryId = case.rightStoryId, rightStoryId = case.leftStoryId)
        }
        require(normalized.leftStoryId < normalized.rightStoryId)
        upsertReconciliationCaseRow(normalized)
    }

    @Upsert
    suspend fun upsertReconciliationCaseRow(case: ReconciliationCaseEntity)

    @Query("SELECT * FROM reconciliation_cases WHERE case_id = :caseId")
    suspend fun reconciliationCase(caseId: String): ReconciliationCaseEntity?

    @Query(
        "SELECT * FROM reconciliation_cases WHERE status = 'PENDING' " +
            "ORDER BY updated_at_epoch_millis DESC, case_id ASC",
    )
    fun observePendingReconciliationCases(): Flow<List<ReconciliationCaseEntity>>

    @Query(
        "SELECT * FROM reconciliation_cases WHERE left_story_id = :storyId OR right_story_id = :storyId " +
            "ORDER BY updated_at_epoch_millis DESC, case_id ASC",
    )
    fun observeReconciliationCasesForStory(storyId: String): Flow<List<ReconciliationCaseEntity>>

    @Query(
        "SELECT * FROM reconciliation_cases WHERE left_story_id = :leftStoryId AND right_story_id = :rightStoryId",
    )
    suspend fun reconciliationCase(leftStoryId: String, rightStoryId: String): ReconciliationCaseEntity?

    @Query(
        "SELECT * FROM reconciliation_cases WHERE left_story_id = :storyId OR right_story_id = :storyId " +
            "ORDER BY left_story_id, right_story_id, case_id",
    )
    suspend fun reconciliationCasesForStory(storyId: String): List<ReconciliationCaseEntity>

    @Upsert
    suspend fun insertReconciliationRevision(revision: ReconciliationCaseRevisionEntity)

    @Query(
        "SELECT * FROM reconciliation_case_revisions WHERE case_id = :caseId " +
            "ORDER BY evaluated_at_epoch_millis, revision_id",
    )
    suspend fun reconciliationRevisions(caseId: String): List<ReconciliationCaseRevisionEntity>

    @Query("SELECT COUNT(*) FROM reconciliation_case_revisions WHERE case_id = :caseId")
    suspend fun reconciliationRevisionCount(caseId: String): Long

    @Query("SELECT * FROM reconciliation_case_revisions WHERE revision_id = :revisionId")
    suspend fun reconciliationRevision(revisionId: String): ReconciliationCaseRevisionEntity?

    @Query("SELECT * FROM reconciliation_case_revisions WHERE revision_id IN (:revisionIds)")
    suspend fun reconciliationRevisionsByIds(
        revisionIds: Collection<String>,
    ): List<ReconciliationCaseRevisionEntity>

    @Query(
        "SELECT case_id, COUNT(*) AS revision_count FROM reconciliation_case_revisions " +
            "WHERE case_id IN (:caseIds) GROUP BY case_id",
    )
    suspend fun reconciliationRevisionCounts(
        caseIds: Collection<String>,
    ): List<ReconciliationRevisionCountRow>

    @Query("UPDATE reconciliation_case_revisions SET case_id = :targetCaseId WHERE case_id = :sourceCaseId")
    suspend fun moveReconciliationRevisions(sourceCaseId: String, targetCaseId: String)

    @Query("DELETE FROM reconciliation_cases WHERE case_id = :caseId")
    suspend fun deleteReconciliationCase(caseId: String)

    @Query(
        "UPDATE reconciliation_cases SET status = :status, updated_at_epoch_millis = :updatedAt " +
            "WHERE case_id = :caseId",
    )
    suspend fun markReconciliationCaseStatus(caseId: String, status: String, updatedAt: Long): Int

    @Query("SELECT * FROM story_merge_events WHERE merge_event_id = :mergeEventId")
    suspend fun mergeEvent(mergeEventId: String): StoryMergeEventEntity?

    @Query(
        "SELECT * FROM story_merge_events WHERE survivor_story_id = :storyId OR retired_story_id = :storyId " +
            "ORDER BY merged_at_epoch_millis, merge_event_id",
    )
    suspend fun mergeEventsForStory(storyId: String): List<StoryMergeEventEntity>

    @Upsert
    suspend fun upsertMergeReversalEvent(event: StoryMergeReversalEventEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMergeReversalEvent(event: StoryMergeReversalEventEntity)

    @Query("SELECT * FROM story_merge_reversal_events WHERE merge_event_id = :mergeEventId")
    suspend fun mergeReversalEvent(mergeEventId: String): StoryMergeReversalEventEntity?

    @Query("SELECT * FROM story_merge_reversal_events WHERE merge_event_id = :mergeEventId ORDER BY reversal_event_id")
    suspend fun mergeReversalEventsForMerge(mergeEventId: String): List<StoryMergeReversalEventEntity>

    @Query(
        "DELETE FROM story_redirects WHERE retired_story_id = :retiredStoryId " +
            "AND canonical_story_id = :canonicalStoryId AND merge_event_id = :mergeEventId",
    )
    suspend fun deleteRedirect(
        retiredStoryId: String,
        canonicalStoryId: String,
        mergeEventId: String,
    ): Int

    @Query(
        "UPDATE story_merge_events SET reversibility_state = :state, reversal_payload_version = :payloadVersion, " +
            "reversal_payload = :payload WHERE merge_event_id = :mergeEventId",
    )
    suspend fun updateMergeReversibility(
        mergeEventId: String,
        state: String,
        payloadVersion: Int,
        payload: String,
    ): Int

    @Transaction
    suspend fun rekeyRetiredStoryState(
        retiredStoryId: String,
        survivorStoryId: String,
        nowEpochMillis: Long,
    ) {
        require(nowEpochMillis >= 0L)
        require(retiredStoryId != survivorStoryId)
        reconciliationCasesForStory(retiredStoryId)
            .filter { it.status == "PENDING" }
            .forEach { sourceCase ->
            val replacedLeft = if (sourceCase.leftStoryId == retiredStoryId) survivorStoryId else sourceCase.leftStoryId
            val replacedRight = if (sourceCase.rightStoryId == retiredStoryId) {
                survivorStoryId
            } else {
                sourceCase.rightStoryId
            }
            if (replacedLeft == replacedRight) {
                upsertReconciliationCaseRow(sourceCase.copy(status = "SUPERSEDED"))
            } else {
                val left = minOf(replacedLeft, replacedRight)
                val right = maxOf(replacedLeft, replacedRight)
                val target = reconciliationCase(left, right)
                if (target == null || target.caseId == sourceCase.caseId) {
                    upsertReconciliationCaseRow(sourceCase.copy(leftStoryId = left, rightStoryId = right))
                } else {
                    moveReconciliationRevisions(sourceCase.caseId, target.caseId)
                    val winner = when {
                        target.status != "PENDING" -> target
                        sourceCase.updatedAtEpochMillis > target.updatedAtEpochMillis -> sourceCase
                        else -> target
                    }
                    upsertReconciliationCaseRow(
                        target.copy(
                            status = winner.status,
                            currentRevisionId = winner.currentRevisionId,
                            contextualDeferredAtEpochMillis = winner.contextualDeferredAtEpochMillis,
                            createdAtEpochMillis = minOf(sourceCase.createdAtEpochMillis, target.createdAtEpochMillis),
                            updatedAtEpochMillis = maxOf(sourceCase.updatedAtEpochMillis, target.updatedAtEpochMillis),
                        ),
                    )
                    deleteReconciliationCase(sourceCase.caseId)
                }
            }
        }

        workForStory(retiredStoryId).forEach { sourceWork ->
            val coalesced = coalesceRekeyedCanonicalEngineWork(
                source = sourceWork,
                target = work(survivorStoryId, sourceWork.workType),
                survivorStoryId = survivorStoryId,
                nowEpochMillis = nowEpochMillis,
            )
            upsertWork(coalesced)
            deleteWork(retiredStoryId, sourceWork.workType)
        }
    }
}
