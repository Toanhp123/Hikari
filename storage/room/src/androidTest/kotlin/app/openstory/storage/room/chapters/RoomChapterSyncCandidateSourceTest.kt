package app.openstory.storage.room.chapters

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RoomChapterSyncCandidateSourceTest {
    private lateinit var database: OpenStoryDatabase

    @AfterTest
    fun closeDatabase() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun returnsOneCurrentLibraryRowWithOldestSuccessfulTimestamp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).build()
        val sql = database.openHelper.writableDatabase
        listOf("story:never", "story:old", "story:new", "story:removed").forEach { storyId ->
            sql.execSQL("INSERT INTO stories(story_id, content_type) VALUES(?, ?)", arrayOf(storyId, "MANGA"))
        }
        listOf("story:never", "story:old", "story:new").forEachIndexed { index, storyId ->
            sql.execSQL(
                "INSERT INTO library_entries(story_id, status, added_at_epoch_millis, updated_at_epoch_millis) " +
                    "VALUES(?, ?, ?, ?)",
                arrayOf<Any?>(storyId, "READING", index.toLong(), index.toLong()),
            )
        }
        insertSyncState(sql, "story:old", "plugin:a", 20)
        insertSyncState(sql, "story:old", "plugin:b", 10)
        insertMapping(sql, "story:old", "plugin:a", "DISCOVERED")
        insertMapping(sql, "story:old", "plugin:b", "USER_APPROVED")
        insertSyncState(sql, "story:new", "plugin:a", 30)
        insertSyncState(sql, "story:removed", "plugin:a", 1)

        val candidates = RoomChapterSyncCandidateSource(database).eligibleCandidates()

        assertEquals(listOf("story:never", "story:old", "story:new"), candidates.map { it.storyId.value })
        assertNull(candidates[0].lastSuccessfulSyncAtEpochMillis)
        assertEquals(10L, candidates[1].lastSuccessfulSyncAtEpochMillis)
        assertEquals(30L, candidates[2].lastSuccessfulSyncAtEpochMillis)
    }

    private fun insertMapping(
        sql: androidx.sqlite.db.SupportSQLiteDatabase,
        storyId: String,
        pluginId: String,
        origin: String,
    ) {
        sql.execSQL(
            "INSERT INTO content_mappings(" +
                "story_id, plugin_id, source_story_id, origin, policy_version, updated_at_epoch_millis" +
                ") VALUES(?, ?, ?, ?, 1, 0)",
            arrayOf<Any?>(storyId, pluginId, "source-$pluginId", origin),
        )
    }

    @Test
    fun resolvesRedirectsAndExcludesDeletedLibraryRows() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).build()
        val sql = database.openHelper.writableDatabase
        listOf("story:retired", "story:canonical", "story:deleted").forEach { storyId ->
            sql.execSQL("INSERT INTO stories(story_id, content_type) VALUES(?, ?)", arrayOf(storyId, "MANGA"))
        }
        listOf("story:retired", "story:deleted").forEachIndexed { index, storyId ->
            sql.execSQL(
                "INSERT INTO library_entries(story_id, status, added_at_epoch_millis, updated_at_epoch_millis) " +
                    "VALUES(?, 'READING', ?, ?)",
                arrayOf<Any?>(storyId, index.toLong(), index.toLong()),
            )
        }
        insertMergeRedirect(sql, "story:retired", "story:canonical")
        insertSyncState(sql, "story:canonical", "plugin:a", 15)
        sql.execSQL("DELETE FROM library_entries WHERE story_id = ?", arrayOf("story:deleted"))

        val candidates = RoomChapterSyncCandidateSource(database).eligibleCandidates()

        assertEquals(listOf("story:canonical"), candidates.map { it.storyId.value })
        assertEquals(15L, candidates.single().lastSuccessfulSyncAtEpochMillis)
    }

    private fun insertMergeRedirect(
        sql: androidx.sqlite.db.SupportSQLiteDatabase,
        retiredStoryId: String,
        canonicalStoryId: String,
    ) {
        sql.execSQL(
            "INSERT INTO story_merge_events(" +
                "merge_event_id, survivor_story_id, retired_story_id, origin, reconciliation_case_id, " +
                "evidence_fingerprint, policy_version, merged_at_epoch_millis, reversibility_state, " +
                "reversal_payload_version, reversal_payload" +
                ") VALUES('merge:test', ?, ?, 'TEST', NULL, 'fingerprint', 1, 0, 'REVERSIBLE', 1, '{}')",
            arrayOf<Any?>(canonicalStoryId, retiredStoryId),
        )
        sql.execSQL(
            "INSERT INTO story_redirects(" +
                "retired_story_id, canonical_story_id, merge_event_id, created_at_epoch_millis" +
                ") VALUES(?, ?, 'merge:test', 0)",
            arrayOf<Any?>(retiredStoryId, canonicalStoryId),
        )
    }

    private fun insertSyncState(
        sql: androidx.sqlite.db.SupportSQLiteDatabase,
        storyId: String,
        pluginId: String,
        timestamp: Long,
    ) {
        sql.execSQL(
            "INSERT INTO chapter_sync_states(" +
                "story_id, plugin_id, source_story_id, phase, cursor, checkpoint, fingerprint, updated_at_epoch_millis" +
                ") VALUES(?, ?, ?, ?, NULL, NULL, NULL, ?)",
            arrayOf<Any?>(storyId, pluginId, "source-$pluginId", "FULL", timestamp),
        )
    }
}
