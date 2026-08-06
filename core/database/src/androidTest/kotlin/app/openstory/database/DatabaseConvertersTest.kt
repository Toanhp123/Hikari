package app.openstory.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.model.ReaderPosition
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseConvertersTest {

    @Test
    fun readerPositionRoundTrips() {
        val source =
            ReaderPosition.Paragraph(
                index = 7,
                fraction = 0.25f,
            )

        val stored =
            DatabaseConverters.fromReaderPosition(source)

        assertEquals(
            source,
            DatabaseConverters.toReaderPosition(stored),
        )
    }

    @Test
    fun decimalChapterNumberRoundTripsLosslessly() {
        val source =
            BigDecimal("10.500")

        val stored =
            DatabaseConverters.fromBigDecimal(source)

        assertEquals(
            source,
            DatabaseConverters.toBigDecimal(stored),
        )
    }

    @Test
    fun schemaDefinesReleaseIdentityAndDeletionBoundaries() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val database =
            Room.inMemoryDatabaseBuilder(
                context,
                OpenStoryDatabase::class.java,
            )
                .allowMainThreadQueries()
                .build()

        try {
            assertReleaseIdentityIndex(database)

            assertCascadeFromCanonicalStory(
                database = database,
                table = "story_catalog_entries",
            )
            assertCascadeFromCanonicalStory(
                database = database,
                table = "story_content_mappings",
            )

            assertNoPluginOwnership(database)
        }
        finally {
            database.close()
        }
    }

    private fun assertReleaseIdentityIndex(
        database: OpenStoryDatabase,
    ) {
        val cursor =
            database.openHelper.writableDatabase.query(
                "PRAGMA index_list('chapter_releases')",
            )

        var hasReleaseIdentityIndex = false

        cursor.use {
            val nameIndex =
                it.getColumnIndexOrThrow("name")
            val uniqueIndex =
                it.getColumnIndexOrThrow("unique")

            while (it.moveToNext()) {
                if (
                    it.getString(nameIndex) ==
                        "index_chapter_releases_plugin_source_release" &&
                    it.getInt(uniqueIndex) == 1
                ) {
                    hasReleaseIdentityIndex = true
                }
            }
        }

        assertTrue(
            hasReleaseIdentityIndex,
            "Release identity must be unique per plugin and source ID",
        )
    }

    private fun assertNoPluginOwnership(
        database: OpenStoryDatabase,
    ) {
        val userOwnedTables =
            listOf(
                "canonical_stories",
                "library_entries",
                "reading_progress",
                "content_mappings",
                "chapter_releases",
            )

        userOwnedTables.forEach { table ->
            assertTrue(
                foreignKeyParents(
                    database = database,
                    table = table,
                ).none { parent ->
                    parent == "plugin_states"
                },
                "$table must not cascade from plugin deletion",
            )
        }
    }

    private fun assertCascadeFromCanonicalStory(
        database: OpenStoryDatabase,
        table: String,
    ) {
        val cursor =
            database.openHelper.writableDatabase.query(
                "PRAGMA foreign_key_list('$table')",
            )

        var hasCascade = false

        cursor.use {
            val parentIndex =
                it.getColumnIndexOrThrow("table")
            val deleteIndex =
                it.getColumnIndexOrThrow("on_delete")

            while (it.moveToNext()) {
                if (
                    it.getString(parentIndex) ==
                        "canonical_stories" &&
                    it.getString(deleteIndex) ==
                        "CASCADE"
                ) {
                    hasCascade = true
                }
            }
        }

        assertTrue(
            hasCascade,
            "$table must cascade when its canonical story is deleted",
        )
    }

    private fun foreignKeyParents(
        database: OpenStoryDatabase,
        table: String,
    ): Set<String> {
        val parents =
            mutableSetOf<String>()

        val cursor =
            database.openHelper.writableDatabase.query(
                "PRAGMA foreign_key_list('$table')",
            )

        cursor.use {
            val parentIndex =
                it.getColumnIndexOrThrow("table")

            while (it.moveToNext()) {
                parents +=
                    it.getString(parentIndex)
            }
        }

        return parents
    }
}
