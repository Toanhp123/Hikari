package app.openstory.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.openstory.model.ReaderPosition
import java.math.BigDecimal

@Entity(
    tableName = "canonical_chapters",
    foreignKeys = [
        ForeignKey(
            entity = CanonicalStoryEntity::class,
            parentColumns = [
                "story_id",
            ],
            childColumns = [
                "story_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = [
                "story_id",
                "sort_key",
            ],
        ),
    ],
)
internal data class CanonicalChapterEntity(
    @PrimaryKey
    @ColumnInfo(name = "chapter_id")
    val chapterId: String,
    @ColumnInfo(name = "story_id")
    val storyId: String,
    val kind: String,
    @ColumnInfo(name = "volume_number")
    val volumeNumber: BigDecimal?,
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: BigDecimal?,
    @ColumnInfo(name = "part_number")
    val partNumber: BigDecimal?,
    @ColumnInfo(name = "normalized_title")
    val normalizedTitle: String,
    @ColumnInfo(name = "sort_key")
    val sortKey: String,
    @ColumnInfo(name = "first_known_published_at_epoch_millis")
    val firstKnownPublishedAtEpochMillis: Long?,
)

@Entity(
    tableName = "chapter_releases",
    foreignKeys = [
        ForeignKey(
            entity = ContentMappingEntity::class,
            parentColumns = [
                "content_mapping_id",
            ],
            childColumns = [
                "content_mapping_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = [
                "content_mapping_id",
            ],
        ),
        Index(
            value = [
                "plugin_id",
                "source_release_id",
            ],
            unique = true,
            name =
                "index_chapter_releases_plugin_source_release",
        ),
        Index(
            value = [
                "language",
            ],
        ),
    ],
)
internal data class ChapterReleaseEntity(
    @PrimaryKey
    @ColumnInfo(name = "release_id")
    val releaseId: String,
    @ColumnInfo(name = "content_mapping_id")
    val contentMappingId: String,
    @ColumnInfo(name = "plugin_id")
    val pluginId: String,
    @ColumnInfo(name = "source_release_id")
    val sourceReleaseId: String,
    @ColumnInfo(name = "source_url")
    val sourceUrl: String,
    val language: String,
    val title: String,
    @ColumnInfo(name = "volume_number")
    val volumeNumber: BigDecimal?,
    @ColumnInfo(name = "chapter_number")
    val chapterNumber: BigDecimal?,
    @ColumnInfo(name = "part_number")
    val partNumber: BigDecimal?,
    @ColumnInfo(name = "translator_or_uploader")
    val translatorOrUploader: String?,
    @ColumnInfo(name = "published_at_epoch_millis")
    val publishedAtEpochMillis: Long?,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long?,
    @ColumnInfo(name = "content_fingerprint")
    val contentFingerprint: String?,
    val availability: String,
    @ColumnInfo(name = "fetched_at_epoch_millis")
    val fetchedAtEpochMillis: Long,
)

@Entity(
    tableName = "canonical_chapter_releases",
    primaryKeys = [
        "chapter_id",
        "release_id",
    ],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalChapterEntity::class,
            parentColumns = [
                "chapter_id",
            ],
            childColumns = [
                "chapter_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterReleaseEntity::class,
            parentColumns = [
                "release_id",
            ],
            childColumns = [
                "release_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = [
                "release_id",
            ],
        ),
    ],
)
internal data class CanonicalChapterReleaseEntity(
    @ColumnInfo(name = "chapter_id")
    val chapterId: String,
    @ColumnInfo(name = "release_id")
    val releaseId: String,
)

@Entity(
    tableName = "reading_progress",
    primaryKeys = [
        "story_id",
        "chapter_id",
    ],
    foreignKeys = [
        ForeignKey(
            entity = CanonicalStoryEntity::class,
            parentColumns = [
                "story_id",
            ],
            childColumns = [
                "story_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CanonicalChapterEntity::class,
            parentColumns = [
                "chapter_id",
            ],
            childColumns = [
                "chapter_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ChapterReleaseEntity::class,
            parentColumns = [
                "release_id",
            ],
            childColumns = [
                "release_id",
            ],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(
            value = [
                "chapter_id",
            ],
        ),
        Index(
            value = [
                "release_id",
            ],
        ),
    ],
)
internal data class ReadingProgressEntity(
    @ColumnInfo(name = "story_id")
    val storyId: String,
    @ColumnInfo(name = "chapter_id")
    val chapterId: String,
    @ColumnInfo(name = "release_id")
    val releaseId: String?,
    val position: ReaderPosition,
    val completed: Boolean,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)
