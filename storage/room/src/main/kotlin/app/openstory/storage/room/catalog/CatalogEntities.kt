package app.openstory.storage.room.catalog

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "stories")
internal data class StoryEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "story_id")
    val storyId: String,
    @ColumnInfo(name = "content_type")
    val contentType: String,
)

@Entity(
    tableName = "catalog_entries",
    primaryKeys = ["plugin_id", "source_id"],
    foreignKeys = [
        ForeignKey(
            entity = StoryEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("story_id")],
)
internal data class CatalogEntryEntity(
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    @ColumnInfo(name = "source_id") val sourceId: String,
    @ColumnInfo(name = "story_id") val storyId: String,
    val title: String,
    val aliases: Set<String>,
    val authors: Set<String>,
    val description: String?,
    val genres: Set<String>,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "language_tags") val languageTags: Set<String>,
    @ColumnInfo(name = "cover_url") val coverUrl: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
    @ColumnInfo(name = "score_value") val scoreValue: Double?,
    @ColumnInfo(name = "score_scale") val scoreScale: Double?,
    @ColumnInfo(name = "popularity_rank") val popularityRank: Long?,
    @ColumnInfo(name = "plugin_version") val pluginVersion: String,
    @ColumnInfo(name = "fetched_at_epoch_millis") val fetchedAtEpochMillis: Long,
)

@Entity(tableName = "catalog_home_snapshots")
internal data class CatalogHomeSnapshotEntity(
    @androidx.room.PrimaryKey
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    @ColumnInfo(name = "plugin_version") val pluginVersion: String,
    @ColumnInfo(name = "refreshed_at_epoch_millis") val refreshedAtEpochMillis: Long,
)

@Entity(
    tableName = "catalog_home_sections",
    primaryKeys = ["plugin_id", "section_id"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogHomeSnapshotEntity::class,
            parentColumns = ["plugin_id"],
            childColumns = ["plugin_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("plugin_id")],
)
internal data class CatalogHomeSectionEntity(
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    @ColumnInfo(name = "section_id") val sectionId: String,
    val title: String,
    val position: Int,
)

@Entity(
    tableName = "catalog_home_items",
    primaryKeys = ["plugin_id", "section_id", "position"],
    foreignKeys = [
        ForeignKey(
            entity = CatalogHomeSectionEntity::class,
            parentColumns = ["plugin_id", "section_id"],
            childColumns = ["plugin_id", "section_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CatalogEntryEntity::class,
            parentColumns = ["plugin_id", "source_id"],
            childColumns = ["plugin_id", "source_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("plugin_id", "section_id"),
        Index("plugin_id", "source_id"),
        Index(value = ["plugin_id", "section_id", "source_id"], unique = true),
    ],
)
internal data class CatalogHomeItemEntity(
    @ColumnInfo(name = "plugin_id") val pluginId: String,
    @ColumnInfo(name = "section_id") val sectionId: String,
    val position: Int,
    @ColumnInfo(name = "source_id") val sourceId: String,
)
