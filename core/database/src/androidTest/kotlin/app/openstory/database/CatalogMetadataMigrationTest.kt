package app.openstory.database

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogMetadataMigrationTest {

    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            OpenStoryDatabase::class.java,
        )

    @Test
    fun versionTwoDatabaseMigratesToVersionThreeWithCatalogDefaults() {
        val context =
            ApplicationProvider.getApplicationContext<Context>()

        context.deleteDatabase(DATABASE_NAME)
        seedVersionTwoDatabase()

        val migrated =
            OpenStoryDatabase.open(
                context = context,
                databaseName = DATABASE_NAME,
            )

        try {
            assertCatalogDefaults(migrated)
            assertNoForeignKeyViolations(migrated)
        } finally {
            migrated.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private fun seedVersionTwoDatabase() {
        val database =
            migrationHelper.createDatabase(
                DATABASE_NAME,
                VERSION_TWO,
            )

        database.execSQL(
            """
            INSERT INTO catalog_entries(
                catalog_entry_id,
                catalog_plugin_id,
                title,
                description,
                score,
                score_scale
            )
            VALUES(?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(
                CATALOG_ENTRY_ID,
                "catalog.example",
                "Story",
                "Description",
                8.4,
                10.0,
            ),
        )
        database.close()
    }

    private fun assertCatalogDefaults(
        database: OpenStoryDatabase,
    ) {
        database.openHelper.writableDatabase.query(
            """
            SELECT
                external_story_id,
                source_url,
                authors_json,
                genres_json,
                cover_reference,
                publication_status
            FROM catalog_entries
            WHERE catalog_entry_id = ?
            """.trimIndent(),
            arrayOf(CATALOG_ENTRY_ID),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(CATALOG_ENTRY_ID, cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertEquals("[]", cursor.getString(2))
            assertEquals("[]", cursor.getString(3))
            assertTrue(cursor.isNull(4))
            assertTrue(cursor.isNull(5))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun assertNoForeignKeyViolations(
        database: OpenStoryDatabase,
    ) {
        database.openHelper.writableDatabase
            .query("PRAGMA foreign_key_check")
            .use { cursor ->
                assertFalse(
                    cursor.moveToFirst(),
                    "Expected no foreign-key violations",
                )
            }
    }

    private companion object {
        const val VERSION_TWO = 2
        const val DATABASE_NAME = "migration-v2-v3.db"
        const val CATALOG_ENTRY_ID = "catalog.example:story-1"
    }
}
