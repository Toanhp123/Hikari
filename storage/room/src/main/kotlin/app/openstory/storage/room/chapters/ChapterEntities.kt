package app.openstory.storage.room.chapters

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Relation
import app.openstory.storage.room.catalog.StoryEntity

@Entity(
    tableName = "canonical_chapters",
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["story_id"]), Index(value = ["story_id", "tombstoned"])],
)
internal data class CanonicalChapterEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "canonical_chapter_id") val canonicalChapterId: String,
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "volume") val volume: String?,
    @ColumnInfo(name = "chapter") val chapter: String?,
    @ColumnInfo(name = "part") val part: Int?,
    @ColumnInfo(name = "normalized_title") val normalizedTitle: String?,
    @ColumnInfo(name = "display_label") val displayLabel: String,
    @ColumnInfo(name = "tombstoned") val tombstoned: Boolean,
)

@Entity(
    tableName = "chapter_releases",
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
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["story_id"]),
        Index(value = ["canonical_chapter_id"]),
        Index(value = ["plugin_id", "source_story_id", "source_release_id"], unique = true),
    ],
)
internal data class ChapterReleaseEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "chapter_release_id") val chapterReleaseId: String,
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    @ColumnInfo(name = "source_story_id") val sourceStoryId: String,
    @ColumnInfo(name = "source_release_id") val sourceReleaseId: String,
    @ColumnInfo(name = "display_label") val displayLabel: String,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "volume") val volume: String?,
    @ColumnInfo(name = "chapter") val chapter: String?,
    @ColumnInfo(name = "part") val part: Int?,
    @ColumnInfo(name = "normalized_title") val normalizedTitle: String?,
    @ColumnInfo(name = "language_tag") val languageTag: String,
    @ColumnInfo(name = "published_at_epoch_millis") val publishedAtEpochMillis: Long?,
    @ColumnInfo(name = "canonical_chapter_id") val canonicalChapterId: String?,
)

@Entity(
    tableName = "chapter_aggregation_overrides",
    primaryKeys = ["story_id", "chapter_release_id"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterReleaseEntity::class,
            parentColumns = ["chapter_release_id"],
            childColumns = ["chapter_release_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CanonicalChapterEntity::class,
            parentColumns = ["canonical_chapter_id"],
            childColumns = ["canonical_chapter_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["chapter_release_id"]), Index(value = ["canonical_chapter_id"])],
)
internal data class ChapterAggregationOverrideEntity(
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "chapter_release_id") val chapterReleaseId: String,
    @ColumnInfo(name = "canonical_chapter_id") val canonicalChapterId: String?,
    @ColumnInfo(name = "kind") val kind: String,
)

@Entity(
    tableName = "chapter_sync_states",
    primaryKeys = ["story_id", "plugin_id", "source_story_id"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["story_id"])],
)
internal data class ChapterSyncStateEntity(
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    @ColumnInfo(name = "source_story_id") val sourceStoryId: String,
    @ColumnInfo(name = "phase") val phase: String,
    @ColumnInfo(name = "cursor") val cursor: String?,
    @ColumnInfo(name = "checkpoint") val checkpoint: String?,
    @ColumnInfo(name = "fingerprint") val fingerprint: String?,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)

internal data class CanonicalChapterWithReleases(
    @Embedded val chapter: CanonicalChapterEntity,
    @Relation(
        parentColumn = "canonical_chapter_id",
        entityColumn = "canonical_chapter_id",
    )
    val releases: List<ChapterReleaseEntity>,
)
