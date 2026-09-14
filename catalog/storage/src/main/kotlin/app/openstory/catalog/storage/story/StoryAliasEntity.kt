package app.openstory.catalog.storage.story

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "story_alias",
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
internal data class StoryAliasEntity(
    @ColumnInfo(name = "story_id") val storyId: String,
    val position: Int,
    val value: String,
)
