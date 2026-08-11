package app.openstory.storage.room.reader

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.RoomMigrations
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadingProgressMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenStoryDatabase::class.java,
    )

    @Test
    fun migrationFourToFivePreservesChapterGraphAndAddsExactProgress() {
        helper.createDatabase(TEST_DATABASE, 4).apply {
            execSQL("INSERT INTO stories (story_id, content_type) VALUES (?, ?)", arrayOf(STORY_ID, "MANGA"))
            execSQL(
                "INSERT INTO canonical_chapters " +
                    "(canonical_chapter_id, story_id, kind, display_label, tombstoned) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf<Any>(CHAPTER_ID, STORY_ID, "NUMBERED", "Chapter 1", 0),
            )
            execSQL(
                "INSERT INTO chapter_releases " +
                    "(chapter_release_id, story_id, plugin_id, source_story_id, source_release_id, " +
                    "display_label, kind, language_tag, canonical_chapter_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf(
                    RELEASE_ID, STORY_ID, "org.example.content", "source-story", "source-release",
                    "Chapter 1", "NUMBERED", "en", CHAPTER_ID,
                ),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            RoomMigrations.MIGRATION_4_5,
        ).use { database ->
            database.execSQL(
                "INSERT INTO reading_progress " +
                    "(story_id, canonical_chapter_id, chapter_release_id, content_fingerprint, block_id, " +
                    "character_offset, fraction, completed_at_epoch_millis, updated_at_epoch_millis) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(STORY_ID, CHAPTER_ID, RELEASE_ID, "fingerprint", "block", 4, 0.5, null, 10L),
            )
            val fraction = database.query("SELECT fraction FROM reading_progress").use { cursor ->
                cursor.moveToFirst()
                cursor.getDouble(0)
            }
            assertEquals(0.5, fraction)
        }
    }

    private companion object {
        const val TEST_DATABASE = "reading-progress-migration-test"
        const val STORY_ID = "story:reader"
        const val CHAPTER_ID = "chapter:reader:1"
        const val RELEASE_ID = "release:reader:1"
    }
}
