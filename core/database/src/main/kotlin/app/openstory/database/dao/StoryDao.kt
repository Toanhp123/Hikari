package app.openstory.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Junction
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
import app.openstory.database.entity.CanonicalStoryEntity
import app.openstory.database.entity.CatalogEntryEntity
import app.openstory.database.entity.LibraryEntryEntity
import app.openstory.database.entity.StoryCatalogEntryEntity
import kotlinx.coroutines.flow.Flow

internal data class StoryAggregate(
    @Embedded
    val story: CanonicalStoryEntity,
    @Relation(
        parentColumn = "story_id",
        entityColumn = "catalog_entry_id",
        associateBy =
            Junction(
                value = StoryCatalogEntryEntity::class,
                parentColumn = "story_id",
                entityColumn = "catalog_entry_id",
            ),
    )
    val catalogEntries: List<CatalogEntryEntity>,
)

@Dao
internal abstract class StoryDao {

    @Transaction
    @Query(
        """
        SELECT *
        FROM canonical_stories
        WHERE story_id = :storyId
        """,
    )
    abstract fun observeStory(
        storyId: String,
    ): Flow<StoryAggregate?>

    @Transaction
    @Query(
        """
        SELECT *
        FROM canonical_stories
        ORDER BY story_id ASC
        """,
    )
    abstract suspend fun canonicalStoryCandidates():
        List<StoryAggregate>

    @Query(
        """
        SELECT *
        FROM library_entries
        ORDER BY
            updated_at_epoch_millis DESC,
            story_id ASC
        """,
    )
    abstract fun observeLibrary():
        Flow<List<LibraryEntryEntity>>

    @Upsert
    protected abstract suspend fun upsertStory(
        story: CanonicalStoryEntity,
    )

    @Upsert
    protected abstract suspend fun upsertCatalogEntries(
        catalogEntries: List<CatalogEntryEntity>,
    )

    @Upsert
    protected abstract suspend fun upsertCatalogLinks(
        links: List<StoryCatalogEntryEntity>,
    )

    @Query(
        """
        DELETE FROM story_catalog_entries
        WHERE story_id = :storyId
        """,
    )
    protected abstract suspend fun deleteCatalogLinks(
        storyId: String,
    )

    @Insert(
        onConflict = OnConflictStrategy.IGNORE,
    )
    protected abstract suspend fun insertLibraryMembership(
        entry: LibraryEntryEntity,
    ): Long

    @Query(
        """
        UPDATE library_entries
        SET
            status = :status,
            updated_at_epoch_millis =
                :updatedAtEpochMillis
        WHERE story_id = :storyId
        """,
    )
    protected abstract suspend fun updateLibraryMembership(
        storyId: String,
        status: String,
        updatedAtEpochMillis: Long,
    ): Int

    @Transaction
    open suspend fun addToLibrary(
        story: CanonicalStoryEntity,
        catalogEntries: List<CatalogEntryEntity>,
        catalogLinks: List<StoryCatalogEntryEntity>,
        status: String,
        nowEpochMillis: Long,
    ) {
        upsertStory(story)
        deleteCatalogLinks(story.storyId)

        if (catalogEntries.isNotEmpty()) {
            upsertCatalogEntries(catalogEntries)
            upsertCatalogLinks(catalogLinks)
        }

        val insertedRowId =
            insertLibraryMembership(
                LibraryEntryEntity(
                    storyId = story.storyId,
                    status = status,
                    addedAtEpochMillis =
                        nowEpochMillis,
                    updatedAtEpochMillis =
                        nowEpochMillis,
                ),
            )

        if (insertedRowId == INSERT_CONFLICT) {
            val updatedRows =
                updateLibraryMembership(
                    storyId = story.storyId,
                    status = status,
                    updatedAtEpochMillis =
                        nowEpochMillis,
                )

            check(updatedRows == 1) {
                "Expected an existing library membership"
            }
        }
    }

    private companion object {
        const val INSERT_CONFLICT = -1L
    }
}
