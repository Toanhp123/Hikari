package app.openstory.storage.room.library

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.openstory.storage.room.catalog.StoryEntity

@Entity(
    tableName = "library_entries",
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["status"]),
        Index(value = ["updated_at_epoch_millis"]),
    ],
)
internal data class LibraryEntity(
    @PrimaryKey
    @ColumnInfo(name = "story_id")
    val storyId: String,
    val status: String,
    @ColumnInfo(name = "added_at_epoch_millis")
    val addedAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)
