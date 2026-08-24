package app.openstory.storage.room.catalog

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "catalog_entry_identifiers",
    primaryKeys = ["plugin_id", "source_id", "namespace", "value", "scope"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogEntryEntity::class,
            parentColumns = ["plugin_id", "source_id"],
            childColumns = ["plugin_id", "source_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("namespace", "value", "scope"),
        Index("plugin_id", "source_id"),
    ],
)
internal data class CatalogEntryIdentifierEntity(
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    val namespace: String,
    val value: String,
    val scope: String,
)

@Entity(
    tableName = "story_canonical_state",
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class StoryCanonicalStateEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "active_generation_id") val activeGenerationId: String?,
    val health: String,
    @ColumnInfo(name = "preference_mode") val preferenceMode: String,
    @ColumnInfo(name = "pinned_plugin_id") val pinnedPluginId: String?,
    @ColumnInfo(name = "pinned_source_id") val pinnedSourceId: String?,
    @ColumnInfo(name = "preference_revision") val preferenceRevision: Long,
    @ColumnInfo(name = "identity_revision") val identityRevision: Long,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long?,
)

@Entity(
    tableName = "canonical_generations",
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("story_id", "created_at_epoch_millis")],
)
internal data class CanonicalGenerationEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "fusion_policy_version") val fusionPolicyVersion: Int,
    @ColumnInfo(name = "primary_policy_version") val primaryPolicyVersion: Int,
    @ColumnInfo(name = "fusion_fingerprint") val fusionFingerprint: String,
    @ColumnInfo(name = "primary_plugin_id") val primaryPluginId: String,
    @ColumnInfo(name = "primary_source_id") val primarySourceId: String,
    val title: String,
    val description: String?,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "popularity_rank") val popularityRank: Long?,
    val aliases: Set<String>,
    val authors: Set<String>,
    val genres: Set<String>,
    @ColumnInfo(name = "language_tags") val languageTags: Set<String>,
    @ColumnInfo(name = "publication_status") val publicationStatus: String?,
    @ColumnInfo(name = "latest_update_at_epoch_millis") val latestUpdateAtEpochMillis: Long?,
    @ColumnInfo(name = "latest_update_release_label") val latestUpdateReleaseLabel: String?,
    @ColumnInfo(name = "score_normalized_value") val scoreNormalizedValue: Double?,
    @ColumnInfo(name = "score_contributor_count") val scoreContributorCount: Int?,
    val health: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    val valid: Boolean,
)

@Entity(
    tableName = "canonical_field_provenance",
    primaryKeys = ["generation_id", "field_key", "contributor_plugin_id", "contributor_source_id"],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalGenerationEntity::class,
            parentColumns = ["generation_id"],
            childColumns = ["generation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("generation_id")],
)
internal data class CanonicalFieldProvenanceEntity(
    @ColumnInfo(name = "generation_id") val generationId: String,
    @ColumnInfo(name = "field_key") val fieldKey: String,
    @ColumnInfo(name = "contributor_plugin_id") val contributorPluginId: String,
    @ColumnInfo(name = "contributor_source_id") val contributorSourceId: String,
    val strategy: String,
    @ColumnInfo(name = "contributor_fusion_fingerprint") val contributorFusionFingerprint: String,
    @ColumnInfo(name = "metadata_level") val metadataLevel: String,
    @ColumnInfo(name = "reason_codes") val reasonCodes: Set<String>,
    @ColumnInfo(name = "policy_version") val policyVersion: Int,
)

@Entity(
    tableName = "reconciliation_cases",
    indices = [Index(value = ["left_story_id", "right_story_id"], unique = true)],
)
internal data class ReconciliationCaseEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "left_story_id") val leftStoryId: String,
    @ColumnInfo(name = "right_story_id") val rightStoryId: String,
    val status: String,
    @ColumnInfo(name = "current_revision_id") val currentRevisionId: String?,
    @ColumnInfo(name = "contextual_deferred_at_epoch_millis") val contextualDeferredAtEpochMillis: Long?,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "reconciliation_case_revisions",
    foreignKeys = [
        ForeignKey(
            entity = ReconciliationCaseEntity::class,
            parentColumns = ["case_id"],
            childColumns = ["case_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("case_id")],
)
internal data class ReconciliationCaseRevisionEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "revision_id") val revisionId: String,
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "left_story_id") val leftStoryId: String,
    @ColumnInfo(name = "right_story_id") val rightStoryId: String,
    val decision: String,
    @ColumnInfo(name = "identity_fingerprint") val identityFingerprint: String,
    @ColumnInfo(name = "policy_version") val policyVersion: Int,
    val score: Double,
    @ColumnInfo(name = "title_similarity") val titleSimilarity: Double?,
    @ColumnInfo(name = "author_similarity") val authorSimilarity: Double?,
    @ColumnInfo(name = "reason_codes") val reasonCodes: Set<String>,
    @ColumnInfo(name = "hard_conflicts") val hardConflicts: Set<String>,
    @ColumnInfo(name = "resolution_origin") val resolutionOrigin: String?,
    @ColumnInfo(name = "evaluated_at_epoch_millis") val evaluatedAtEpochMillis: Long,
)

