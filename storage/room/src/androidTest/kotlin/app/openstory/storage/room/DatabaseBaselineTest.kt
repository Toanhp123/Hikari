package app.openstory.storage.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseBaselineTest {
    @Test
    fun freshDatabaseContainsCurrentTables() = withDatabase { database ->
        val names = database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' " +
                "AND name NOT LIKE 'android_%' AND name NOT LIKE 'sqlite_%' " +
                "AND name != 'room_master_table'",
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertEquals(
            setOf(
                "stories",
                "catalog_entries",
                "catalog_home_snapshots",
                "catalog_home_sections",
                "catalog_home_items",
                "catalog_entry_identifiers",
                "story_canonical_state",
                "canonical_generations",
                "canonical_field_provenance",
                "reconciliation_cases",
                "reconciliation_case_revisions",
                "story_merge_events",
                "story_merge_reversal_events",
                "story_redirects",
                "canonical_engine_work",
                "catalog_change_outbox",
                "plugin_state",
                "plugin_versions",
                "plugin_diagnostics",
                "library_entries",
                "content_mappings",
                "content_mapping_rejections",
                "canonical_chapters",
                "chapter_releases",
                "chapter_aggregation_overrides",
                "chapter_sync_states",
                "reading_progress",
                "chapter_storage_entries",
            ),
            names,
        )
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use {
            assertEquals(0, it.count)
        }
        listOf("downloads").forEach { assertFalse(it in names, "Legacy table present: $it") }
    }

    @Test
    fun catalogIdentityAndHomeOrderingAreDatabaseEnforced() = withDatabase { database ->
        val entryIndices = database.openHelper.readableDatabase
            .query("PRAGMA index_list('catalog_entries')")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(1)) } }
        val itemForeignKeys = database.openHelper.readableDatabase
            .query("PRAGMA foreign_key_list('catalog_home_items')")
            .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(2)) } }

        assertFalse(entryIndices.isEmpty())
        assertEquals(
            setOf("catalog_home_sections", "catalog_entries"),
            itemForeignKeys.toSet(),
        )
    }

    private inline fun withDatabase(block: (OpenStoryDatabase) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(
            context,
            OpenStoryDatabase::class.java,
        ).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }
}
