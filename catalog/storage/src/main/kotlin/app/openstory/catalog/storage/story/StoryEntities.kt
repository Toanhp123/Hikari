package app.openstory.catalog.storage.story

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.openstory.catalog.domain.identity.StorySourceRef

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

@Entity(
    tableName = "story_detail",
    foreignKeys = [
        ForeignKey(
            entity = StorySourceSummaryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["source_key", "source_story_id"], unique = true)],
)
internal data class StoryDetailEntity(
    @PrimaryKey
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "source_story_id") val sourceStoryId: String,
    @ColumnInfo(name = "source_version") val sourceVersion: String,
    val description: String?,
    @ColumnInfo(name = "publication_status") val publicationStatus: String?,
    val language: String?,
    @ColumnInfo(name = "fetched_at_epoch_ms") val fetchedAtEpochMs: Long,
    @ColumnInfo(name = "last_accessed_epoch_ms") val lastAccessedEpochMs: Long,
)

@Entity(
    tableName = "story_author",
    primaryKeys = ["story_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = StoryDetailEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
internal data class StoryAuthorEntity(
    @ColumnInfo(name = "story_id") val storyId: String,
    val position: Int,
    val value: String,
)

@Entity(
    tableName = "story_artist",
    primaryKeys = ["story_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = StoryDetailEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
internal data class StoryArtistEntity(
    @ColumnInfo(name = "story_id") val storyId: String,
    val position: Int,
    val value: String,
)

@Entity(
    tableName = "story_genre",
    primaryKeys = ["story_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = StoryDetailEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
)
internal data class StoryGenreEntity(
    @ColumnInfo(name = "story_id") val storyId: String,
    val position: Int,
    val value: String,
)

internal fun StorySourceIdentityEntity.matches(ref: StorySourceRef): Boolean =
    storyId == ref.storyId.value &&
        sourceKey == ref.catalogSourceKey.value &&
        sourceStoryId == ref.sourceStoryId

internal fun StorySourceRef.toIdentityEntity() = StorySourceIdentityEntity(
    storyId = storyId.value,
    sourceKey = catalogSourceKey.value,
    sourceStoryId = sourceStoryId,
)
