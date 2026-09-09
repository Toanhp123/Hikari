package app.openstory.catalog.storage.story

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

@Dao
internal interface StoryDetailDao {
    @Query(
        """
        SELECT
            identity.story_id AS story_id,
            identity.source_key AS source_key,
            identity.source_story_id AS source_story_id,
            summary.source_version AS summary_source_version,
            summary.title AS summary_title,
            summary.content_type AS summary_content_type,
            summary.cover_locator_type AS summary_cover_locator_type,
            summary.cover_locator_value AS summary_cover_locator_value,
            summary.cover_locator_aux AS summary_cover_locator_aux,
            summary.cover_revision AS summary_cover_revision,
            summary.rating_value AS summary_rating_value,
            summary.rating_scale AS summary_rating_scale,
            summary.publication_status_summary AS summary_publication_status_summary,
            summary.latest_update_epoch_ms AS summary_latest_update_epoch_ms,
            detail.source_version AS detail_source_version,
            detail.description AS detail_description,
            detail.publication_status AS detail_publication_status,
            detail.language AS detail_language,
            detail.fetched_at_epoch_ms AS detail_fetched_at_epoch_ms
        FROM story_source_identity AS identity
        INNER JOIN story_source_summary AS summary ON summary.story_id = identity.story_id
        LEFT JOIN story_detail AS detail ON detail.story_id = identity.story_id
        WHERE identity.story_id = :storyId
            AND identity.source_key = :sourceKey
            AND identity.source_story_id = :sourceStoryId
        """,
    )
    suspend fun storyRow(
        storyId: String,
        sourceKey: String,
        sourceStoryId: String,
    ): StoryDetailObservationRow?

    @Query("SELECT * FROM story_author WHERE story_id = :storyId ORDER BY position")
    suspend fun authors(storyId: String): List<StoryAuthorEntity>

    @Query("SELECT * FROM story_artist WHERE story_id = :storyId ORDER BY position")
    suspend fun artists(storyId: String): List<StoryArtistEntity>

    @Query("SELECT * FROM story_genre WHERE story_id = :storyId ORDER BY position")
    suspend fun genres(storyId: String): List<StoryGenreEntity>

    @Upsert
    suspend fun upsertSummary(entity: StorySourceSummaryEntity)

    @Query("SELECT last_accessed_epoch_ms FROM story_detail WHERE story_id = :storyId")
    suspend fun lastAccessedEpochMs(storyId: String): Long?

    @Upsert
    suspend fun upsertDetail(entity: StoryDetailEntity)

    @Query("DELETE FROM story_author WHERE story_id = :storyId")
    suspend fun deleteAuthors(storyId: String)

    @Query("DELETE FROM story_artist WHERE story_id = :storyId")
    suspend fun deleteArtists(storyId: String)

    @Query("DELETE FROM story_genre WHERE story_id = :storyId")
    suspend fun deleteGenres(storyId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAuthors(entities: List<StoryAuthorEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArtists(entities: List<StoryArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGenres(entities: List<StoryGenreEntity>)

    @Query(
        """
        UPDATE story_detail
        SET last_accessed_epoch_ms = MAX(last_accessed_epoch_ms, :accessedAtEpochMs)
        WHERE story_id = :storyId
        """,
    )
    suspend fun touchAccess(storyId: String, accessedAtEpochMs: Long): Int
}

internal data class StoryDetailRecord(
    val row: StoryDetailObservationRow,
    val authors: List<StoryAuthorEntity>,
    val artists: List<StoryArtistEntity>,
    val genres: List<StoryGenreEntity>,
)

internal data class StoryDetailObservationRow(
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "source_story_id") val sourceStoryId: String,
    @ColumnInfo(name = "summary_source_version") val summarySourceVersion: String,
    @ColumnInfo(name = "summary_title") val summaryTitle: String,
    @ColumnInfo(name = "summary_content_type") val summaryContentType: String,
    @ColumnInfo(name = "summary_cover_locator_type") val summaryCoverLocatorType: String?,
    @ColumnInfo(name = "summary_cover_locator_value") val summaryCoverLocatorValue: String?,
    @ColumnInfo(name = "summary_cover_locator_aux") val summaryCoverLocatorAux: String?,
    @ColumnInfo(name = "summary_cover_revision") val summaryCoverRevision: String?,
    @ColumnInfo(name = "summary_rating_value") val summaryRatingValue: Double?,
    @ColumnInfo(name = "summary_rating_scale") val summaryRatingScale: Double?,
    @ColumnInfo(name = "summary_publication_status_summary") val summaryPublicationStatusSummary: String?,
    @ColumnInfo(name = "summary_latest_update_epoch_ms") val summaryLatestUpdateEpochMs: Long?,
    @ColumnInfo(name = "detail_source_version") val detailSourceVersion: String?,
    @ColumnInfo(name = "detail_description") val detailDescription: String?,
    @ColumnInfo(name = "detail_publication_status") val detailPublicationStatus: String?,
    @ColumnInfo(name = "detail_language") val detailLanguage: String?,
    @ColumnInfo(name = "detail_fetched_at_epoch_ms") val detailFetchedAtEpochMs: Long?,
)
