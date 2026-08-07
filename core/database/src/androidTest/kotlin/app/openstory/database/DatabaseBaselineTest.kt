package app.openstory.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.database.repository.RoomStoryRepository
import app.openstory.model.CanonicalStory
import app.openstory.model.ContentType
import app.openstory.model.LibraryStatus
import app.openstory.model.StoryId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseBaselineTest {

    private val context =
        ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun freshDatabaseContainsCurrentBaselineTables() =
        withFreshDatabase { database ->
            val tables =
                buildSet {
                    database.openHelper.writableDatabase.query(
                        "SELECT name FROM sqlite_master WHERE type = 'table'",
                    ).use { cursor ->
                        while (cursor.moveToNext()) {
                            add(cursor.getString(0))
                        }
                    }
                }

            assertEquals(
                expected =
                    setOf(
                        "canonical_stories",
                        "catalog_entries",
                        "story_catalog_entries",
                        "library_entries",
                        "content_mappings",
                        "story_content_mappings",
                        "canonical_chapters",
                        "chapter_releases",
                        "canonical_chapter_releases",
                        "reading_progress",
                        "plugin_states",
                        "plugin_versions",
                    ),
                actual = tables.filterNotTo(mutableSetOf()) { table ->
                    table == "android_metadata" ||
                        table == "room_master_table" ||
                        table.startsWith("sqlite_")
                },
            )
        }

    @Test
    fun freshDatabaseHasNoForeignKeyViolations() =
        withFreshDatabase { database ->
            database.openHelper.writableDatabase.query(
                "PRAGMA foreign_key_check",
            ).use { cursor ->
                assertFalse(
                    cursor.moveToFirst(),
                    "Expected no foreign-key violations",
                )
            }
        }

    @Test
    fun freshDatabaseSupportsCurrentRepositoryRoundTrip() = runTest {
        withFreshDatabase { database ->
            val repository = RoomStoryRepository(database)
            val story =
                CanonicalStory(
                    id = StoryId("baseline-story"),
                    contentType = ContentType.WEB_NOVEL,
                    preferredTitle = "Baseline Story",
                    aliases = emptySet(),
                    catalogEntries = emptyList(),
                )

            repository.addToLibrary(
                story = story,
                status = LibraryStatus.WANT_TO_READ,
            )

            assertEquals(
                expected = story,
                actual = repository.observeStory(story.id).first(),
            )
        }
    }

    private inline fun withFreshDatabase(
        block: (OpenStoryDatabase) -> Unit,
    ) {
        context.deleteDatabase(DATABASE_NAME)
        val database =
            OpenStoryDatabase.open(
                context = context,
                databaseName = DATABASE_NAME,
            )

        try {
            block(database)
        } finally {
            database.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private companion object {
        const val DATABASE_NAME =
            "database-baseline.db"
    }
}
