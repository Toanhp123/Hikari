package app.openstory.storage.room.reader

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import app.openstory.storage.room.catalog.StoryEntity
import app.openstory.storage.room.chapters.CanonicalChapterEntity

@Entity(
    tableName = "reading_progress",
    primaryKeys = ["story_id", "canonical_chapter_id"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CanonicalChapterEntity::class,
            parentColumns = ["canonical_chapter_id"],
            childColumns = ["canonical_chapter_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("canonical_chapter_id"), Index("chapter_release_id")],
)
internal data class ReadingProgressEntity(
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "canonical_chapter_id") val canonicalChapterId: String,
    @ColumnInfo(name = "chapter_release_id") val chapterReleaseId: String,
    @ColumnInfo(name = "content_fingerprint") val contentFingerprint: String,
    @ColumnInfo(name = "block_id") val blockId: String,
    @ColumnInfo(name = "character_offset") val characterOffset: Int,
    @ColumnInfo(name = "fraction") val fraction: Float,
    @ColumnInfo(name = "completed_at_epoch_millis") val completedAtEpochMillis: Long?,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)
