package app.openstory.storage.room.chapters

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
internal interface ChapterDao {
    @Transaction
    @Query("SELECT * FROM canonical_chapters ORDER BY story_id ASC, canonical_chapter_id ASC")
    fun observeAllGroups(): Flow<List<CanonicalChapterWithReleases>>

    @Transaction
    @Query(
        "SELECT * FROM canonical_chapters WHERE story_id IN (:storyIds) " +
            "ORDER BY story_id ASC, canonical_chapter_id ASC",
    )
    fun observeGroups(storyIds: Collection<String>): Flow<List<CanonicalChapterWithReleases>>

    @Transaction
    @Query("SELECT * FROM canonical_chapters WHERE story_id = :storyId")
    fun observeGroups(storyId: String): Flow<List<CanonicalChapterWithReleases>>

    @Transaction
    @Query("SELECT * FROM canonical_chapters WHERE story_id = :storyId")
    suspend fun groups(storyId: String): List<CanonicalChapterWithReleases>

    @Query("SELECT * FROM chapter_releases WHERE story_id = :storyId")
    suspend fun releases(storyId: String): List<ChapterReleaseEntity>

    @Query("SELECT * FROM chapter_releases WHERE chapter_release_id = :releaseId")
    suspend fun findRelease(releaseId: String): ChapterReleaseEntity?

    @Query("SELECT * FROM canonical_chapters WHERE canonical_chapter_id = :chapterId")
    suspend fun findChapter(chapterId: String): CanonicalChapterEntity?

    @Query(
        "SELECT * FROM chapter_releases WHERE canonical_chapter_id = :chapterId " +
            "ORDER BY language_tag, chapter_release_id",
    )
    suspend fun releasesForChapter(chapterId: String): List<ChapterReleaseEntity>

    @Query("SELECT * FROM chapter_aggregation_overrides WHERE story_id = :storyId")
    suspend fun overrides(storyId: String): List<ChapterAggregationOverrideEntity>

    @Upsert
    suspend fun upsertChapters(chapters: List<CanonicalChapterEntity>)

    @Upsert
    suspend fun upsertReleases(releases: List<ChapterReleaseEntity>)

    @Query("UPDATE chapter_releases SET canonical_chapter_id = NULL WHERE chapter_release_id IN (:releaseIds)")
    suspend fun unlink(releaseIds: Collection<String>)

    @Query(
        "UPDATE chapter_releases SET canonical_chapter_id = :chapterId " +
            "WHERE chapter_release_id = :releaseId",
    )
    suspend fun link(releaseId: String, chapterId: String): Int

    @Query("UPDATE canonical_chapters SET tombstoned = 1 WHERE canonical_chapter_id IN (:chapterIds)")
    suspend fun tombstone(chapterIds: Collection<String>)

    @Query("UPDATE canonical_chapters SET tombstoned = 0 WHERE canonical_chapter_id IN (:chapterIds)")
    suspend fun restore(chapterIds: Collection<String>)

    @Query("UPDATE canonical_chapters SET story_id = :survivorStoryId WHERE story_id = :retiredStoryId")
    suspend fun moveChapterOwnership(retiredStoryId: String, survivorStoryId: String): Int

    @Query("UPDATE chapter_releases SET story_id = :survivorStoryId WHERE story_id = :retiredStoryId")
    suspend fun moveReleaseOwnership(retiredStoryId: String, survivorStoryId: String): Int

    @Query(
        "UPDATE canonical_chapters SET story_id = :newStoryId WHERE canonical_chapter_id = :chapterId " +
            "AND story_id = :expectedStoryId",
    )
    suspend fun moveChapter(chapterId: String, expectedStoryId: String, newStoryId: String): Int

    @Query(
        "UPDATE chapter_releases SET story_id = :newStoryId WHERE chapter_release_id = :releaseId " +
            "AND story_id = :expectedStoryId",
    )
    suspend fun moveRelease(releaseId: String, expectedStoryId: String, newStoryId: String): Int

    @Query("DELETE FROM chapter_aggregation_overrides WHERE story_id IN (:storyIds)")
    suspend fun deleteOverridesForStories(storyIds: Collection<String>): Int

    @Upsert
    suspend fun upsertOverrides(overrides: List<ChapterAggregationOverrideEntity>)

}

@Dao
internal interface ChapterSyncDao {
    @Upsert
    suspend fun upsertOverride(override: ChapterAggregationOverrideEntity)

    @Upsert
    suspend fun upsert(state: ChapterSyncStateEntity)

    @Query(
        "SELECT * FROM chapter_sync_states WHERE story_id = :storyId " +
            "AND plugin_id = :pluginId AND source_story_id = :sourceStoryId",
    )
    suspend fun find(storyId: String, pluginId: String, sourceStoryId: String): ChapterSyncStateEntity?

    @Query(
        "SELECT * FROM chapter_sync_states WHERE story_id = :storyId " +
            "ORDER BY plugin_id, source_story_id",
    )
    suspend fun statesForStory(storyId: String): List<ChapterSyncStateEntity>
    @Query("DELETE FROM chapter_sync_states WHERE story_id IN (:storyIds)")
    suspend fun deleteStatesForStories(storyIds: Collection<String>): Int

    @Query("DELETE FROM chapter_sync_states WHERE story_id = :storyId")
    suspend fun deleteStatesForStory(storyId: String): Int

    @Query(
        "DELETE FROM chapter_sync_states WHERE story_id = :storyId " +
            "AND plugin_id = :pluginId AND source_story_id = :sourceStoryId",
    )
    suspend fun deleteState(storyId: String, pluginId: String, sourceStoryId: String): Int

    @Upsert
    suspend fun upsertAll(states: List<ChapterSyncStateEntity>)

}
