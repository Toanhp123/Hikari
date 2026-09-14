package app.openstory.catalog.storage

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CatalogMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CatalogDatabase::class.java,
    )

    @Test
    fun migrationOneToTwoPreservesDurableRowsAndAddsEmptyMetadataTables() {
        helper.createDatabase(DATABASE_NAME, 1).use { database ->
            insertVersionOneFixture(database)
        }

        helper.runMigrationsAndValidate(
            DATABASE_NAME,
            2,
            true,
            CatalogDatabase.MIGRATION_1_2,
        ).use { database ->
            listOf(
                "catalog_source_state",
                "story_source_identity",
                "story_source_summary",
                "discover_card",
                "story_detail",
                "story_author",
                "story_artist",
                "story_genre",
                "story_orphan_retention",
            ).forEach { table -> assertEquals("$table row count", 1, database.rowCount(table)) }
            assertEquals(0, database.rowCount("story_alias"))
            assertEquals(0, database.rowCount("story_language"))
            database.query("SELECT title FROM story_source_summary WHERE story_id = 'story-id'").use { cursor ->
                check(cursor.moveToFirst())
                assertEquals("Preserved title", cursor.getString(0))
            }
        }
    }

    private fun insertVersionOneFixture(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO catalog_source_state VALUES ('source', 'MANGA', 'v1', 1, 10, 10)",
        )
        database.execSQL(
            "INSERT INTO story_source_identity VALUES ('story-id', 'source', 'story-one')",
        )
        database.execSQL(
            """
            INSERT INTO story_source_summary VALUES (
                'story-id', 'source', 'v1', 'Preserved title', 'MANGA',
                NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 10
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO discover_card VALUES (
                'source', 'MANGA', 'v1', 1, 'POPULAR', 0, 'story-id', 'story-one',
                'Preserved title', 'MANGA', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO story_detail VALUES (
                'story-id', 'source', 'story-one', 'v1', 'Description', 'ONGOING', 'English', 10, 10
            )
            """.trimIndent(),
        )
        database.execSQL("INSERT INTO story_author VALUES ('story-id', 0, 'Author')")
        database.execSQL("INSERT INTO story_artist VALUES ('story-id', 0, 'Artist')")
        database.execSQL("INSERT INTO story_genre VALUES ('story-id', 0, 'Genre')")
        database.execSQL("INSERT INTO story_orphan_retention VALUES ('story-id', 10)")
    }

    private fun SupportSQLiteDatabase.rowCount(table: String): Int =
        query("SELECT COUNT(*) FROM $table").use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val DATABASE_NAME = "catalog-migration"
    }
}