internal data class ReconciliationRevisionCountRow(
    @ColumnInfo(name = "case_id") val caseId: String,
    @ColumnInfo(name = "revision_count") val revisionCount: Long,
)

@Entity(
    tableName = "story_merge_events",
    indices = [Index(
        value = ["survivor_story_id", "retired_story_id", "evidence_fingerprint", "policy_version"],
        unique = true,
    )],
)
internal data class StoryMergeEventEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "merge_event_id") val mergeEventId: String,
    @ColumnInfo(name = "survivor_story_id") val survivorStoryId: String,
    @ColumnInfo(name = "retired_story_id") val retiredStoryId: String,
    val origin: String,
    @ColumnInfo(name = "reconciliation_case_id") val reconciliationCaseId: String?,
    @ColumnInfo(name = "evidence_fingerprint") val evidenceFingerprint: String,
    @ColumnInfo(name = "policy_version") val policyVersion: Int,
    @ColumnInfo(name = "merged_at_epoch_millis") val mergedAtEpochMillis: Long,
    @ColumnInfo(name = "reversibility_state") val reversibilityState: String,
    @ColumnInfo(name = "reversal_payload_version") val reversalPayloadVersion: Int,
    @ColumnInfo(name = "reversal_payload") val reversalPayload: String,
)

@Entity(
    tableName = "story_merge_reversal_events",
    foreignKeys = [
        ForeignKey(
            entity = StoryMergeEventEntity::class,
            parentColumns = ["merge_event_id"],
            childColumns = ["merge_event_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["merge_event_id"], unique = true)],
)
internal data class StoryMergeReversalEventEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "reversal_event_id") val reversalEventId: String,
    @ColumnInfo(name = "merge_event_id") val mergeEventId: String,
    @ColumnInfo(name = "restored_story_id") val restoredStoryId: String,
    @ColumnInfo(name = "surviving_story_id") val survivingStoryId: String,
    val origin: String,
    @ColumnInfo(name = "reason_codes") val reasonCodes: Set<String>,
    @ColumnInfo(name = "reversed_at_epoch_millis") val reversedAtEpochMillis: Long,
)

@Entity(
    tableName = "story_redirects",
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["canonical_story_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = StoryMergeEventEntity::class,
            parentColumns = ["merge_event_id"],
            childColumns = ["merge_event_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("canonical_story_id"), Index("merge_event_id")],
)
internal data class StoryRedirectEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "retired_story_id") val retiredStoryId: String,
    @ColumnInfo(name = "canonical_story_id") val canonicalStoryId: String,
    @ColumnInfo(name = "merge_event_id") val mergeEventId: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
)

@Entity(
    tableName = "canonical_engine_work",
    primaryKeys = ["story_id", "work_type"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = [
                "lease_expires_at_epoch_millis",
                "next_attempt_at_epoch_millis",
                "work_type",
                "story_id",
            ],
        ),
    ],
)
internal data class CanonicalEngineWorkEntity(
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "work_type") val workType: String,
    val reason: String,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "next_attempt_at_epoch_millis") val nextAttemptAtEpochMillis: Long,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String?,
    @ColumnInfo(name = "required_policy_version") val requiredPolicyVersion: Int?,
    @ColumnInfo(name = "lease_token") val leaseToken: String? = null,
    @ColumnInfo(name = "lease_expires_at_epoch_millis") val leaseExpiresAtEpochMillis: Long? = null,
)

@Entity(
    tableName = "catalog_change_outbox",
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("story_id"),
        Index(value = ["created_at_epoch_millis", "event_id"]),
    ],
)
internal data class CatalogChangeOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "event_id") val eventId: Long = 0L,
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "identity_fingerprint_changed") val identityFingerprintChanged: Boolean,
    @ColumnInfo(name = "fusion_fingerprint_changed") val fusionFingerprintChanged: Boolean,
    @ColumnInfo(name = "availability_changed") val availabilityChanged: Boolean,
    @ColumnInfo(name = "evidence_level") val evidenceLevel: String,
    val reason: String,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
)
