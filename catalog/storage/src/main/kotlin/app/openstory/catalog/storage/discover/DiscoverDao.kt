package app.openstory.catalog.storage.discover

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import app.openstory.catalog.storage.story.StorySourceIdentityEntity
import app.openstory.catalog.storage.story.StorySourceSummaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
internal interface DiscoverDao {
    @Query(
        """
        SELECT
            state.source_key AS state_source_key,
            state.media_type AS state_media_type,
            state.source_version AS state_source_version,
            state.published_generation AS state_generation,
            state.published_at_epoch_ms AS state_published_at_epoch_ms,
            card.source_version AS card_source_version,
            card.section_kind AS card_section_kind,
            card.item_position AS card_item_position,
            card.story_id AS card_story_id,
            card.source_story_id AS card_source_story_id,
            card.title AS card_title,
            card.content_type AS card_content_type,
            card.cover_locator_type AS card_cover_locator_type,
            card.cover_locator_value AS card_cover_locator_value,
            card.cover_locator_aux AS card_cover_locator_aux,
            card.cover_revision AS card_cover_revision,
            card.rating_value AS card_rating_value,
            card.rating_scale AS card_rating_scale,
            card.publication_status_summary AS card_publication_status_summary,
            card.latest_update_epoch_ms AS card_latest_update_epoch_ms
        FROM catalog_source_state AS state
        LEFT JOIN discover_card AS card
            ON card.source_key = state.source_key
            AND card.media_type = state.media_type
            AND card.generation = state.published_generation
        WHERE state.source_key = :sourceKey AND state.media_type = :mediaType
        ORDER BY
            CASE card.section_kind
                WHEN 'POPULAR' THEN 0
                WHEN 'LATEST_UPDATES' THEN 1
                WHEN 'TOP_RATED' THEN 2
                ELSE 3
            END,
            card.item_position ASC
        """,
    )
    fun observeDiscover(sourceKey: String, mediaType: String): Flow<List<DiscoverObservationRow>>

    @Query(
        "SELECT * FROM catalog_source_state WHERE source_key = :sourceKey AND media_type = :mediaType",
    )
    suspend fun sourceState(sourceKey: String, mediaType: String): CatalogSourceStateEntity?

    @Query(
        """
        SELECT story_id FROM discover_card
        WHERE source_key = :sourceKey AND media_type = :mediaType AND generation = :generation
        """,
    )
    suspend fun storyIdsForGeneration(sourceKey: String, mediaType: String, generation: Long): List<String>

    @Query("SELECT * FROM story_source_identity WHERE story_id = :storyId")
    suspend fun identityByStoryId(storyId: String): StorySourceIdentityEntity?

    @Query(
        """
        SELECT * FROM story_source_identity
        WHERE source_key = :sourceKey AND source_story_id = :sourceStoryId
        """,
    )
    suspend fun identityBySourceStoryKey(sourceKey: String, sourceStoryId: String): StorySourceIdentityEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIdentity(entity: StorySourceIdentityEntity): Long

    @Upsert
    suspend fun upsertSummaries(entities: List<StorySourceSummaryEntity>)

    @Upsert
    suspend fun upsertSourceState(entity: CatalogSourceStateEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDiscoverCards(entities: List<DiscoverCardEntity>)

    @Query(
        """
        DELETE FROM discover_card
        WHERE source_key = :sourceKey AND media_type = :mediaType AND generation = :generation
        """,
    )
    suspend fun deleteGeneration(sourceKey: String, mediaType: String, generation: Long)
}
