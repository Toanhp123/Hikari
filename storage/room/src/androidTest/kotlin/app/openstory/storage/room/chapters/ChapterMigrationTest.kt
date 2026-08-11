package app.openstory.storage.room.chapters

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
class ChapterMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenStoryDatabase::class.java,
    )

    @Test
    fun migrationThreeToFourPreservesMappingsAndAddsChapterGraph() {
        helper.createDatabase(TEST_DATABASE, 3).apply {
            execSQL("INSERT INTO stories (story_id, content_type) VALUES (?, ?)", arrayOf(STORY_ID, "MANGA"))
            execSQL(
                "INSERT INTO content_mappings " +
                    "(story_id, plugin_id, source_story_id, origin, policy_version, updated_at_epoch_millis) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(STORY_ID, PLUGIN_ID, SOURCE_STORY_ID, "USER_APPROVED", 1, 10L),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            4,
            true,
            RoomMigrations.MIGRATION_3_4,
        ).use { database ->
            val mappingCount = database.query("SELECT COUNT(*) FROM content_mappings").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(1, mappingCount)

            database.execSQL(
                "INSERT INTO canonical_chapters " +
                    "(canonical_chapter_id, story_id, kind, volume, chapter, part, normalized_title, " +
                    "display_label, tombstoned) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>("chapter-1", STORY_ID, "NUMBERED", null, "1", null, null, "Chapter 1", 0),
            )
            val chapterCount = database.query("SELECT COUNT(*) FROM canonical_chapters").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(1, chapterCount)
        }
    }

    private companion object {
        const val TEST_DATABASE = "chapter-migration-test"
        const val STORY_ID = "story:chapters"
        const val PLUGIN_ID = "org.example.content"
        const val SOURCE_STORY_ID = "source-story"
    }
}
