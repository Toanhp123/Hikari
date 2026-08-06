package app.openstory.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.common.AppResult
import app.openstory.common.FakeClock
import app.openstory.database.repository.RoomStoryRepository
import app.openstory.model.CanonicalStory
import app.openstory.model.ChapterId
import app.openstory.model.ChapterRelease
import app.openstory.model.ContentMappingId
import app.openstory.model.ContentType
import app.openstory.model.LanguageTag
import app.openstory.model.LibraryStatus
import app.openstory.model.PluginId
import app.openstory.model.ReaderPosition
import app.openstory.model.ReadingProgress
import app.openstory.model.ReleaseAvailability
import app.openstory.model.ReleaseId
import app.openstory.model.StoryId
import java.math.BigDecimal
import kotlinx.coroutines.flow.first
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal const val METADATA_DATABASE_NAME =
    "wave-02-checkpoint-metadata.db"

internal const val RELEASE_DATABASE_NAME =
    "wave-02-checkpoint-releases.db"

internal const val SOURCE_REMOVAL_DATABASE_NAME =
    "wave-02-checkpoint-source-removal.db"

internal suspend fun <T> withFreshCheckpointDatabase(
    databaseName: String,
    block: suspend (Context) -> T,
): T {
    val context =
        ApplicationProvider
            .getApplicationContext<Context>()

    context.deleteDatabase(databaseName)

    return try {
        block(context)
    }
    finally {
        context.deleteDatabase(databaseName)
    }
}

internal fun metadataOnlyStory():
    CanonicalStory =
    CanonicalStory(
        id =
            StoryId(
                "checkpoint-metadata-story",
            ),
        contentType =
            ContentType.WEB_NOVEL,
        preferredTitle =
            "Checkpoint Metadata Story",
        aliases = emptySet(),
        catalogEntries = emptyList(),
    )

internal suspend fun persistMetadataStory(
    context: Context,
    story: CanonicalStory,
) {
    withCheckpointDatabase(
        context = context,
        databaseName =
            METADATA_DATABASE_NAME,
    ) { database ->
        RoomStoryRepository(
            database = database,
            clock = FakeClock(1_000L),
        ).addToLibrary(
            story = story,
            status =
                LibraryStatus.WANT_TO_READ,
        )
    }
}

internal suspend fun assertMetadataStoryAfterReopen(
    context: Context,
    story: CanonicalStory,
) {
    withCheckpointDatabase(
        context = context,
        databaseName =
            METADATA_DATABASE_NAME,
    ) { database ->
        val repository =
            RoomStoryRepository(database)

        assertEquals(
            expected = story,
            actual =
                repository
                    .observeStory(story.id)
                    .first(),
        )

        val libraryEntry =
            repository
                .observeLibrary()
                .first()
                .single()

        assertEquals(
            story.id,
            libraryEntry.storyId,
        )
        assertEquals(
            LibraryStatus.WANT_TO_READ,
            libraryEntry.status,
        )
        assertEquals(
            1_000L,
            libraryEntry.addedAtEpochMillis,
        )
    }
}

internal suspend fun persistTwoSourceReleases(
    context: Context,
) {
    withCheckpointDatabase(
        context = context,
        databaseName =
            RELEASE_DATABASE_NAME,
    ) { database ->
        seedReleaseGraph(database)

        val repository =
            RoomStoryRepository(database)

        repository.replaceSourceReleases(
            mappingId =
                ContentMappingId("mapping-a"),
            releases =
                listOf(
                    checkpointRelease(
                        id = "release-a",
                        mappingId = "mapping-a",
                        pluginId = "plugin-a",
                    ),
                ),
        )

        repository.replaceSourceReleases(
            mappingId =
                ContentMappingId("mapping-b"),
            releases =
                listOf(
                    checkpointRelease(
                        id = "release-b",
                        mappingId = "mapping-b",
                        pluginId = "plugin-b",
                    ),
                ),
        )
    }
}

