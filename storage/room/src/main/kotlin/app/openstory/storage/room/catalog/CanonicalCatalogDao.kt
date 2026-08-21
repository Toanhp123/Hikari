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

    @Upsert
    suspend fun upsertWork(item: CanonicalEngineWorkEntity)

    @Query(
        "SELECT * FROM canonical_engine_work WHERE next_attempt_at_epoch_millis <= :nowEpochMillis " +
            "ORDER BY next_attempt_at_epoch_millis ASC, story_id ASC, work_type ASC LIMIT :limit",
    )
    suspend fun readyWork(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkEntity>

    @Query("SELECT * FROM canonical_engine_work WHERE story_id = :storyId AND work_type = :workType")
    suspend fun work(storyId: String, workType: String): CanonicalEngineWorkEntity?

    @Query("SELECT * FROM canonical_engine_work WHERE story_id = :storyId ORDER BY work_type")
    suspend fun workForStory(storyId: String): List<CanonicalEngineWorkEntity>

    @Query("DELETE FROM canonical_engine_work WHERE story_id = :storyId AND work_type = :workType")
    suspend fun deleteWork(storyId: String, workType: String)

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

    @Query("SELECT * FROM reconciliation_case_revisions WHERE revision_id = :revisionId")
    suspend fun reconciliationRevision(revisionId: String): ReconciliationCaseRevisionEntity?

    @Query("UPDATE reconciliation_case_revisions SET case_id = :targetCaseId WHERE case_id = :sourceCaseId")
    suspend fun moveReconciliationRevisions(sourceCaseId: String, targetCaseId: String)

    @Query("DELETE FROM reconciliation_cases WHERE case_id = :caseId")
    suspend fun deleteReconciliationCase(caseId: String)

    @Query("SELECT * FROM story_merge_events WHERE merge_event_id = :mergeEventId")
    suspend fun mergeEvent(mergeEventId: String): StoryMergeEventEntity?

    @Query(
        "SELECT * FROM story_merge_events WHERE survivor_story_id = :storyId OR retired_story_id = :storyId " +
            "ORDER BY merged_at_epoch_millis, merge_event_id",
    )
    suspend fun mergeEventsForStory(storyId: String): List<StoryMergeEventEntity>

    @Upsert
    suspend fun upsertMergeReversalEvent(event: StoryMergeReversalEventEntity)

    @Query("SELECT * FROM story_merge_reversal_events WHERE merge_event_id = :mergeEventId")
    suspend fun mergeReversalEvent(mergeEventId: String): StoryMergeReversalEventEntity?

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
    suspend fun rekeyRetiredStoryState(retiredStoryId: String, survivorStoryId: String) {
        require(retiredStoryId != survivorStoryId)
        reconciliationCasesForStory(retiredStoryId).forEach { sourceCase ->
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
                    val winner = if (sourceCase.updatedAtEpochMillis > target.updatedAtEpochMillis) {
                        sourceCase
                    } else {
                        target
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
            val target = work(survivorStoryId, sourceWork.workType)
            val coalesced = if (target == null) {
                sourceWork.copy(storyId = survivorStoryId)
            } else {
                target.copy(
                    reason = maxOf(target.reason, sourceWork.reason),
                    attemptCount = maxOf(target.attemptCount, sourceWork.attemptCount),
                    nextAttemptAtEpochMillis = minOf(
                        target.nextAttemptAtEpochMillis,
                        sourceWork.nextAttemptAtEpochMillis,
                    ),
                    lastErrorCode = listOfNotNull(target.lastErrorCode, sourceWork.lastErrorCode).maxOrNull(),
                    requiredPolicyVersion = listOfNotNull(
                        target.requiredPolicyVersion,
                        sourceWork.requiredPolicyVersion,
                    ).maxOrNull(),
                )
            }
            upsertWork(coalesced)
            deleteWork(retiredStoryId, sourceWork.workType)
        }
    }
}
