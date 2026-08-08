package app.openstory.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "catalog_home_snapshots",
    primaryKeys = [
        "catalog_plugin_id",
    ],
)
internal data class CatalogHomeSnapshotEntity(
    @ColumnInfo(name = "catalog_plugin_id")
    val catalogPluginId: String,
    @ColumnInfo(name = "plugin_version")
    val pluginVersion: String,
    @ColumnInfo(name = "refreshed_at_epoch_millis")
    val refreshedAtEpochMillis: Long,
)

@Entity(
    tableName = "catalog_home_sections",
    primaryKeys = [
        "catalog_plugin_id",
        "section_source_id",
    ],
    foreignKeys = [
        ForeignKey(
            entity = CatalogHomeSnapshotEntity::class,
            parentColumns = [
                "catalog_plugin_id",
            ],
            childColumns = [
                "catalog_plugin_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = [
                "catalog_plugin_id",
                "section_position",
            ],
            unique = true,
            name =
                "index_catalog_home_sections_plugin_position",
        ),
    ],
)
internal data class CatalogHomeSectionEntity(
    @ColumnInfo(name = "catalog_plugin_id")
    val catalogPluginId: String,
    @ColumnInfo(name = "section_source_id")
    val sectionSourceId: String,
    val title: String,
    @ColumnInfo(name = "section_position")
    val sectionPosition: Int,
)

@Entity(
    tableName = "catalog_home_items",
    primaryKeys = [
        "catalog_plugin_id",
        "section_source_id",
        "catalog_entry_id",
    ],
    foreignKeys = [
        ForeignKey(
            entity = CatalogHomeSectionEntity::class,
            parentColumns = [
                "catalog_plugin_id",
                "section_source_id",
            ],
            childColumns = [
                "catalog_plugin_id",
                "section_source_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CatalogEntryEntity::class,
            parentColumns = [
                "catalog_entry_id",
            ],
            childColumns = [
                "catalog_entry_id",
            ],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(
            value = [
                "catalog_plugin_id",
                "section_source_id",
                "item_position",
            ],
            unique = true,
            name =
                "index_catalog_home_items_section_position",
        ),
        Index(
            value = [
                "catalog_entry_id",
            ],
            name =
                "index_catalog_home_items_catalog_entry_id",
        ),
    ],
)
internal data class CatalogHomeItemEntity(
    @ColumnInfo(name = "catalog_plugin_id")
    val catalogPluginId: String,
    @ColumnInfo(name = "section_source_id")
    val sectionSourceId: String,
    @ColumnInfo(name = "catalog_entry_id")
    val catalogEntryId: String,
    @ColumnInfo(name = "item_position")
    val itemPosition: Int,
)
