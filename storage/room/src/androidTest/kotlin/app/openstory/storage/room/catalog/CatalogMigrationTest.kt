package app.openstory.storage.room.catalog

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.RoomMigrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenStoryDatabase::class.java,
    )

    @Test
    fun migrationSixToSevenPreservesCatalogRowsAndAddsSemanticDefaults() {
        helper.createDatabase(TEST_DATABASE, 6).apply {
            execSQL("INSERT INTO stories (story_id, content_type) VALUES ('story:1', 'MANGA')")
            execSQL(
                "INSERT INTO catalog_entries " +
                    "(plugin_id, source_id, story_id, title, aliases, authors, description, genres, content_type, " +
                    "language_tags, cover_url, source_url, score_value, score_scale, popularity_rank, " +
                    "plugin_version, fetched_at_epoch_millis) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "catalog.example", "source:1", "story:1", "Existing title", "[]", "[]", null, "[]", "MANGA",
                    "[]", null, null, 9.0, 10.0, 1L, "1.0.0", 100L,
                ),
            )
            execSQL(
                "INSERT INTO catalog_home_snapshots (plugin_id, plugin_version, refreshed_at_epoch_millis) " +
                    "VALUES ('catalog.example', '1.0.0', 100)",
            )
            execSQL(
                "INSERT INTO catalog_home_sections (plugin_id, section_id, title, position) " +
                    "VALUES ('catalog.example', 'top', 'Top', 0)",
            )
            execSQL(
                "INSERT INTO catalog_home_items (plugin_id, section_id, position, source_id) " +
                    "VALUES ('catalog.example', 'top', 0, 'source:1')",
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 7, true, RoomMigrations.MIGRATION_6_7).use { database ->
            val preservedTitle = database.query(
                "SELECT title FROM catalog_entries WHERE plugin_id = 'catalog.example' AND source_id = 'source:1'",
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
            val sectionFeedKind = database.query(
                "SELECT feed_kind FROM catalog_home_sections WHERE plugin_id = 'catalog.example' AND section_id = 'top'",
            ).use { cursor ->
                cursor.moveToFirst()
                cursor.getString(0)
            }
            val metadata = database.query(
                "SELECT publication_status, latest_update_at_epoch_millis, latest_update_release_label " +
                    "FROM catalog_entries WHERE plugin_id = 'catalog.example' AND source_id = 'source:1'",
            ).use { cursor ->
                cursor.moveToFirst()
                Triple(
                    if (cursor.isNull(0)) null else cursor.getString(0),
                    if (cursor.isNull(1)) null else cursor.getLong(1),
                    if (cursor.isNull(2)) null else cursor.getString(2),
                )
            }

            assertEquals("Existing title", preservedTitle)
            assertEquals("OTHER", sectionFeedKind)
            assertNull(metadata.first)
            assertNull(metadata.second)
            assertNull(metadata.third)
        }
    }

    private companion object {
        const val TEST_DATABASE = "catalog-migration-test"
    }
}
