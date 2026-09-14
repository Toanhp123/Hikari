package app.openstory.library.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.library.domain.LibraryEntry
import app.openstory.library.domain.LibraryFilter
import app.openstory.library.domain.LibraryMutationResult
import app.openstory.library.domain.LibraryPresentationSnapshot
import app.openstory.library.domain.LibraryQuery
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoomLibraryStoreInstrumentedTest {
    private lateinit var database: LibraryDatabase
    private lateinit var store: RoomLibraryStore
    private val observedSql = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ -> observedSql += sql.lowercase() }, Executor(Runnable::run))
            .build()
        store = RoomLibraryStore(database)
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun mutationsAreIdempotentWithoutTimestampOrUpdateChurn() = runBlocking {
        val first = entry("saved", 10, title = "First")

        assertEquals(LibraryMutationResult.CHANGED, store.add(first))
        observedSql.clear()
        assertEquals(LibraryMutationResult.NO_OP, store.add(first.copy(savedAtEpochMs = 20)))
        assertEquals(10, store.observeMembership(first.ref).first()?.savedAtEpochMs)
        assertTrue(observedSql.none { it.startsWith("update library_entry") })

        observedSql.clear()
        assertEquals(LibraryMutationResult.NO_OP, store.enrichSnapshot(first.ref, first.snapshot))
        assertTrue(observedSql.none { it.startsWith("update library_entry") })

        assertEquals(LibraryMutationResult.CHANGED, store.remove(first.ref))
        assertNull(store.observeMembership(first.ref).first())
        assertEquals(LibraryMutationResult.CHANGED, store.add(first.copy(savedAtEpochMs = 30)))
        assertEquals(30, store.observeMembership(first.ref).first()?.savedAtEpochMs)
    }

    @Test
    fun membershipObservationIsPointKeyed() = runBlocking {
        repeat(100) { index -> store.add(entry("story-$index", index.toLong())) }
        val target = ref("story-50")
        observedSql.clear()

        assertEquals(target, store.observeMembership(target).first()?.ref)

        val reads = observedSql.filter { it.contains("from library_entry") }
        assertTrue(reads.isNotEmpty())
        assertTrue(reads.all { it.contains("where story_id") })
    }

    @Test
    fun blankAndFilteredWindowsUseBoundedKeysetQueriesAndIndexes() = runBlocking {
        repeat(100) { index ->
            val media = if (index % 2 == 0) CatalogMediaType.MANGA else CatalogMediaType.LIGHT_NOVEL
            store.add(entry("story-$index", index.toLong(), media))
        }
        observedSql.clear()

        val first = store.observeWindow(LibraryQuery("", LibraryFilter.ALL, null, 10)).first()
        assertEquals(10, first.items.size)
        assertEquals((99 downTo 90).map { "story-$it" }, first.items.map { it.ref.sourceStoryId })
        val second = store.observeWindow(
            LibraryQuery("", LibraryFilter.ALL, first.nextCursor, 10),
        ).first()
        assertEquals((89 downTo 80).map { "story-$it" }, second.items.map { it.ref.sourceStoryId })

        val manga = store.observeWindow(LibraryQuery("", LibraryFilter.MANGA, null, 7)).first()
        assertEquals(listOf("story-98", "story-96", "story-94", "story-92", "story-90", "story-88", "story-86"), manga.items.map { it.ref.sourceStoryId })
        assertTrue(observedSql.filter { it.contains("from library_entry") }.all { it.contains("limit") })
        assertUsesIndex(
            """
            SELECT * FROM library_entry
            WHERE saved_at_epoch_ms < ? OR (saved_at_epoch_ms = ? AND story_id > ?)
            ORDER BY saved_at_epoch_ms DESC, story_id ASC LIMIT ?
            """.trimIndent(),
            arrayOf(90, 90, first.nextCursor!!.storyId, 10),
        )
        assertUsesIndex(
            """
            SELECT * FROM library_entry
            WHERE origin_media_context = ?
            ORDER BY saved_at_epoch_ms DESC, story_id ASC LIMIT ?
            """.trimIndent(),
            arrayOf("MANGA", 7),
        )
    }

    @Test
    fun nonblankSearchUsesFtsAndNeverReturnsMoreThanTheRequestedWindow() = runBlocking {
        repeat(80) { index ->
            val title = if (index % 3 == 0) "Needle story $index" else "Other story $index"
            store.add(entry("story-$index", index.toLong(), title = title))
        }
        observedSql.clear()

        val window = store.observeWindow(LibraryQuery("needle", LibraryFilter.ALL, null, 6)).first()

        assertEquals(6, window.items.size)
        assertTrue(window.items.all { it.snapshot.title.startsWith("Needle") })
        assertTrue(observedSql.any { it.contains("library_search_fts match") && it.contains("limit") })
    }

    @Test
    fun libraryDatabaseContainsNoCatalogTablesOrForeignKeys() {
        val tables = database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table'",
        ).use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }
        assertTrue("library_entry" in tables)
        assertTrue("library_search_fts" in tables)
        assertFalse(tables.any { it.startsWith("catalog_") || it.startsWith("story_") })
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_list(library_entry)").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }

    private fun assertUsesIndex(sql: String, args: Array<Any?>) {
        val details = database.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql", args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(3)) }
        }
        assertTrue(details.joinToString().contains("INDEX", ignoreCase = true))
    }

    private fun entry(
        sourceStoryId: String,
        savedAtEpochMs: Long,
        mediaType: CatalogMediaType = CatalogMediaType.MANGA,
        title: String = "Title $sourceStoryId",
    ) = LibraryEntry(
        ref = ref(sourceStoryId),
        originMediaContext = mediaType,
        savedAtEpochMs = savedAtEpochMs,
        snapshot = LibraryPresentationSnapshot(title, artwork = null, supportingText = "Support $sourceStoryId"),
    )

    private fun ref(sourceStoryId: String): StorySourceRef = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, sourceStoryId)),
        catalogSourceKey = SOURCE_KEY,
        sourceStoryId = sourceStoryId,
    )

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("fixture.source")
    }
}
