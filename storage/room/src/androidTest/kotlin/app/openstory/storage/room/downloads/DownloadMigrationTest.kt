package app.openstory.storage.room.downloads

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
class DownloadMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenStoryDatabase::class.java,
    )

    @Test
    fun migrationFiveToSixPreservesProgressAndAddsCacheAndDownloadMetadata() {
        helper.createDatabase(TEST_DATABASE, 5).apply {
            execSQL("INSERT INTO stories (story_id, content_type) VALUES ('story:1', 'MANGA')")
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 6, true, RoomMigrations.MIGRATION_5_6).use { database ->
            val storyCount = database.query("SELECT COUNT(*) FROM stories").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(1, storyCount)

            database.execSQL(
                "INSERT INTO chapter_storage_entries " +
                    "(namespace, chapter_release_id, content_fingerprint, checksum, size_bytes, " +
                    "last_accessed_at_epoch_millis, pinned, current, download_state, failure_reason, " +
                    "attempt, updated_at_epoch_millis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "EXPLICIT_DOWNLOAD", "release:1", "fingerprint", null, 0L, 10L,
                    1, 0, "QUEUED", null, 0, 10L,
                ),
            )
            val state = database.query("SELECT download_state FROM chapter_storage_entries").use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
            assertEquals("QUEUED", state)
        }
    }

    private companion object {
        const val TEST_DATABASE = "download-migration-test"
    }
}
