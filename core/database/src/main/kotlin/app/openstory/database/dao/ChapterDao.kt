package app.openstory.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.openstory.database.entity.CanonicalChapterReleaseEntity
import app.openstory.database.entity.ChapterReleaseEntity

@Dao
internal abstract class ChapterDao {

    @Query(
        """
        DELETE FROM canonical_chapter_releases
        WHERE release_id IN (
            SELECT release_id
            FROM chapter_releases
            WHERE content_mapping_id = :mappingId
        )
        """,
    )
    protected abstract suspend fun deleteChapterLinks(
        mappingId: String,
    )

    @Query(
        """
        DELETE FROM chapter_releases
        WHERE content_mapping_id = :mappingId
        """,
    )
    protected abstract suspend fun deleteReleases(
        mappingId: String,
    )

    @Upsert
    protected abstract suspend fun upsertReleases(
        releases: List<ChapterReleaseEntity>,
    )

    @Upsert
    protected abstract suspend fun upsertChapterLinks(
        links: List<CanonicalChapterReleaseEntity>,
    )

    @Transaction
    open suspend fun replaceSourceReleases(
        mappingId: String,
        releases: List<ChapterReleaseEntity>,
        links: List<CanonicalChapterReleaseEntity>,
    ) {
        deleteChapterLinks(mappingId)
        deleteReleases(mappingId)

        if (releases.isNotEmpty()) {
            upsertReleases(releases)
            upsertChapterLinks(links)
        }
    }
}
