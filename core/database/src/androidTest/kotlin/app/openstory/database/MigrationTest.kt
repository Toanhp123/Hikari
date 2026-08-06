package app.openstory.database

import android.content.Context

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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
class MigrationTest {

    @get:Rule
    val migrationHelper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            OpenStoryDatabase::class.java,
        )

    @Test
    fun migrationHarnessCreatesVersionOneSchema() {
        val database =
            migrationHelper.createDatabase(
                HARNESS_DATABASE_NAME,
                VERSION_ONE,
            )

        try {
            val tables =
                mutableSetOf<String>()

            database.query(
                """
                SELECT name
                FROM sqlite_master
                WHERE type = 'table'
                """.trimIndent(),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    tables +=
                        cursor.getString(0)
                }
            }

            assertTrue(
                "canonical_stories" in tables,
            )
            assertTrue(
                "canonical_chapters" in tables,
            )
            assertTrue(
                "chapter_releases" in tables,
            )
            assertNoForeignKeyViolations(
                database,
            )
        } finally {
            database.close()
        }
    }

    @Test
    fun committedVersionOneFixtureOpensWithIntegrity() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        copyFixtureToDatabase(
            context =
                context,
            databaseName =
                FIXTURE_DATABASE_NAME,
        )

        val database =
            OpenStoryDatabase.open(
                context =
                    context,
                databaseName =
                    FIXTURE_DATABASE_NAME,
            )

        try {
            assertFixtureContents(
                database
                    .openHelper
                    .writableDatabase,
            )
        } finally {
            database.close()

            context.deleteDatabase(
                FIXTURE_DATABASE_NAME,
            )
        }
    }

    @Test
    fun versionOneDatabaseMigratesToVersionTwoPreservingPluginState() {
        val context =
            ApplicationProvider
                .getApplicationContext<Context>()

        context.deleteDatabase(
            MIGRATION_DATABASE_NAME,
        )

        createVersionOneDatabaseWithPluginState()

        val migratedDatabase =
            OpenStoryDatabase.open(
                context,
            )

        try {
            assertVersionTwoMigration(
                migratedDatabase
                    .openHelper
                    .writableDatabase,
            )
        } finally {
            migratedDatabase.close()

            context.deleteDatabase(
                MIGRATION_DATABASE_NAME,
            )
        }
    }

    private fun createVersionOneDatabaseWithPluginState() {
        val database =
            migrationHelper.createDatabase(
                MIGRATION_DATABASE_NAME,
                VERSION_ONE,
            )

        try {
            database.execSQL(
                """
                INSERT INTO plugin_states (
                    plugin_id,
                    enabled,
                    active_version,
                    previous_version,
                    updated_at_epoch_millis
                )
                VALUES (
                    '$FIXTURE_PLUGIN_ID',
                    0,
                    '1.0.0',
                    NULL,
                    1000
                )
                """.trimIndent(),
            )
        } finally {
            database.close()
        }
    }

    private fun assertVersionTwoMigration(
        database: SupportSQLiteDatabase,
    ) {
        assertTrue(
            tableExists(
                database =
                    database,
                table =
                    "plugin_versions",
            ),
        )

        assertEquals(
            expected =
                0,
            actual =
                rowCount(
                    database =
                        database,
                    table =
                        "plugin_versions",
                ),
        )

        assertPluginStatePreserved(
            database =
                database,
        )

        assertNoForeignKeyViolations(
            database,
        )
    }

    private fun assertPluginStatePreserved(
        database: SupportSQLiteDatabase,
    ) {
        database.query(
            """
            SELECT
                enabled,
                active_version,
                previous_version,
                updated_at_epoch_millis
            FROM plugin_states
            WHERE plugin_id =
                '$FIXTURE_PLUGIN_ID'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(
                cursor.moveToFirst(),
                "Expected migrated plugin state.",
            )

            assertEquals(
                expected =
                    0,
                actual =
                    cursor.getInt(0),
            )

            assertEquals(
                expected =
                    "1.0.0",
                actual =
                    cursor.getString(1),
            )

            assertTrue(
                cursor.isNull(2),
            )

            assertEquals(
                expected =
                    1_000L,
                actual =
                    cursor.getLong(3),
            )

            assertFalse(
                cursor.moveToNext(),
                "Expected one migrated plugin state.",
            )
        }
    }

    private fun assertFixtureContents(
        database: SupportSQLiteDatabase,
    ) {
        val expectedTables =
            listOf(
                "canonical_stories",
                "catalog_entries",
                "content_mappings",
                "canonical_chapters",
                "chapter_releases",
                "reading_progress",
            )

        expectedTables.forEach { table ->
            assertEquals(
                expected =
                    1,
                actual =
                    rowCount(
                        database =
                            database,
                        table =
                            table,
                    ),
                message =
                    "Expected one fixture row in $table",
            )
        }

        assertNoForeignKeyViolations(
            database,
        )
    }

    private fun copyFixtureToDatabase(
        context: Context,
        databaseName: String,
    ) {
        context.deleteDatabase(
            databaseName,
        )

        val destination =
            context.getDatabasePath(
                databaseName,
            )

        checkNotNull(
            destination.parentFile,
        ).mkdirs()

        InstrumentationRegistry
            .getInstrumentation()
            .context
            .assets
            .open(
                FIXTURE_ASSET_PATH,
            )
            .use { input ->
                destination
                    .outputStream()
                    .use { output ->
                        input.copyTo(
                            output,
                        )
                    }
            }

        assertTrue(
            destination.isFile,
        )

        assertTrue(
            destination.length() > 0L,
        )
    }

    private fun tableExists(
        database: SupportSQLiteDatabase,
        table: String,
    ): Boolean =
        database.query(
            """
            SELECT COUNT(*)
            FROM sqlite_master
            WHERE
                type = 'table'
                AND name = '$table'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(
                cursor.moveToFirst(),
            )

            cursor.getInt(0) ==
                1
        }

    private fun rowCount(
        database: SupportSQLiteDatabase,
        table: String,
    ): Int =
        database.query(
            "SELECT COUNT(*) FROM $table",
        ).use { cursor ->
            assertTrue(
                cursor.moveToFirst(),
            )

            cursor.getInt(0)
        }

    private fun assertNoForeignKeyViolations(
        database: SupportSQLiteDatabase,
    ) {
        database.query(
            "PRAGMA foreign_key_check",
        ).use { cursor ->
            assertFalse(
                cursor.moveToFirst(),
                "Expected no foreign-key violations",
            )
        }
    }

    private companion object {
        const val VERSION_ONE =
            1

        const val FIXTURE_PLUGIN_ID =
            "community.fixture"

        const val HARNESS_DATABASE_NAME =
            "migration-harness.db"

        const val MIGRATION_DATABASE_NAME =
            "openstory.db"

        const val FIXTURE_DATABASE_NAME =
            "migration-fixture.db"

        const val FIXTURE_ASSET_PATH =
            "database/v1/openstory.db"
    }
}
