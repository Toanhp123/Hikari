package app.openstory.catalog.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.write.CatalogMutationBounds
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import app.openstory.catalog.storage.retention.StoryRetentionEntity
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoryRetentionInstrumentedTest {
    private lateinit var database: CatalogDatabase
    private lateinit var store: RoomCatalogStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomCatalogStore(database)
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun retentionQueriesStayKeyedAndBoundedOnAgedStorage() = runBlocking {
        store.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val observedSql = CopyOnWriteArrayList<String>()
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback({ sql, _ -> observedSql += sql.lowercase() }, Executor(Runnable::run))
            .build()
        store = RoomCatalogStore(database)
        repeat(500) { index -> insertRawStory("historical-$index", index.toLong()) }
        val dao = database.storyRetentionDao()
        val candidates = (0 until 64).map { index ->
            val ref = ref("historical-$index")
            StoryRetentionEntity(ref.storyId.value, index.toLong())
        }
        candidates.forEach { dao.touchOrphan(it.storyId, it.lastAccessedEpochMs) }
        val target = ref("historical-499")
        observedSql.clear()

        assertTrue(dao.hasDetail(target.storyId.value))
        assertFalse(dao.isReachableFromAnyCurrentDiscover(target.storyId.value))
        assertEquals(candidates, dao.oldestOrphans())

        assertTrue(observedSql.none { it.contains("count(") })
        assertTrue(observedSql.none { it.contains("order by") && it.contains("story_detail") })
        assertTrue(
            observedSql.filter { it.contains("from story_detail") }
                .all { it.contains("where story_id") },
        )
        assertTrue(
            observedSql.filter { it.contains("from story_orphan_retention") }
                .all { it.contains("limit 65") },
        )

        assertUsesIndex(
            "SELECT * FROM story_detail WHERE story_id = ?",
            arrayOf(target.storyId.value),
        )
        assertUsesIndex(
            """
            SELECT * FROM story_orphan_retention
            ORDER BY last_accessed_epoch_ms ASC, story_id ASC
            LIMIT 65
            """.trimIndent(),
        )
    }

    @Test
    fun releaseKeepsRetentionAtSixtyFourAndEvictsOnlyBoundedOldestCandidate() = runBlocking {
        repeat(65) { index ->
            val command = detailPublication("orphan-$index", index.toLong())
            store.publishStoryDetail(command)
            val diagnostics = store.releaseStoryDemand(command.ref, emptySet(), index.toLong())
            assertTrue(diagnostics.touchedStoryIds.size <= CatalogMutationBounds.MAX_RELEASE_TOUCHED_STORY_IDS)
            assertTrue(retentionCount() <= 64)
        }

        assertEquals(64, retentionCount())
        assertFalse(identityExists(ref("orphan-0")))
        assertTrue(identityExists(ref("orphan-64")))
    }

    @Test
    fun releaseDeletesUnreachableSummaryWhenNoDetailExists() = runBlocking {
        val ref = ref("summary-only")
        insertRawSummary(ref, 10)

        val diagnostics = store.releaseStoryDemand(ref, emptySet(), 20)

        assertEquals(setOf(ref.storyId), diagnostics.touchedStoryIds)
        assertFalse(identityExists(ref))
        assertEquals(0, retentionCount())
    }

    @Test
    fun reachableStoryIsNeverAddedToOrphanRetention() = runBlocking {
        val command = detailPublication("reachable", 10)
        store.publishStoryDetail(command)
        insertCurrentDiscoverReachability(command.ref)

        store.releaseStoryDemand(command.ref, emptySet(), 20)

        assertTrue(identityExists(command.ref))
        assertEquals(0, retentionCount())
    }

    @Test
    fun detailPublicationAdvancesAnExistingOrphanCandidateWithoutRegressingAccess() = runBlocking {
        val first = detailPublication("retained-detail", 20)
        store.publishStoryDetail(first)
        store.releaseStoryDemand(first.ref, emptySet(), 20)

        store.publishStoryDetail(detailPublication("retained-detail", 40))
        store.touchStoryAccess(first.ref, 30)

        assertEquals(40, retainedAccess(first.ref))
    }

    @Test
    fun childPositionUniquenessIsDatabaseBacked() = runBlocking {
        val command = detailPublication("child-constraint", 10)
        store.publishStoryDetail(command)

        assertSqlFailure {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO story_author(story_id, position, value) VALUES (?, 0, 'duplicate')",
                arrayOf(command.ref.storyId.value),
            )
        }
    }

    private fun assertUsesIndex(sql: String, args: Array<Any?> = emptyArray()) {
        val details = database.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql", args).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.getString(3))
            }
        }
        assertTrue(details.joinToString().contains("INDEX", ignoreCase = true))
    }

    private fun insertRawStory(sourceStoryId: String, epochMs: Long) {
        val ref = ref(sourceStoryId)
        insertRawSummary(ref, epochMs)
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO story_detail(
                story_id, source_key, source_story_id, source_version, description,
                publication_status, language, fetched_at_epoch_ms, last_accessed_epoch_ms
            ) VALUES (?, ?, ?, 'raw-v1', NULL, NULL, NULL, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>(ref.storyId.value, SOURCE_KEY.value, sourceStoryId, epochMs, epochMs),
        )
    }

    private fun insertRawSummary(ref: StorySourceRef, epochMs: Long) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO story_source_identity(story_id, source_key, source_story_id) VALUES (?, ?, ?)",
            arrayOf(ref.storyId.value, SOURCE_KEY.value, ref.sourceStoryId),
        )
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO story_source_summary(
                story_id, source_key, source_version, title, content_type, cover_locator_type,
                cover_locator_value, cover_locator_aux, cover_revision, rating_value, rating_scale,
                publication_status_summary, latest_update_epoch_ms, last_seen_epoch_ms
            ) VALUES (?, ?, 'raw-v1', ?, 'MANGA', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, ?)
            """.trimIndent(),
            arrayOf<Any?>(ref.storyId.value, SOURCE_KEY.value, "Raw ${ref.sourceStoryId}", epochMs),
        )
    }

    private fun insertCurrentDiscoverReachability(ref: StorySourceRef) {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO catalog_source_state(
                source_key, media_type, source_version, published_generation,
                published_at_epoch_ms, last_success_epoch_ms
            ) VALUES (?, 'MANGA', 'raw-v1', 1, 1, 1)
            """.trimIndent(),
            arrayOf(SOURCE_KEY.value),
        )
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO discover_card(
                source_key, media_type, source_version, generation, section_kind, item_position,
                story_id, source_story_id, title, content_type, cover_locator_type,
                cover_locator_value, cover_locator_aux, cover_revision, rating_value, rating_scale,
                publication_status_summary, latest_update_epoch_ms
            ) VALUES (?, 'MANGA', 'raw-v1', 1, 'POPULAR', 0, ?, ?, ?, 'MANGA',
                NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
            """.trimIndent(),
            arrayOf(SOURCE_KEY.value, ref.storyId.value, ref.sourceStoryId, "Reachable"),
        )
    }

    private fun retentionCount(): Int = database.openHelper.readableDatabase.query(
        "SELECT COUNT(*) FROM story_orphan_retention",
    ).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }

    private fun retainedAccess(ref: StorySourceRef): Long = database.openHelper.readableDatabase.query(
        "SELECT last_accessed_epoch_ms FROM story_orphan_retention WHERE story_id = ?",
        arrayOf(ref.storyId.value),
    ).use { cursor ->
        cursor.moveToFirst()
        cursor.getLong(0)
    }

    private fun identityExists(ref: StorySourceRef): Boolean = database.openHelper.readableDatabase.query(
        "SELECT 1 FROM story_source_identity WHERE story_id = ?",
        arrayOf(ref.storyId.value),
    ).use { it.moveToFirst() }

    private fun detailPublication(sourceStoryId: String, epochMs: Long): StoryDetailPublicationCommand {
        val ref = ref(sourceStoryId)
        return StoryDetailPublicationCommand(
            ref = ref,
            provenance = AcquisitionProvenance(SOURCE_KEY, "detail-v1", epochMs),
            summary = StorySummaryProjection(
                ref = ref,
                title = "Detail $sourceStoryId",
                contentType = CatalogMediaType.MANGA,
                sourceVersion = "detail-v1",
                coverLocator = null,
                coverAssetKey = null,
                rating = null,
                publicationStatusSummary = null,
                latestUpdateEpochMs = null,
            ),
            detail = StoryRichDetailProjection(
                description = "Description",
                authors = listOf("Author"),
                artists = listOf("Artist"),
                genres = listOf("Genre"),
                publicationStatus = "Ongoing",
                language = "English",
            ),
        )
    }

    private fun ref(sourceStoryId: String) = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, sourceStoryId)),
        catalogSourceKey = SOURCE_KEY,
        sourceStoryId = sourceStoryId,
    )

    private fun assertSqlFailure(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected SQLite failure")
        } catch (expected: RuntimeException) {
            assertTrue(expected.message.orEmpty().isNotEmpty())
        }
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("fixture.source")
    }
}
