package app.openstory.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction

@Dao
internal abstract class StoryPurgeDao {

    @Query(
        """
        SELECT catalog_entry_id
        FROM story_catalog_entries
        WHERE story_id = :storyId
        """,
    )
    protected abstract suspend fun catalogEntryIdsForStory(
        storyId: String,
    ): List<String>

    @Query(
        """
        SELECT content_mapping_id
        FROM story_content_mappings
        WHERE story_id = :storyId
        """,
    )
    protected abstract suspend fun contentMappingIdsForStory(
        storyId: String,
    ): List<String>

    @Query(
        """
        DELETE FROM canonical_stories
        WHERE story_id = :storyId
        """,
    )
    protected abstract suspend fun deleteCanonicalStory(
        storyId: String,
    )

    @Query(
        """
        DELETE FROM catalog_entries
        WHERE catalog_entry_id IN (:catalogEntryIds)
            AND NOT EXISTS (
                SELECT 1
                FROM story_catalog_entries
                WHERE story_catalog_entries.catalog_entry_id =
                    catalog_entries.catalog_entry_id
            )
        """,
    )
    protected abstract suspend fun deleteOrphanCatalogEntries(
        catalogEntryIds: List<String>,
    )

    @Query(
        """
        DELETE FROM content_mappings
        WHERE content_mapping_id IN (:contentMappingIds)
            AND NOT EXISTS (
                SELECT 1
                FROM story_content_mappings
                WHERE story_content_mappings.content_mapping_id =
                    content_mappings.content_mapping_id
            )
        """,
    )
    protected abstract suspend fun deleteOrphanContentMappings(
        contentMappingIds: List<String>,
    )

    @Transaction
    open suspend fun purgeStory(
        storyId: String,
    ) {
        val catalogEntryIds =
            catalogEntryIdsForStory(storyId)
        val contentMappingIds =
            contentMappingIdsForStory(storyId)

        deleteCanonicalStory(storyId)

        if (catalogEntryIds.isNotEmpty()) {
            deleteOrphanCatalogEntries(catalogEntryIds)
        }
        if (contentMappingIds.isNotEmpty()) {
            deleteOrphanContentMappings(contentMappingIds)
        }
    }
}
