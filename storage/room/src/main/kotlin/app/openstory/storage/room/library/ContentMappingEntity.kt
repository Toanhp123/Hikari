package app.openstory.storage.room.library

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import app.openstory.storage.room.catalog.StoryEntity

@Entity(
    tableName = "content_mappings",
    primaryKeys = ["story_id", "plugin_id"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["story_id"]),
        Index(value = ["plugin_id"]),
        Index(value = ["origin"]),
    ],
)
internal data class ContentMappingEntity(
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    @ColumnInfo(name = "source_story_id") val sourceStoryId: String,
    @ColumnInfo(name = "origin") val origin: String,
    @ColumnInfo(name = "policy_version") val policyVersion: Int,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "content_mapping_rejections",
    primaryKeys = ["story_id", "plugin_id", "source_story_id", "policy_version"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["story_id"]),
    ],
)
internal data class ContentMappingRejectionEntity(
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    @ColumnInfo(name = "source_story_id") val sourceStoryId: String,
    @ColumnInfo(name = "policy_version") val policyVersion: Int,
    @ColumnInfo(name = "rejected_at_epoch_millis") val rejectedAtEpochMillis: Long,
)
