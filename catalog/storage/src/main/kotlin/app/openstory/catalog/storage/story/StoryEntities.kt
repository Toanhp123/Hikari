package app.openstory.catalog.storage.story

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "story_source_identity",
    indices = [Index(value = ["source_key", "source_story_id"], unique = true)],
)
internal data class StorySourceIdentityEntity(
    @PrimaryKey @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "source_story_id") val sourceStoryId: String,
)

@Entity(
    tableName = "story_source_summary",
    foreignKeys = [
        ForeignKey(
            entity = StorySourceIdentityEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["source_key"])],
)
internal data class StorySourceSummaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "source_version") val sourceVersion: String,
    val title: String,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "cover_locator_type") val coverLocatorType: String?,
    @ColumnInfo(name = "cover_locator_value") val coverLocatorValue: String?,
    @ColumnInfo(name = "cover_locator_aux") val coverLocatorAux: String?,
    @ColumnInfo(name = "cover_revision") val coverRevision: String?,
    @ColumnInfo(name = "rating_value") val ratingValue: Double?,
    @ColumnInfo(name = "rating_scale") val ratingScale: Double?,
    @ColumnInfo(name = "publication_status_summary") val publicationStatusSummary: String?,
    @ColumnInfo(name = "latest_update_epoch_ms") val latestUpdateEpochMs: Long?,
    @ColumnInfo(name = "last_seen_epoch_ms") val lastSeenEpochMs: Long,
)
