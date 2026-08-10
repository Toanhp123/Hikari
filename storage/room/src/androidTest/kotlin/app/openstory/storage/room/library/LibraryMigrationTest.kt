package app.openstory.storage.room.library

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryStatus
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.RoomMigrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenStoryDatabase::class.java,
    )

    @Test
    fun migrationOneToTwoAddsLibraryWithoutChangingExistingCatalogRows() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                "INSERT INTO stories (story_id, content_type) VALUES (?, ?)",
                arrayOf(STORY_ID, "LIGHT_NOVEL"),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            RoomMigrations.MIGRATION_1_2,
        ).use { database ->
            val storyCount = database.query(
                "SELECT COUNT(*) FROM stories WHERE story_id = ?",
                arrayOf(STORY_ID),
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(1, storyCount)

            database.execSQL(
                "INSERT INTO library_entries " +
                    "(story_id, status, added_at_epoch_millis, updated_at_epoch_millis) " +
                    "VALUES (?, ?, ?, ?)",
                arrayOf<Any?>(STORY_ID, LibraryStatus.WANT_TO_READ.name, 10L, 10L),
            )
            val status = database.query(
                "SELECT status FROM library_entries WHERE story_id = ?",
                arrayOf(STORY_ID),
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
            assertEquals(LibraryStatus.WANT_TO_READ.name, status)
        }
    }

    @Test
    fun roomRepositoryAddIsIdempotentAndPreservesStatus() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            OpenStoryDatabase::class.java,
        ).build()
        try {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO stories (story_id, content_type) VALUES (?, ?)",
                arrayOf(STORY_ID, "LIGHT_NOVEL"),
            )
            val repository = RoomLibraryRepository(database)
            val storyId = StoryId(STORY_ID)

            val first = repository.add(storyId, LibraryStatus.WANT_TO_READ, 10L)
            val changed = repository.changeStatus(storyId, LibraryStatus.READING, 20L)
            val repeated = repository.add(storyId, LibraryStatus.COMPLETED, 30L)

            assertEquals(LibraryStatus.WANT_TO_READ, first.status)
            assertEquals(LibraryStatus.READING, changed?.status)
            assertEquals(changed, repeated)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DATABASE = "library-migration-test"
        const val STORY_ID = "story:library-migration"
    }
}
