package app.openstory.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "canonical_stories",
)
internal data class CanonicalStoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "story_id")
    val storyId: String,
    @ColumnInfo(name = "content_type")
    val contentType: String,
    @ColumnInfo(name = "preferred_title")
    val preferredTitle: String,
    @ColumnInfo(name = "aliases_json")
    val aliasesJson: String,
)

@Entity(
    tableName = "catalog_entries",
    indices = [
        Index(
            value = [
                "catalog_plugin_id",
            ],
        ),
    ],
)
internal data class CatalogEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "catalog_entry_id")
    val catalogEntryId: String,
    @ColumnInfo(name = "catalog_plugin_id")
    val catalogPluginId: String,
    val title: String,
    val description: String?,
    val score: Double?,
    @ColumnInfo(name = "score_scale")
    val scoreScale: Double?,
    @ColumnInfo(
        name = "external_story_id",
        defaultValue = "''",
    )
    val externalStoryId: String,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String?,
    @ColumnInfo(
        name = "authors_json",
        defaultValue = "'[]'",
    )
    val authorsJson: String,
    @ColumnInfo(
        name = "genres_json",
        defaultValue = "'[]'",
    )
    val genresJson: String,
    @ColumnInfo(name = "cover_reference")
    val coverReference: String?,
    @ColumnInfo(name = "publication_status")
    val publicationStatus: String?,
)

@Entity(
    tableName = "story_catalog_entries",
    primaryKeys = [
        "story_id",
        "catalog_entry_id",
    ],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalStoryEntity::class,
            parentColumns = [
                "story_id",
            ],
            childColumns = [
                "story_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CatalogEntryEntity::class,
            parentColumns = [
                "catalog_entry_id",
            ],
            childColumns = [
                "catalog_entry_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = [
                "catalog_entry_id",
            ],
        ),
    ],
)
internal data class StoryCatalogEntryEntity(
    @ColumnInfo(name = "story_id")
    val storyId: String,
    @ColumnInfo(name = "catalog_entry_id")
    val catalogEntryId: String,
)

@Entity(
    tableName = "library_entries",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalStoryEntity::class,
            parentColumns = [
                "story_id",
            ],
            childColumns = [
                "story_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
internal data class LibraryEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "story_id")
    val storyId: String,
    val status: String,
    @ColumnInfo(name = "added_at_epoch_millis")
    val addedAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "content_mappings",
    indices = [
        Index(
            value = [
                "plugin_id",
                "external_story_id",
            ],
            unique = true,
            name =
                "index_content_mappings_plugin_external_story",
        ),
    ],
)
internal data class ContentMappingEntity(
    @PrimaryKey
    @ColumnInfo(name = "content_mapping_id")
    val contentMappingId: String,
    @ColumnInfo(name = "plugin_id")
    val pluginId: String,
    @ColumnInfo(name = "external_story_id")
    val externalStoryId: String,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String,
    val language: String,
    val origin: String,
    val confidence: Double,
    @ColumnInfo(name = "user_locked")
    val userLocked: Boolean,
    val enabled: Boolean,
    @ColumnInfo(name = "last_successful_sync_at_epoch_millis")
    val lastSuccessfulSyncAtEpochMillis: Long?,
    @ColumnInfo(name = "next_eligible_sync_at_epoch_millis")
    val nextEligibleSyncAtEpochMillis: Long?,
    @ColumnInfo(name = "failure_state")
    val failureState: String?,
)

@Entity(
    tableName = "story_content_mappings",
    primaryKeys = [
        "story_id",
        "content_mapping_id",
    ],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalStoryEntity::class,
            parentColumns = [
                "story_id",
            ],
            childColumns = [
                "story_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ContentMappingEntity::class,
            parentColumns = [
                "content_mapping_id",
            ],
            childColumns = [
                "content_mapping_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = [
                "content_mapping_id",
            ],
        ),
    ],
)
internal data class StoryContentMappingEntity(
    @ColumnInfo(name = "story_id")
    val storyId: String,
    @ColumnInfo(name = "content_mapping_id")
    val contentMappingId: String,
)
