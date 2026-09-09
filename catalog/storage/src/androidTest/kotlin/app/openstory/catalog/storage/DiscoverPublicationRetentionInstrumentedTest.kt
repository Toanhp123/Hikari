package app.openstory.catalog.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DiscoverPublicationRetentionInstrumentedTest {
    private lateinit var store: RoomCatalogStore

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, CatalogDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomCatalogStore(database)
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun writePortDetailPublicationLeavesCurrentDiscoverSnapshotUnchanged() = runBlocking {
        val writePort: CatalogWritePort = store
        val ref = ref("one")
        val card = card(ref)
        writePort.publishDiscover(
            DiscoverPublicationCommand(
                catalogSourceKey = SOURCE_KEY,
                mediaType = CatalogMediaType.MANGA,
                provenance = AcquisitionProvenance(SOURCE_KEY, "discover-v1", 10),
                cards = listOf(card),
            ),
            emptySet(),
        )
        val before = store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first()

        writePort.publishStoryDetail(detailCommand(ref))

        val after = store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first()
        assertEquals(before, after)
        assertEquals(listOf(card), (after as DiscoverPersistenceState.Published).cards)
    }

    private fun card(ref: StorySourceRef) = DiscoverCard(
        ref = ref,
        sectionKind = CatalogSectionKind.POPULAR,
        itemPosition = 0,
        title = "Discover title",
        contentType = CatalogMediaType.MANGA,
        sourceVersion = "discover-v1",
        coverLocator = null,
        coverAssetKey = null,
        rating = null,
        publicationStatusSummary = "Discover status",
        latestUpdateEpochMs = 10,
    )

    private fun detailCommand(ref: StorySourceRef) = StoryDetailPublicationCommand(
        ref = ref,
        provenance = AcquisitionProvenance(SOURCE_KEY, "detail-v2", 20),
        summary = StorySummaryProjection(
            ref = ref,
            title = "Detail title",
            contentType = CatalogMediaType.MANGA,
            sourceVersion = "detail-v2",
            coverLocator = null,
            coverAssetKey = null,
            rating = null,
            publicationStatusSummary = "Detail status",
            latestUpdateEpochMs = 20,
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

    private fun ref(sourceStoryId: String) = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, sourceStoryId)),
        catalogSourceKey = SOURCE_KEY,
        sourceStoryId = sourceStoryId,
    )

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("fixture.source")
    }
}
