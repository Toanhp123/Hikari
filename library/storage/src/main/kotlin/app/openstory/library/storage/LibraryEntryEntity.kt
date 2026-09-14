package app.openstory.library.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "library_entry",
    indices = [
        Index(
            value = ["saved_at_epoch_ms", "story_id"],
            orders = [Index.Order.DESC, Index.Order.ASC],
            name = "index_library_entry_saved_story",
        ),
        Index(
            value = ["origin_media_context", "saved_at_epoch_ms", "story_id"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC],
            name = "index_library_entry_media_saved_story",
        ),
    ],
)
internal data class LibraryEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "source_story_id") val sourceStoryId: String,
    @ColumnInfo(name = "origin_media_context") val originMediaContext: String,
    @ColumnInfo(name = "saved_at_epoch_ms") val savedAtEpochMs: Long,
    val title: String,
    @ColumnInfo(name = "supporting_text") val supportingText: String?,
    @ColumnInfo(name = "cover_locator_type") val coverLocatorType: String?,
    @ColumnInfo(name = "cover_locator_value") val coverLocatorValue: String?,
    @ColumnInfo(name = "cover_locator_aux") val coverLocatorAux: String?,
    @ColumnInfo(name = "cover_revision") val coverRevision: String?,
)
