package app.openstory.catalog.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiscoverPersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: CatalogDatabase
    private lateinit var store: RoomCatalogStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
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
    fun freshDatabaseIsAbsent() = runBlocking {
        assertEquals(
            DiscoverPersistenceState.Absent,
            store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first(),
        )
    }

    @Test
    fun successfulZeroCardPublicationIsPublishedEmpty() = runBlocking {
        store.publishDiscover(publication(emptyList()), emptySet())

        assertEquals(
            DiscoverPersistenceState.Published(
                generation = 1,
                provenance = provenance(),
                cards = emptyList(),
            ),
            store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first(),
        )
    }

    @Test
    fun publishedEmptySurvivesFactoryCloseAndReopen() = runBlocking {
        store.close()
        val databaseName = "discover-reopen-${System.nanoTime()}.db"
        context.deleteDatabase(databaseName)
        try {
            val factory = CatalogStorageFactory(context, databaseName)
            factory.open().use { firstStore ->
                firstStore.publishDiscover(publication(emptyList()), emptySet())
                firstStore.close()
            }

            factory.open().use { reopenedStore ->
                assertEquals(
                    DiscoverPersistenceState.Published(1, provenance(), emptyList()),
                    reopenedStore.observe(SOURCE_KEY, CatalogMediaType.MANGA).first(),
                )
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun publishedCardsUseFixedSectionThenItemOrdering() = runBlocking {
        val cards = listOf(
            card("top-1", CatalogSectionKind.TOP_RATED, 1),
            card("latest-0", CatalogSectionKind.LATEST_UPDATES, 0),
            card("popular-1", CatalogSectionKind.POPULAR, 1),
            card("top-0", CatalogSectionKind.TOP_RATED, 0),
            card("popular-0", CatalogSectionKind.POPULAR, 0),
        )

        store.publishDiscover(publication(cards), emptySet())

        val published = store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first()
            as DiscoverPersistenceState.Published
        assertEquals(
            listOf("popular-0", "popular-1", "latest-0", "top-0", "top-1"),
            published.cards.map { it.ref.sourceStoryId },
        )
    }

    @Test
    fun publishedCardsRoundTripMaterializedFields() = runBlocking {
        val localRef = ref("local")
        val remoteRef = ref("remote")
        val localLocator = CoverLocator.TrustedLocalResource("covers/local", "asset-v2")
        val localCard = card("local", CatalogSectionKind.POPULAR, 0).copy(
            title = "Local title",
            coverLocator = localLocator,
            coverAssetKey = CoverAssetKey(
                localRef.storyId,
                CoverRevisionV1.local(localLocator.logicalAssetId, localLocator.assetVersion),
            ),
            rating = CatalogRating(8.25, 10.0),
            publicationStatusSummary = "Ongoing",
            latestUpdateEpochMs = 9876,
        )
        val remoteUri = RemoteHttpsUriV1.parseAndNormalize("https://cdn.example.com/cover.webp?sig=A%2F")
        val remoteRevision = CoverRevisionV1.remoteUri(remoteUri)
        val remoteCard = card("remote", CatalogSectionKind.POPULAR, 1).copy(
            title = "Remote title",
            coverLocator = CoverLocator.RemoteHttps(SOURCE_KEY, remoteUri, remoteRevision),
            coverAssetKey = CoverAssetKey(remoteRef.storyId, remoteRevision),
        )

        store.publishDiscover(publication(listOf(localCard, remoteCard)), emptySet())

        val published = store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first()
            as DiscoverPersistenceState.Published
        assertEquals(listOf(localCard, remoteCard), published.cards)
    }

    @Test
    fun oneLiveObservationExecutesOneCoherentQueryPerSnapshot() = runBlocking {
        store.close()
        val coherentQueries = AtomicInteger(0)
        val queryExecutor = Executor { command -> command.run() }
        database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .setQueryCallback(
                { sql, _ ->
                    if (sql.contains("LEFT JOIN discover_card", ignoreCase = true)) {
                        coherentQueries.incrementAndGet()
                    }
                },
                queryExecutor,
            )
            .build()
        store = RoomCatalogStore(database)
        val firstEmission = CompletableDeferred<Unit>()
        val secondEmission = CompletableDeferred<Unit>()
        val observed = mutableListOf<DiscoverPersistenceState>()
        val collection = launch(Dispatchers.Default) {
            store.observe(SOURCE_KEY, CatalogMediaType.MANGA).collect { state ->
                observed += state
                if (observed.size == 1) firstEmission.complete(Unit)
                if (observed.size == 2) secondEmission.complete(Unit)
            }
        }

        try {
            withTimeout(5_000) { firstEmission.await() }
            store.publishDiscover(publication(listOf(card("one", CatalogSectionKind.POPULAR, 0))), emptySet())
            withTimeout(5_000) { secondEmission.await() }

            assertEquals(2, coherentQueries.get())
            assertEquals(DiscoverPersistenceState.Absent, observed[0])
            assertTrue(observed[1] is DiscoverPersistenceState.Published)
        } finally {
            collection.cancelAndJoin()
        }
    }

    @Test
    fun discoverReadNeedsNoDetailOrChildTables() = runBlocking {
        store.publishDiscover(publication(listOf(card("one", CatalogSectionKind.POPULAR, 0))), emptySet())

        val cursor = database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name",
        )
        val tableNames = buildList {
            cursor.use {
                while (it.moveToNext()) add(it.getString(0))
            }
        }

        assertFalse(tableNames.contains("story_detail"))
        assertFalse(tableNames.contains("story_author"))
        assertFalse(tableNames.contains("story_artist"))
        assertFalse(tableNames.contains("story_genre"))
        assertTrue(store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first() is DiscoverPersistenceState.Published)
    }

    private fun publication(cards: List<DiscoverCard>) = DiscoverPublicationCommand(
        catalogSourceKey = SOURCE_KEY,
        mediaType = CatalogMediaType.MANGA,
        provenance = provenance(),
        cards = cards,
    )

    private fun provenance() = AcquisitionProvenance(
        catalogSourceKey = SOURCE_KEY,
        sourceVersion = "fixture-v1",
        acquiredAtEpochMs = 1234,
    )

    private fun card(
        sourceStoryId: String,
        sectionKind: CatalogSectionKind,
        itemPosition: Int,
    ): DiscoverCard {
        val ref = ref(sourceStoryId)
        return DiscoverCard(
            ref = ref,
            sectionKind = sectionKind,
            itemPosition = itemPosition,
            title = "Title $sourceStoryId",
            contentType = CatalogMediaType.MANGA,
            sourceVersion = "fixture-v1",
            coverLocator = null,
            coverAssetKey = null,
            rating = null,
            publicationStatusSummary = null,
            latestUpdateEpochMs = null,
        )
    }

    private fun ref(sourceStoryId: String) = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, sourceStoryId)),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = sourceStoryId,
        )

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("fixture.source")
    }
}
