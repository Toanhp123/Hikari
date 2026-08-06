package app.openstory.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class StoryPurgeRepositoryTest {

    @Test
    fun purgeStoryRemovesCanonicalGraphAndOrphanSourceRecords() = runTest {
        val database = createDatabase()

        try {
            seedReleaseGraph(database)
            seedCatalogEntry(database)
            val repository = RoomStoryRepository(database)
            val mapping = ContentMappingId("mapping-a")

            repository.replaceSourceReleases(
                mappingId = mapping,
                releases = listOf(
                    release(
                        id = "release-a",
                        mappingId = mapping,
                        pluginId = "plugin-a",
                        externalId = "a",
                    ),
                ),
            )
            repository.upsertProgress(
                ReadingProgress(
                    storyId = StoryId("story-1"),
                    chapterId = ChapterId("chapter-1"),
                    releaseId = ReleaseId("release-a"),
                    position = ReaderPosition.Paragraph(2, 0.5f),
                    completed = false,
                    updatedAtEpochMillis = 2_000L,
                ),
            )

            repository.purgeStory(StoryId("story-1"))

            listOf(
                "canonical_stories",
                "catalog_entries",
                "content_mappings",
                "canonical_chapters",
                "chapter_releases",
                "reading_progress",
            ).forEach { table ->
                assertEquals(0, rowCount(database, table))
            }
            assertEquals(
                false,
                database.openHelper.writableDatabase
                    .query("PRAGMA foreign_key_check")
                    .use { cursor -> cursor.moveToFirst() },
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

    private fun seedCatalogEntry(database: OpenStoryDatabase) {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            """
            INSERT INTO catalog_entries(
                catalog_entry_id,
                catalog_plugin_id,
                title,
                description,
                score,
                score_scale,
                external_story_id,
                source_url,
                authors_json,
                genres_json,
                cover_reference,
                publication_status
            )
            VALUES(?, ?, ?, NULL, NULL, NULL, ?, NULL, '[]', '[]', NULL, NULL)
            """.trimIndent(),
            arrayOf(
                "catalog.example:story-1",
                "catalog.example",
                "Story",
                "story-1",
            ),
        )
        sqlite.execSQL(
            """
            INSERT INTO story_catalog_entries(story_id, catalog_entry_id)
            VALUES(?, ?)
            """.trimIndent(),
            arrayOf("story-1", "catalog.example:story-1"),
        )
    }

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

    private fun rowCount(
        database: OpenStoryDatabase,
        table: String,
    ): Int = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM $table")
        .use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
}
