package app.openstory.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.database.DatabaseConverters
import app.openstory.database.OpenStoryDatabase
import app.openstory.model.ChapterId
import app.openstory.model.ChapterRelease
import app.openstory.model.ContentMappingId
import app.openstory.model.LanguageTag
import app.openstory.model.PluginId
import app.openstory.model.ReaderPosition
import app.openstory.model.ReadingProgress
import app.openstory.model.ReleaseAvailability
import app.openstory.model.ReleaseId
import app.openstory.model.StoryId
import java.math.BigDecimal
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStoryRepositorySafetyTest {

    @Test
    fun equalTimestampCannotOverwriteExistingProgress() = runTest {
        val database = createDatabase()

        try {
            seedReleaseGraph(database)
            val repository = RoomStoryRepository(database)

            repository.upsertProgress(
                progress(
                    position = ReaderPosition.Paragraph(9, 0.9f),
                    completed = true,
                ),
            )
            repository.upsertProgress(
                progress(
                    position = ReaderPosition.Start,
                    completed = false,
                ),
            )

            val stored = storedProgress(database)
            assertEquals(true, stored.completed)
            assertEquals(
                ReaderPosition.Paragraph(9, 0.9f),
                stored.position,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun releaseMappingMismatchReturnsValidationFailure() = runTest {
        val database = createDatabase()

        try {
            val result = RoomStoryRepository(database)
                .replaceSourceReleases(
                    mappingId = ContentMappingId("mapping-a"),
                    releases = listOf(
                        release(
                            id = "release-wrong-mapping",
                            mappingId = ContentMappingId("mapping-b"),
                            pluginId = "plugin-b",
                            externalId = "wrong",
                        ),
                    ),
                )

            assertEquals(
                AppResult.Failure(
                    AppError.Validation(
                        code = "storage.release_mapping_mismatch",
                    ),
                ),
                result,
            )
        } finally {
            database.close()
        }
    }

    private fun createDatabase(): OpenStoryDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OpenStoryDatabase::class.java,
        ).build()

    private fun seedReleaseGraph(database: OpenStoryDatabase) {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            """
            INSERT INTO canonical_stories(
                story_id,
                content_type,
                preferred_title,
                aliases_json
            )
            VALUES(?, ?, ?, ?)
            """.trimIndent(),
            arrayOf("story-1", "WEB_NOVEL", "Story", "[]"),
        )
        insertMapping(database, "mapping-a", "plugin-a")
        insertMapping(database, "mapping-b", "plugin-b")
        sqlite.execSQL(
            """
            INSERT INTO canonical_chapters(
                chapter_id,
                story_id,
                kind,
                volume_number,
                chapter_number,
                part_number,
                normalized_title,
                sort_key,
                first_known_published_at_epoch_millis
            )
            VALUES(?, ?, ?, NULL, ?, NULL, ?, ?, NULL)
            """.trimIndent(),
            arrayOf(
                "chapter-1",
                "story-1",
                "NUMBERED",
                "1",
                "Chapter 1",
                "00000001",
            ),
        )
    }

    private fun insertMapping(
        database: OpenStoryDatabase,
        mappingId: String,
        pluginId: String,
    ) {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            """
            INSERT INTO content_mappings(
                content_mapping_id,
                plugin_id,
                external_story_id,
                source_url,
                language,
                origin,
                confidence,
                user_locked,
                enabled,
                last_successful_sync_at_epoch_millis,
                next_eligible_sync_at_epoch_millis,
                failure_state
            )
            VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL)
            """.trimIndent(),
            arrayOf<Any?>(
                mappingId,
                pluginId,
                "external-$mappingId",
                "https://example.com/$mappingId",
                "en",
                "PLUGIN",
                1.0,
                0,
                1,
            ),
        )
        sqlite.execSQL(
            """
            INSERT INTO story_content_mappings(story_id, content_mapping_id)
            VALUES(?, ?)
            """.trimIndent(),
            arrayOf("story-1", mappingId),
        )
    }

    private fun progress(
        position: ReaderPosition,
        completed: Boolean,
    ): ReadingProgress =
        ReadingProgress(
            storyId = StoryId("story-1"),
            chapterId = ChapterId("chapter-1"),
            releaseId = null,
            position = position,
            completed = completed,
            updatedAtEpochMillis = 2_000L,
        )

    private fun release(
        id: String,
        mappingId: ContentMappingId,
        pluginId: String,
        externalId: String,
    ): ChapterRelease =
        ChapterRelease(
            id = ReleaseId(id),
            chapterId = ChapterId("chapter-1"),
            contentMappingId = mappingId,
            pluginId = PluginId(pluginId),
            externalReleaseId = externalId,
            sourceUrl = "https://example.com/$externalId",
            language = LanguageTag("en"),
            title = "Chapter 1",
            volumeNumber = null,
            chapterNumber = BigDecimal.ONE,
            partNumber = null,
            translatorOrUploader = null,
            publishedAtEpochMillis = null,
            updatedAtEpochMillis = null,
            contentFingerprint = null,
            availability = ReleaseAvailability.AVAILABLE,
            fetchedAtEpochMillis = 1_000L,
        )

    private fun storedProgress(database: OpenStoryDatabase): StoredProgress =
        database.openHelper.writableDatabase.query(
            """
            SELECT position, completed
            FROM reading_progress
            WHERE story_id = ? AND chapter_id = ?
            """.trimIndent(),
            arrayOf("story-1", "chapter-1"),
        ).use { cursor ->
            check(cursor.moveToFirst())
            StoredProgress(
                position = DatabaseConverters.toReaderPosition(cursor.getString(0)),
                completed = cursor.getInt(1) != 0,
            )
        }

    private data class StoredProgress(
        val position: ReaderPosition,
        val completed: Boolean,
    )
}
