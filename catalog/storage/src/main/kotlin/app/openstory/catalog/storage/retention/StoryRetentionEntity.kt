package app.openstory.catalog.storage.retention

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.openstory.catalog.storage.story.StorySourceIdentityEntity

@Entity(
    tableName = "story_orphan_retention",
    foreignKeys = [
        ForeignKey(
            entity = StorySourceIdentityEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [Index(value = ["last_accessed_epoch_ms", "story_id"])],
)
internal data class StoryRetentionEntity(
    @PrimaryKey
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "last_accessed_epoch_ms") val lastAccessedEpochMs: Long,
)