internal suspend fun assertTwoSourceReleasesAfterReopen(
    context: Context,
) {
    withCheckpointDatabase(
        context = context,
        databaseName =
            RELEASE_DATABASE_NAME,
    ) { database ->
        assertEquals(
            expected =
                setOf(
                    StoredReleaseLink(
                        chapterId =
                            "checkpoint-chapter",
                        releaseId = "release-a",
                        mappingId = "mapping-a",
                        pluginId = "plugin-a",
                    ),
                    StoredReleaseLink(
                        chapterId =
                            "checkpoint-chapter",
                        releaseId = "release-b",
                        mappingId = "mapping-b",
                        pluginId = "plugin-b",
                    ),
                ),
            actual =
                storedReleaseLinks(database),
        )
    }
}

internal suspend fun persistProgressForSource(
    context: Context,
) {
    withCheckpointDatabase(
        context = context,
        databaseName =
            SOURCE_REMOVAL_DATABASE_NAME,
    ) { database ->
        seedReleaseGraph(database)

        val repository =
            RoomStoryRepository(database)

        repository.replaceSourceReleases(
            mappingId =
                ContentMappingId("mapping-a"),
            releases =
                listOf(
                    checkpointRelease(
                        id = "release-a",
                        mappingId = "mapping-a",
                        pluginId = "plugin-a",
                    ),
                ),
        )

        repository.upsertProgress(
            ReadingProgress(
                storyId =
                    StoryId(
                        "checkpoint-story",
                    ),
                chapterId =
                    ChapterId(
                        "checkpoint-chapter",
                    ),
                releaseId =
                    ReleaseId("release-a"),
                position =
                    ReaderPosition.Paragraph(
                        index = 4,
                        fraction = 0.5f,
                    ),
                completed = true,
                updatedAtEpochMillis = 2_000L,
            ),
        )
    }
}

internal suspend fun removeSource(
    context: Context,
) {
    withCheckpointDatabase(
        context = context,
        databaseName =
            SOURCE_REMOVAL_DATABASE_NAME,
    ) { database ->
        RoomStoryRepository(database)
            .replaceSourceReleases(
                mappingId =
                    ContentMappingId(
                        "mapping-a",
                    ),
                releases = emptyList(),
            )

        database.openHelper.writableDatabase
            .execSQL(
                """
                DELETE FROM content_mappings
                WHERE content_mapping_id = ?
                """.trimIndent(),
                arrayOf("mapping-a"),
            )
    }
}

internal suspend fun assertSourceRemovalAfterReopen(
    context: Context,
) {
    withCheckpointDatabase(
        context = context,
        databaseName =
            SOURCE_REMOVAL_DATABASE_NAME,
    ) { database ->
        assertCanonicalDataPreserved(database)
        assertSourceDataRemoved(database)
        assertCanonicalProgressPreserved(database)
        assertNoForeignKeyViolations(database)
    }
}

private fun assertCanonicalDataPreserved(
    database: OpenStoryDatabase,
) {
    assertEquals(
        1,
        rowCount(
            database,
            """
            SELECT COUNT(*)
            FROM canonical_stories
            WHERE story_id =
                'checkpoint-story'
            """,
        ),
    )

    assertEquals(
        1,
        rowCount(
            database,
            """
            SELECT COUNT(*)
            FROM canonical_chapters
            WHERE chapter_id =
                'checkpoint-chapter'
            """,
        ),
    )
}

private fun assertSourceDataRemoved(
    database: OpenStoryDatabase,
) {
    assertEquals(
        0,
        rowCount(
            database,
            """
            SELECT COUNT(*)
            FROM content_mappings
            WHERE content_mapping_id =
                'mapping-a'
            """,
        ),
    )

    assertEquals(
        0,
        rowCount(
            database,
            """
            SELECT COUNT(*)
            FROM chapter_releases
            WHERE content_mapping_id =
                'mapping-a'
            """,
        ),
    )
}

private fun assertCanonicalProgressPreserved(
    database: OpenStoryDatabase,
) {
    assertEquals(
        expected =
            StoredProgress(
                releaseId = null,
                position =
                    ReaderPosition.Paragraph(
                        index = 4,
                        fraction = 0.5f,
                    ),
                completed = true,
                updatedAtEpochMillis = 2_000L,
            ),
        actual =
            storedProgress(database),
    )
}

