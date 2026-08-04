package app.openstory.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.database.DatabaseConverters
import app.openstory.common.FakeClock
import app.openstory.database.OpenStoryDatabase
import app.openstory.model.CanonicalStory
import app.openstory.model.ChapterId
import app.openstory.model.ChapterRelease
import app.openstory.model.ContentMappingId
import app.openstory.model.ContentType
import app.openstory.model.LanguageTag
import app.openstory.model.LibraryStatus
import app.openstory.model.PluginId
import app.openstory.model.ReleaseAvailability
import app.openstory.model.ReaderPosition
import app.openstory.model.ReadingProgress
import app.openstory.model.ReleaseId
import app.openstory.model.StoryId
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStoryRepositoryTest {

    @Test
    fun addMetadataOnlyStoryIsAtomic() = runTest {
        val database =
            createDatabase()

        try {
            val repository =
                RoomStoryRepository(database)

            repository.addToLibrary(
                story =
                    CanonicalStory(
                        id = StoryId("s1"),
                        contentType = ContentType.WEB_NOVEL,
                        preferredTitle = "Story",
                        aliases = emptySet(),
                        catalogEntries = emptyList(),
                    ),
                status = LibraryStatus.WANT_TO_READ,
            )

            assertEquals(
                "Story",
                repository
                    .observeStory(StoryId("s1"))
                    .first()
                    ?.preferredTitle,
            )
        }
        finally {
            database.close()
        }
    }

    @Test
    fun observeLibraryReturnsDomainEntries() = runTest {
        val database =
            createDatabase()

        try {
            val repository =
                RoomStoryRepository(
                    database = database,
                    clock = FakeClock(1_234L),
                )

            repository.addToLibrary(
                story =
                    CanonicalStory(
                        id = StoryId("library-story"),
                        contentType = ContentType.LIGHT_NOVEL,
                        preferredTitle = "Library Story",
                        aliases = emptySet(),
                        catalogEntries = emptyList(),
                    ),
                status = LibraryStatus.READING,
            )

            val entry =
                repository
                    .observeLibrary()
                    .first()
                    .single()

            assertEquals(
                StoryId("library-story"),
                entry.storyId,
            )
            assertEquals(
                LibraryStatus.READING,
                entry.status,
            )
            assertEquals(
                1_234L,
                entry.addedAtEpochMillis,
            )
            assertEquals(
                1_234L,
                entry.updatedAtEpochMillis,
            )
        }
        finally {
            database.close()
        }
    }
    @Test
    fun replacingOneSourceKeepsOtherPluginReleases() = runTest {
        val database =
            createDatabase()

        try {
            seedReleaseGraph(database)

            val repository =
                RoomStoryRepository(database)

            val mappingA =
                ContentMappingId("mapping-a")
            val mappingB =
                ContentMappingId("mapping-b")

            repository.replaceSourceReleases(
                mappingId = mappingA,
                releases =
                    listOf(
                        release(
                            id = "release-a-old",
                            mappingId = mappingA,
                            pluginId = "plugin-a",
                            externalId = "a-old",
                        ),
                    ),
            )

            repository.replaceSourceReleases(
                mappingId = mappingB,
                releases =
                    listOf(
                        release(
                            id = "release-b-kept",
                            mappingId = mappingB,
                            pluginId = "plugin-b",
                            externalId = "b-kept",
                        ),
                    ),
            )

            repository.replaceSourceReleases(
                mappingId = mappingA,
                releases =
                    listOf(
                        release(
                            id = "release-a-new",
                            mappingId = mappingA,
                            pluginId = "plugin-a",
                            externalId = "a-new",
                        ),
                    ),
            )

            assertEquals(
                setOf(
                    "release-a-new",
                    "release-b-kept",
                ),
                storedReleaseIds(database),
            )
        }
        finally {
            database.close()
        }
    }

    @Test
    fun staleProgressCannotOverwriteNewerProgress() = runTest {
        val database =
            createDatabase()

        try {
            seedReleaseGraph(database)

            val repository =
                RoomStoryRepository(database)

            repository.upsertProgress(
                ReadingProgress(
                    storyId = StoryId("story-1"),
                    chapterId = ChapterId("chapter-1"),
                    releaseId = null,
                    position =
                        ReaderPosition.Paragraph(
                            index = 9,
                            fraction = 0.9f,
                        ),
                    completed = true,
                    updatedAtEpochMillis = 2_000L,
                ),
            )

            repository.upsertProgress(
                ReadingProgress(
                    storyId = StoryId("story-1"),
                    chapterId = ChapterId("chapter-1"),
                    releaseId = null,
                    position = ReaderPosition.Start,
                    completed = false,
                    updatedAtEpochMillis = 1_000L,
                ),
            )

            val stored =
                storedProgress(database)

            assertEquals(
                2_000L,
                stored.updatedAtEpochMillis,
            )
            assertEquals(
                true,
                stored.completed,
            )
            assertEquals(
                ReaderPosition.Paragraph(
                    index = 9,
                    fraction = 0.9f,
                ),
                stored.position,
            )
        }
        finally {
            database.close()
        }
    }
    private fun createDatabase():
        OpenStoryDatabase =
        Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            OpenStoryDatabase::class.java,
        )
            .build()

    private fun seedReleaseGraph(
        database: OpenStoryDatabase,
    ) {
        val sqlite =
            database.openHelper.writableDatabase

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
            arrayOf(
                "story-1",
                "WEB_NOVEL",
                "Story",
                "[]",
            ),
        )

        insertMapping(
            database = database,
            mappingId = "mapping-a",
            pluginId = "plugin-a",
        )
        insertMapping(
            database = database,
            mappingId = "mapping-b",
            pluginId = "plugin-b",
        )

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
        val sqlite =
            database.openHelper.writableDatabase

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
            INSERT INTO story_content_mappings(
                story_id,
                content_mapping_id
            )
            VALUES(?, ?)
            """.trimIndent(),
            arrayOf(
                "story-1",
                mappingId,
            ),
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
            sourceUrl =
                "https://example.com/$externalId",
            language = LanguageTag("en"),
            title = "Chapter 1",
            volumeNumber = null,
            chapterNumber = BigDecimal.ONE,
            partNumber = null,
            translatorOrUploader = null,
            publishedAtEpochMillis = null,
            updatedAtEpochMillis = null,
            contentFingerprint = null,
            availability =
                ReleaseAvailability.AVAILABLE,
            fetchedAtEpochMillis = 1_000L,
        )

    private data class StoredProgress(
        val position: ReaderPosition,
        val completed: Boolean,
        val updatedAtEpochMillis: Long,
    )

    private fun storedProgress(
        database: OpenStoryDatabase,
    ): StoredProgress {
        val cursor =
            database.openHelper.writableDatabase
                .query(
                    """
                    SELECT
                        position,
                        completed,
                        updated_at_epoch_millis
                    FROM reading_progress
                    WHERE story_id = ?
                        AND chapter_id = ?
                    """.trimIndent(),
                    arrayOf(
                        "story-1",
                        "chapter-1",
                    ),
                )

        cursor.use {
            check(it.moveToFirst()) {
                "Expected stored reading progress"
            }

            return StoredProgress(
                position =
                    DatabaseConverters.toReaderPosition(
                        it.getString(
                            it.getColumnIndexOrThrow(
                                "position",
                            ),
                        ),
                    ),
                completed =
                    it.getInt(
                        it.getColumnIndexOrThrow(
                            "completed",
                        ),
                    ) != 0,
                updatedAtEpochMillis =
                    it.getLong(
                        it.getColumnIndexOrThrow(
                            "updated_at_epoch_millis",
                        ),
                    ),
            )
        }
    }
    private fun storedReleaseIds(
        database: OpenStoryDatabase,
    ): Set<String> {
        val ids =
            linkedSetOf<String>()

        database.openHelper.writableDatabase
            .query(
                """
                SELECT release_id
                FROM chapter_releases
                ORDER BY release_id
                """.trimIndent(),
            )
            .use { cursor ->
                val releaseIdIndex =
                    cursor.getColumnIndexOrThrow(
                        "release_id",
                    )

                while (cursor.moveToNext()) {
                    ids +=
                        cursor.getString(
                            releaseIdIndex,
                        )
                }
            }

        return ids
    }
}
