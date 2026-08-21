package app.openstory.storage.room.catalog

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.RoomMigrations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanonicalEngineMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OpenStoryDatabase::class.java,
    )

    @Test
    fun migrationEightToNineCreatesCanonicalBootstrapStateWithoutChangingIdentity() {
        helper.createDatabase(TEST_DATABASE, 8).apply {
            execSQL("INSERT INTO stories (story_id, content_type) VALUES ('story-1', 'MANGA')")
            execSQL(
                "INSERT INTO catalog_entries " +
                    "(plugin_id, source_id, story_id, title, aliases, authors, description, genres, content_type, " +
                    "language_tags, cover_url, source_url, score_value, score_scale, popularity_rank, " +
                    "publication_status, latest_update_at_epoch_millis, latest_update_release_label, " +
                    "plugin_version, fetched_at_epoch_millis, full_plugin_version, full_resolved_at_epoch_millis) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(
                    "catalog.example",
                    "source-1",
                    "story-1",
                    "Title",
                    "[]",
                    "[]",
                    null,
                    "[]",
                    "MANGA",
                    "[]",
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "1.0.0",
                    100L,
                    null,
                    null,
                ),
            )
            close()
        }

        helper.runMigrationsAndValidate(TEST_DATABASE, 9, true, RoomMigrations.MIGRATION_8_9).use { database ->
            database.query("SELECT COUNT(*) FROM story_canonical_state").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            database.query(
                "SELECT health, preference_mode, active_generation_id, created_at_epoch_millis " +
                    "FROM story_canonical_state WHERE story_id = 'story-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("REEVALUATING", cursor.getString(0))
                assertEquals("AUTO", cursor.getString(1))
                assertTrue(cursor.isNull(2))
                assertTrue(cursor.isNull(3))
            }
            database.query(
                "SELECT work_type, reason, attempt_count FROM canonical_engine_work WHERE story_id = 'story-1'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("FUSION_REBUILD", cursor.getString(0))
                assertEquals("schema-9-bootstrap", cursor.getString(1))
                assertEquals(0, cursor.getInt(2))
            }
            database.query("SELECT story_id FROM stories WHERE story_id = 'story-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("story-1", cursor.getString(0))
            }
        }
    }

    @Test
    fun migrationEightToNinePreservesRepresentativeStoryGraphAndForeignKeys() {
        helper.createDatabase(TEST_DATABASE_GRAPH, 8).apply {
            execSQL("INSERT INTO stories (story_id, content_type) VALUES ('story:a', 'MANGA')")
            execSQL("INSERT INTO stories (story_id, content_type) VALUES ('story:b', 'MANGA')")
            execSQL(
                "INSERT INTO catalog_entries (plugin_id, source_id, story_id, title, aliases, authors, description, " +
                    "genres, content_type, language_tags, cover_url, source_url, score_value, score_scale, " +
                    "popularity_rank, publication_status, latest_update_at_epoch_millis, latest_update_release_label, " +
                    "plugin_version, fetched_at_epoch_millis, full_plugin_version, full_resolved_at_epoch_millis) " +
                    "VALUES ('catalog.one', 'source:a', 'story:a', 'Story A', '[]', '[]', NULL, '[]', 'MANGA', '[]', " +
                    "NULL, NULL, NULL, NULL, NULL, 'ONGOING', 100, 'Ch. 1', '1.0.0', 100, NULL, NULL)",
            )
            execSQL(
                "INSERT INTO catalog_entries (plugin_id, source_id, story_id, title, aliases, authors, description, " +
                    "genres, content_type, language_tags, cover_url, source_url, score_value, score_scale, " +
                    "popularity_rank, publication_status, latest_update_at_epoch_millis, latest_update_release_label, " +
                    "plugin_version, fetched_at_epoch_millis, full_plugin_version, full_resolved_at_epoch_millis) " +
                    "VALUES ('catalog.two', 'source:b', 'story:b', 'Story B', '[]', '[]', NULL, '[]', 'MANGA', '[]', " +
                    "NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '1.0.0', 110, '1.0.0', 120)",
            )
            execSQL(
                "INSERT INTO catalog_home_snapshots (plugin_id, plugin_version, refreshed_at_epoch_millis) " +
                    "VALUES ('catalog.one', '1.0.0', 100)",
            )
            execSQL(
                "INSERT INTO catalog_home_sections (plugin_id, section_id, title, position, feed_kind) " +
                    "VALUES ('catalog.one', 'popular', 'Popular', 0, 'POPULAR')",
            )
            execSQL(
                "INSERT INTO catalog_home_items (plugin_id, section_id, position, source_id) " +
                    "VALUES ('catalog.one', 'popular', 0, 'source:a')",
            )
            execSQL(
                "INSERT INTO library_entries (story_id, status, added_at_epoch_millis, updated_at_epoch_millis) " +
                    "VALUES ('story:a', 'READING', 10, 20)",
            )
            execSQL(
                "INSERT INTO content_mappings (story_id, plugin_id, source_story_id, origin, policy_version, updated_at_epoch_millis) " +
                    "VALUES ('story:a', 'content.protected', 'protected-source', 'USER_APPROVED', 1, 30)",
            )
            execSQL(
                "INSERT INTO content_mappings (story_id, plugin_id, source_story_id, origin, policy_version, updated_at_epoch_millis) " +
                    "VALUES ('story:b', 'content.auto', 'auto-source', 'AUTOMATED', 1, 31)",
            )
            execSQL(
                "INSERT INTO content_mapping_rejections (story_id, plugin_id, source_story_id, policy_version, rejected_at_epoch_millis) " +
                    "VALUES ('story:a', 'content.reject', 'rejected-source', 1, 32)",
            )
            execSQL(
                "INSERT INTO canonical_chapters (canonical_chapter_id, story_id, kind, volume, chapter, part, normalized_title, display_label, tombstoned) " +
                    "VALUES ('chapter:a:1', 'story:a', 'NUMBERED', '1', '1', NULL, NULL, 'Chapter 1', 0)",
            )
            execSQL(
                "INSERT INTO chapter_releases (chapter_release_id, story_id, plugin_id, source_story_id, source_release_id, " +
                    "display_label, kind, volume, chapter, part, normalized_title, language_tag, published_at_epoch_millis, canonical_chapter_id) " +
                    "VALUES ('release:a:1', 'story:a', 'content.protected', 'protected-source', 'release-source-1', " +
                    "'Chapter 1', 'NUMBERED', '1', '1', NULL, NULL, 'en', 40, 'chapter:a:1')",
            )
            execSQL(
                "INSERT INTO chapter_aggregation_overrides (story_id, chapter_release_id, canonical_chapter_id, kind) " +
                    "VALUES ('story:a', 'release:a:1', 'chapter:a:1', 'FORCE_LINK')",
            )
            execSQL(
                "INSERT INTO chapter_sync_states (story_id, plugin_id, source_story_id, phase, cursor, checkpoint, fingerprint, updated_at_epoch_millis) " +
                    "VALUES ('story:a', 'content.protected', 'protected-source', 'INCREMENTAL', 'cursor-1', 'checkpoint-1', 'fingerprint-1', 50)",
            )
            execSQL(
                "INSERT INTO reading_progress (story_id, canonical_chapter_id, chapter_release_id, content_fingerprint, block_id, " +
                    "character_offset, fraction, completed_at_epoch_millis, updated_at_epoch_millis) " +
                    "VALUES ('story:a', 'chapter:a:1', 'release:a:1', 'content-fingerprint-1', 'block-1', 12, 0.5, NULL, 60)",
            )
            execSQL(
                "INSERT INTO chapter_storage_entries (namespace, chapter_release_id, content_fingerprint, checksum, size_bytes, " +
                    "last_accessed_at_epoch_millis, pinned, current, download_state, failure_reason, attempt, updated_at_epoch_millis) " +
                    "VALUES ('download', 'release:a:1', 'content-fingerprint-1', 'checksum-1', 128, 70, 1, 1, 'READY', NULL, 0, 70)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            TEST_DATABASE_GRAPH,
            9,
            true,
            RoomMigrations.MIGRATION_8_9,
        ).use { database ->
            assertEquals("story:a", scalarText(database, "SELECT story_id FROM catalog_entries WHERE plugin_id='catalog.one'"))
            assertEquals("source:a", scalarText(database, "SELECT source_id FROM catalog_home_items WHERE plugin_id='catalog.one'"))
            assertEquals("READING", scalarText(database, "SELECT status FROM library_entries WHERE story_id='story:a'"))
            assertEquals("USER_APPROVED", scalarText(database, "SELECT origin FROM content_mappings WHERE story_id='story:a'"))
            assertEquals("AUTOMATED", scalarText(database, "SELECT origin FROM content_mappings WHERE story_id='story:b'"))
            assertEquals("rejected-source", scalarText(database, "SELECT source_story_id FROM content_mapping_rejections WHERE story_id='story:a'"))
            assertEquals("chapter:a:1", scalarText(database, "SELECT canonical_chapter_id FROM canonical_chapters WHERE story_id='story:a'"))
            assertEquals("release:a:1", scalarText(database, "SELECT chapter_release_id FROM chapter_releases WHERE story_id='story:a'"))
            assertEquals("FORCE_LINK", scalarText(database, "SELECT kind FROM chapter_aggregation_overrides WHERE story_id='story:a'"))
            assertEquals("cursor-1", scalarText(database, "SELECT cursor FROM chapter_sync_states WHERE story_id='story:a'"))
            assertEquals("block-1", scalarText(database, "SELECT block_id FROM reading_progress WHERE story_id='story:a'"))
            assertEquals("release:a:1", scalarText(database, "SELECT chapter_release_id FROM chapter_storage_entries WHERE namespace='download'"))

            listOf("story:a", "story:b").forEach { storyId ->
                database.query(
                    "SELECT active_generation_id, health, preference_mode, created_at_epoch_millis " +
                        "FROM story_canonical_state WHERE story_id = '$storyId'",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertTrue(cursor.isNull(0))
                    assertEquals("REEVALUATING", cursor.getString(1))
                    assertEquals("AUTO", cursor.getString(2))
                    assertTrue(cursor.isNull(3))
                }
                database.query(
                    "SELECT COUNT(*) FROM canonical_engine_work WHERE story_id = '$storyId' AND work_type='FUSION_REBUILD'",
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(1, cursor.getInt(0))
                }
            }

            listOf(
                "catalog_entry_identifiers",
                "reconciliation_cases",
                "reconciliation_case_revisions",
                "story_merge_events",
                "story_merge_reversal_events",
                "story_redirects",
            ).forEach { table ->
                database.query("SELECT COUNT(*) FROM $table").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(0), "Migration must not fabricate rows in $table")
                }
            }
            database.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }
        }
    }

    private fun scalarText(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        query: String,
    ): String = database.query(query).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getString(0)
    }

    private companion object {
        const val TEST_DATABASE = "canonical-engine-migration-8-9-test"
        const val TEST_DATABASE_GRAPH = "canonical-engine-migration-8-9-graph-test"
    }
}