private fun assertNoForeignKeyViolations(
    database: OpenStoryDatabase,
) {
    database.openHelper.writableDatabase
        .query("PRAGMA foreign_key_check")
        .use { cursor ->
            assertEquals(
                expected = false,
                actual = cursor.moveToFirst(),
                message =
                    "Source removal left a foreign-key violation",
            )
        }
}

private fun seedReleaseGraph(
    database: OpenStoryDatabase,
) {
    insertStory(database)

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

    insertChapter(database)
}

private fun insertStory(
    database: OpenStoryDatabase,
) {
    database.openHelper.writableDatabase
        .execSQL(
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
                "checkpoint-story",
                "WEB_NOVEL",
                "Checkpoint Story",
                "[]",
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
            "checkpoint-story",
            mappingId,
        ),
    )
}

private fun insertChapter(
    database: OpenStoryDatabase,
) {
    database.openHelper.writableDatabase
        .execSQL(
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
                "checkpoint-chapter",
                "checkpoint-story",
                "NUMBERED",
                "1",
                "Chapter 1",
                "00000001",
            ),
        )
}

private fun checkpointRelease(
    id: String,
    mappingId: String,
    pluginId: String,
): ChapterRelease =
    ChapterRelease(
        id = ReleaseId(id),
        chapterId =
            ChapterId(
                "checkpoint-chapter",
            ),
        contentMappingId =
            ContentMappingId(mappingId),
        pluginId = PluginId(pluginId),
        externalReleaseId =
            "external-$id",
        sourceUrl =
            "https://example.com/$id",
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

private fun storedReleaseLinks(
    database: OpenStoryDatabase,
): Set<StoredReleaseLink> {
    val links =
        linkedSetOf<StoredReleaseLink>()

    database.openHelper.writableDatabase
        .query(
            """
            SELECT
                canonical_chapter_releases.chapter_id,
                chapter_releases.release_id,
                chapter_releases.content_mapping_id,
                chapter_releases.plugin_id
            FROM canonical_chapter_releases
            INNER JOIN chapter_releases
                ON chapter_releases.release_id =
                    canonical_chapter_releases.release_id
            ORDER BY chapter_releases.release_id
            """.trimIndent(),
        )
        .use { cursor ->
            while (cursor.moveToNext()) {
                links +=
                    StoredReleaseLink(
                        chapterId =
                            cursor.getString(0),
                        releaseId =
                            cursor.getString(1),
                        mappingId =
                            cursor.getString(2),
                        pluginId =
                            cursor.getString(3),
                    )
            }
        }

    return links
}

private fun rowCount(
    database: OpenStoryDatabase,
    query: String,
): Int =
    database.openHelper.writableDatabase
        .query(query.trimIndent())
        .use { cursor ->
            check(cursor.moveToFirst()) {
                "Expected count result"
            }

            cursor.getInt(0)
        }

private fun storedProgress(
    database: OpenStoryDatabase,
): StoredProgress =
    database.openHelper.writableDatabase
        .query(
            """
            SELECT
                release_id,
                position,
                completed,
                updated_at_epoch_millis
            FROM reading_progress
            WHERE story_id =
                'checkpoint-story'
                AND chapter_id =
                    'checkpoint-chapter'
            """.trimIndent(),
        )
        .use { cursor ->
            check(cursor.moveToFirst()) {
                "Expected canonical progress"
            }

            StoredProgress(
                releaseId =
                    if (cursor.isNull(0)) {
                        null
                    }
                    else {
                        cursor.getString(0)
                    },
                position =
                    DatabaseConverters
                        .toReaderPosition(
                            cursor.getString(1),
                        ),
                completed =
                    cursor.getInt(2) != 0,
                updatedAtEpochMillis =
                    cursor.getLong(3),
            )
        }

private suspend fun <T> withCheckpointDatabase(
    context: Context,
    databaseName: String,
    block:
        suspend (
            OpenStoryDatabase,
        ) -> T,
): T {
    val database =
        Room.databaseBuilder(
            context,
            OpenStoryDatabase::class.java,
            databaseName,
        ).build()

    return try {
        block(database)
    }
    finally {
        database.close()
    }
}

private data class StoredProgress(
    val releaseId: String?,
    val position: ReaderPosition,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
)

private data class StoredReleaseLink(
    val chapterId: String,
    val releaseId: String,
    val mappingId: String,
    val pluginId: String,
)
