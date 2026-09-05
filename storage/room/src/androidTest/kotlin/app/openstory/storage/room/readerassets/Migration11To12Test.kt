package app.openstory.storage.room.readerassets

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.RoomMigrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration11To12Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenStoryDatabase::class.java,
    )

    @Test
    fun migrationPreservesSchemaElevenDataAndAddsEmptySecretFreeAssetMetadata() {
        helper.createDatabase(DATABASE_NAME, 11).apply {
            execSQL("INSERT INTO stories (story_id, content_type) VALUES ('story:1', 'MANGA')")
            execSQL(
                "INSERT INTO library_entries " +
                    "(story_id, status, added_at_epoch_millis, updated_at_epoch_millis) " +
                    "VALUES ('story:1', 'READING', 1, 2)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            12,
            true,
            RoomMigrations.MIGRATION_11_12,
        ).use { database ->
            assertEquals(1, database.count("stories"))
            assertEquals(1, database.count("library_entries"))
            assertEquals(0, database.count("reader_asset_entries"))
            val columns = database.query("PRAGMA table_info('reader_asset_entries')").use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
                    }
                }
            }
            assertTrue("local_blob_checksum" in columns)
            assertTrue("source_integrity_hash" in columns)
            listOf("url", "token", "credential", "authorization", "cookie", "raw_scope").forEach {
                forbidden -> assertFalse(columns.any { it.contains(forbidden, ignoreCase = true) })
            }
            database.query("PRAGMA foreign_key_check").use { cursor -> assertEquals(0, cursor.count) }
        }
    }

    private fun androidx.sqlite.db.SupportSQLiteDatabase.count(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val DATABASE_NAME = "ricc-migration-11-12"
    }
}
