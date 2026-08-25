package app.openstory.storage.room.chapters

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.RoomMigrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Wave10Migration10To11Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenStoryDatabase::class.java,
    )

    @Test
    fun migrationPreservesSchemaTenRowsAndAddsOnlyDurableNotificationState() {
        helper.createDatabase(DATABASE_NAME, 10).apply {
            execSQL("INSERT INTO stories (story_id, content_type) VALUES ('story:1', 'MANGA')")
            execSQL(
                "INSERT INTO library_entries " +
                    "(story_id, status, added_at_epoch_millis, updated_at_epoch_millis) " +
                    "VALUES ('story:1', 'READING', 1, 2)",
            )
            execSQL(
                "INSERT INTO canonical_chapters " +
                    "(canonical_chapter_id, story_id, kind, volume, chapter, part, normalized_title, " +
                    "display_label, tombstoned) VALUES " +
                    "('chapter:1', 'story:1', 'NUMBERED', NULL, '1', NULL, NULL, 'Chapter 1', 0)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            11,
            true,
            RoomMigrations.MIGRATION_10_11,
        ).use { database ->
            assertEquals(1, database.count("library_entries"))
            assertEquals(1, database.count("canonical_chapters"))
            assertEquals(0, database.count("chapter_change_events"))
            assertEquals(0, database.count("notification_deliveries"))
            database.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
            database.query("PRAGMA index_list('notification_deliveries')").use { cursor ->
                val names = buildSet {
                    while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                }
                assertTrue("index_notification_deliveries_notification_id" in names)
                assertTrue("index_notification_deliveries_claim_expires_at_epoch_millis" in names)
            }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val DATABASE_NAME = "wave-10-migration-10-11"
    }
}
