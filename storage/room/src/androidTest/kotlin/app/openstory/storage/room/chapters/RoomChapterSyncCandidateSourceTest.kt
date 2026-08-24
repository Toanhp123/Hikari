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
        insertSyncState(sql, "story:new", "plugin:a", 30)
        insertSyncState(sql, "story:removed", "plugin:a", 1)

        val candidates = RoomChapterSyncCandidateSource(database).eligibleCandidates()

        assertEquals(listOf("story:never", "story:old", "story:new"), candidates.map { it.storyId.value })
        assertNull(candidates[0].lastSuccessfulSyncAtEpochMillis)
        assertEquals(10L, candidates[1].lastSuccessfulSyncAtEpochMillis)
        assertEquals(30L, candidates[2].lastSuccessfulSyncAtEpochMillis)
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
