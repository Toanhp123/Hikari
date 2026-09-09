package app.openstory.catalog.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StoryDetailPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: CatalogDatabase
    private lateinit var store: RoomCatalogStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        openDatabase()
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun discoverSummaryIsObservableBeforeDetailEnrichment() = runBlocking {
        val card = discoverCard("summary-only", "Discover title", "discover-v1")
        store.publishDiscover(discoverPublication(card, "discover-v1", 100), emptySet())

        val projection = store.observe(card.ref).first()

        assertEquals(card.ref, projection?.ref)
        assertEquals("Discover title", projection?.summary?.title)
        assertNull(projection?.detail)
        assertNull(projection?.detailProvenance)
    }

    @Test
    fun detailRoundTripsBoundedChildrenInDeterministicOrder() = runBlocking {
        val command = detailPublication(
            sourceStoryId = "round-trip",
            sourceVersion = "detail-v1",
            acquiredAtEpochMs = 200,
            detail = StoryRichDetailProjection(
                description = "Complete description",
                authors = listOf("Author B", "Author A"),
                artists = listOf("Artist B", "Artist A"),
                genres = listOf("Drama", "Action"),
                publicationStatus = "Ongoing",
                language = "English",
            ),
        )

        store.publishStoryDetail(command)

        assertEquals(expectedProjection(command), store.observe(command.ref).first())
    }

    @Test
    fun oneStorySnapshotUsesFourQueriesRegardlessOfUnrelatedRows() = runBlocking {
        store.close()
        val storyQueries = AtomicInteger(0)
        val queryExecutor = Executor(Runnable::run)
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                { sql, _ ->
                    val normalized = sql.trimStart().lowercase()
                    if (normalized.startsWith("select") && STORY_QUERY_TABLES.any(normalized::contains)) {
                        storyQueries.incrementAndGet()
                    }
                },
                queryExecutor,
            )
            .build()
        store = RoomCatalogStore(database)
        repeat(250) { index -> insertRawStory("unrelated-$index", index.toLong()) }
        val command = detailPublication("query-target", "detail-v1", 500)
        store.publishStoryDetail(command)
        storyQueries.set(0)

        assertEquals(expectedProjection(command), store.observe(command.ref).first())
        assertEquals(4, storyQueries.get())
    }

    @Test
    fun failedDetailTransactionPreservesCompletePreviousProjection() = runBlocking {
        val first = detailPublication("rollback", "detail-v1", 100)
        store.publishStoryDetail(first)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER reject_exploding_genre
            BEFORE INSERT ON story_genre
            WHEN NEW.value = 'explode'
            BEGIN
                SELECT RAISE(ABORT, 'forced detail failure');
            END
            """.trimIndent(),
        )
        val replacement = detailPublication(
            sourceStoryId = "rollback",
            sourceVersion = "detail-v2",
            acquiredAtEpochMs = 200,
            detail = detail().copy(
                description = "Replacement description",
                authors = listOf("Replacement author"),
                artists = listOf("Replacement artist"),
                genres = listOf("explode"),
            ),
        )

        val failure = assertCatalogFailure { store.publishStoryDetail(replacement) }

        assertEquals(CatalogFailure.Storage(CatalogStorageOperation.PUBLISH_STORY), failure)
        assertEquals(expectedProjection(first), store.observe(first.ref).first())
    }

    @Test
    fun laterDiscoverPublicationDoesNotRewriteDetailProvenance() = runBlocking {
        val detail = detailPublication("independent-provenance", "detail-v1", 100)
        store.publishStoryDetail(detail)
        val refreshedCard = discoverCard(detail.ref.sourceStoryId, "New Discover title", "discover-v2")

        store.publishDiscover(discoverPublication(refreshedCard, "discover-v2", 300), emptySet())

        val projection = requireNotNull(store.observe(detail.ref).first())
        assertEquals("discover-v2", projection.summary.sourceVersion)
        assertEquals("New Discover title", projection.summary.title)
        assertEquals(detail.provenance, projection.detailProvenance)
        assertEquals(detail.detail, projection.detail)
    }

    @Test
    fun observationNeverTouchesAccessAndExplicitTouchWritesOnce() = runBlocking {
        store.close()
        val accessUpdates = AtomicInteger(0)
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCallback(
                { sql, _ ->
                    if (sql.trimStart().lowercase().startsWith("update story_detail")) {
                        accessUpdates.incrementAndGet()
                    }
                },
                Executor(Runnable::run),
            )
            .build()
        store = RoomCatalogStore(database)
        val command = detailPublication("access", "detail-v1", 100)
        store.publishStoryDetail(command)
        database.storyRetentionDao().touchOrphan(command.ref.storyId.value, 100)
        accessUpdates.set(0)

        store.observe(command.ref).first()
        store.observe(command.ref).first()
        assertEquals(0, accessUpdates.get())
        assertEquals(100, lastAccessed(command.ref))

        store.touchStoryAccess(command.ref, 250)

        assertEquals(1, accessUpdates.get())
        assertEquals(250, lastAccessed(command.ref))
        assertEquals(250, retainedAccess(command.ref))
    }

    @Test
    fun storageBoundaryRevalidatesMutableDetailBeforeOpeningTransaction() = runBlocking {
        val original = detailPublication("revalidate", "detail-v1", 100)
        store.publishStoryDetail(original)
        val mutableAuthors = mutableListOf("valid")
        val replacement = detailPublication(
            sourceStoryId = "revalidate",
            sourceVersion = "detail-v2",
            acquiredAtEpochMs = 200,
            detail = detail().copy(authors = mutableAuthors),
        )
        repeat(32) { mutableAuthors += "overflow-$it" }

        val failure = assertCatalogFailure { store.publishStoryDetail(replacement) }

        assertEquals(
            CatalogFailure.Validation("authors", CatalogValidationReason.OVER_LIMIT),
            failure,
        )
        assertEquals(expectedProjection(original), store.observe(original.ref).first())
    }

    @Test
    fun detailEnrichmentDoesNotRewriteMaterializedDiscoverCard() = runBlocking {
        val card = discoverCard("materialized", "Discover title", "discover-v1")
        store.publishDiscover(discoverPublication(card, "discover-v1", 100), emptySet())
        val detail = detailPublication("materialized", "detail-v2", 200).copy(
            summary = summary(card.ref, "Detail title", "detail-v2"),
        )

        store.publishStoryDetail(detail)

        val discover = store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first()
            as DiscoverPersistenceState.Published
        assertEquals(card, discover.cards.single())
        assertEquals("Detail title", store.observe(card.ref).first()?.summary?.title)
    }

    private fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomCatalogStore(database)
    }

    private fun insertRawStory(sourceStoryId: String, epochMs: Long) {
        val ref = ref(sourceStoryId)
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO story_source_identity(story_id, source_key, source_story_id) VALUES (?, ?, ?)",
            arrayOf(ref.storyId.value, SOURCE_KEY.value, sourceStoryId),
        )
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO story_source_summary(
                story_id, source_key, source_version, title, content_type, cover_locator_type,
                cover_locator_value, cover_locator_aux, cover_revision, rating_value, rating_scale,
                publication_status_summary, latest_update_epoch_ms, last_seen_epoch_ms
            ) VALUES (?, ?, 'raw-v1', ?, 'MANGA', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, ?)
            """.trimIndent(),
            arrayOf<Any?>(ref.storyId.value, SOURCE_KEY.value, "Raw $sourceStoryId", epochMs),
        )
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

    private fun lastAccessed(ref: StorySourceRef): Long = database.openHelper.readableDatabase.query(
        "SELECT last_accessed_epoch_ms FROM story_detail WHERE story_id = ?",
        arrayOf(ref.storyId.value),
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun retainedAccess(ref: StorySourceRef): Long = database.openHelper.readableDatabase.query(
        "SELECT last_accessed_epoch_ms FROM story_orphan_retention WHERE story_id = ?",
        arrayOf(ref.storyId.value),
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getLong(0)
    }

    private fun expectedProjection(command: StoryDetailPublicationCommand) = StoryDetailProjection(
        ref = command.ref,
        summary = command.summary,
        detail = command.detail,
        detailProvenance = command.provenance,
    )

    private fun detailPublication(
        sourceStoryId: String,
        sourceVersion: String,
        acquiredAtEpochMs: Long,
        detail: StoryRichDetailProjection = detail(),
    ): StoryDetailPublicationCommand {
        val ref = ref(sourceStoryId)
        return StoryDetailPublicationCommand(
            ref = ref,
            provenance = provenance(sourceVersion, acquiredAtEpochMs),
            summary = summary(ref, "Detail $sourceStoryId", sourceVersion),
            detail = detail,
        )
    }

    private fun detail() = StoryRichDetailProjection(
        description = "Description",
        authors = listOf("Author"),
        artists = listOf("Artist"),
        genres = listOf("Genre"),
        publicationStatus = "Ongoing",
        language = "English",
    )

    private fun summary(ref: StorySourceRef, title: String, sourceVersion: String) =
        StorySummaryProjection(
            ref = ref,
            title = title,
            contentType = CatalogMediaType.MANGA,
            sourceVersion = sourceVersion,
            coverLocator = null,
            coverAssetKey = null,
            rating = null,
            publicationStatusSummary = null,
            latestUpdateEpochMs = null,
        )

    private fun discoverCard(sourceStoryId: String, title: String, sourceVersion: String): DiscoverCard {
        val ref = ref(sourceStoryId)
        return DiscoverCard(
            ref = ref,
            sectionKind = CatalogSectionKind.POPULAR,
            itemPosition = 0,
            title = title,
            contentType = CatalogMediaType.MANGA,
            sourceVersion = sourceVersion,
            coverLocator = null,
            coverAssetKey = null,
            rating = null,
            publicationStatusSummary = null,
            latestUpdateEpochMs = null,
        )
    }

    private fun discoverPublication(
        card: DiscoverCard,
        sourceVersion: String,
        acquiredAtEpochMs: Long,
    ) = DiscoverPublicationCommand(
        catalogSourceKey = SOURCE_KEY,
        mediaType = CatalogMediaType.MANGA,
        provenance = provenance(sourceVersion, acquiredAtEpochMs),
        cards = listOf(card),
    )

    private fun provenance(sourceVersion: String, acquiredAtEpochMs: Long) = AcquisitionProvenance(
        catalogSourceKey = SOURCE_KEY,
        sourceVersion = sourceVersion,
        acquiredAtEpochMs = acquiredAtEpochMs,
    )

    private fun ref(sourceStoryId: String) = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, sourceStoryId)),
        catalogSourceKey = SOURCE_KEY,
        sourceStoryId = sourceStoryId,
    )

    private suspend fun assertCatalogFailure(block: suspend () -> Unit): CatalogFailure = try {
        block()
        throw AssertionError("Expected CatalogFailureException")
    } catch (error: CatalogFailureException) {
        error.failure
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("fixture.source")
        val STORY_QUERY_TABLES = listOf(
            "story_source_summary",
            "story_author",
            "story_artist",
            "story_genre",
        )
    }
}
