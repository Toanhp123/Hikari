package app.openstory.database.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import app.openstory.database.entity.CanonicalStoryEntity
import app.openstory.database.entity.CatalogEntryEntity
import app.openstory.database.entity.CatalogHomeItemEntity
import app.openstory.database.entity.CatalogHomeSectionEntity
import app.openstory.database.entity.CatalogHomeSnapshotEntity
import app.openstory.database.entity.StoryCatalogEntryEntity
import kotlinx.coroutines.flow.Flow

internal data class CatalogEntryWithStoryRow(
    @ColumnInfo(name = "story_id")
    val storyId: String,
    @Embedded
    val entry: CatalogEntryEntity,
)

internal data class CatalogHomeRow(
    @ColumnInfo(name = "home_plugin_id")
    val pluginId: String,
    @ColumnInfo(name = "home_plugin_version")
    val pluginVersion: String,
    @ColumnInfo(name = "home_refreshed_at_epoch_millis")
    val refreshedAtEpochMillis: Long,
    @ColumnInfo(name = "home_section_source_id")
    val sectionSourceId: String?,
    @ColumnInfo(name = "home_section_title")
    val sectionTitle: String?,
    @ColumnInfo(name = "home_section_position")
    val sectionPosition: Int?,
    @ColumnInfo(name = "home_item_position")
    val itemPosition: Int?,
    @ColumnInfo(name = "home_story_id")
    val storyId: String?,
    @Embedded
    val entry: CatalogEntryEntity?,
)

@Dao
internal abstract class CatalogDao {
    @Query(
        """
        SELECT
            links.story_id AS story_id,
            entries.*
        FROM catalog_entries AS entries
        INNER JOIN story_catalog_entries AS links
            ON links.catalog_entry_id = entries.catalog_entry_id
        WHERE entries.catalog_plugin_id = :pluginId
          AND entries.external_story_id = :sourceId
        ORDER BY links.story_id ASC
        LIMIT 1
        """,
    )
    abstract suspend fun catalogEntryWithStory(
        pluginId: String,
        sourceId: String,
    ): CatalogEntryWithStoryRow?

    @Insert(
        onConflict = OnConflictStrategy.ABORT,
    )
    abstract suspend fun insertCanonicalStory(
        story: CanonicalStoryEntity,
    )

    @Upsert
    abstract suspend fun upsertCatalogEntry(
        entry: CatalogEntryEntity,
    )

    @Query(
        """
        DELETE FROM story_catalog_entries
        WHERE catalog_entry_id = :catalogEntryId
        """,
    )
    abstract suspend fun deleteCatalogLinks(
        catalogEntryId: String,
    )

    @Insert(
        onConflict = OnConflictStrategy.IGNORE,
    )
    abstract suspend fun insertCatalogLink(
        link: StoryCatalogEntryEntity,
    ): Long

    @Upsert
    abstract suspend fun upsertHomeSnapshot(
        snapshot: CatalogHomeSnapshotEntity,
    )

    @Query(
        """
        DELETE FROM catalog_home_sections
        WHERE catalog_plugin_id = :pluginId
        """,
    )
    abstract suspend fun deleteHomeSections(
        pluginId: String,
    )

    @Insert(
        onConflict = OnConflictStrategy.ABORT,
    )
    abstract suspend fun insertHomeSections(
        sections: List<CatalogHomeSectionEntity>,
    )

    @Insert(
        onConflict = OnConflictStrategy.ABORT,
    )
    abstract suspend fun insertHomeItems(
        items: List<CatalogHomeItemEntity>,
    )

    @Query(
        HOME_QUERY +
            " WHERE snapshots.catalog_plugin_id = :pluginId" +
            HOME_ORDER,
    )
    abstract fun observeHome(
        pluginId: String,
    ): Flow<List<CatalogHomeRow>>

    @Query(
        HOME_QUERY + HOME_ORDER,
    )
    abstract fun observeHomes():
        Flow<List<CatalogHomeRow>>

    private companion object {
        const val HOME_QUERY =
            """
            SELECT
                snapshots.catalog_plugin_id AS home_plugin_id,
                snapshots.plugin_version AS home_plugin_version,
                snapshots.refreshed_at_epoch_millis AS home_refreshed_at_epoch_millis,
                sections.section_source_id AS home_section_source_id,
                sections.title AS home_section_title,
                sections.section_position AS home_section_position,
                items.item_position AS home_item_position,
                (
                    SELECT links.story_id
                    FROM story_catalog_entries AS links
                    WHERE links.catalog_entry_id = entries.catalog_entry_id
                    ORDER BY links.story_id ASC
                    LIMIT 1
                ) AS home_story_id,
                entries.*
            FROM catalog_home_snapshots AS snapshots
            LEFT JOIN catalog_home_sections AS sections
                ON sections.catalog_plugin_id = snapshots.catalog_plugin_id
            LEFT JOIN catalog_home_items AS items
                ON items.catalog_plugin_id = sections.catalog_plugin_id
               AND items.section_source_id = sections.section_source_id
            LEFT JOIN catalog_entries AS entries
                ON entries.catalog_entry_id = items.catalog_entry_id
            """

        const val HOME_ORDER =
            """
             ORDER BY
                snapshots.catalog_plugin_id ASC,
                sections.section_position ASC,
                items.item_position ASC,
                items.catalog_entry_id ASC
            """
    }
}
