package app.openstory.library.storage

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

@Fts4
@Entity(tableName = "library_search_fts")
internal data class LibrarySearchFts(
    @ColumnInfo(name = "story_id") val storyId: String,
    val title: String,
    @ColumnInfo(name = "supporting_text") val supportingText: String?,
)
