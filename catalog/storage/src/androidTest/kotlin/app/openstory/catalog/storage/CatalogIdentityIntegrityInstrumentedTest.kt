package app.openstory.catalog.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CatalogIdentityIntegrityInstrumentedTest {
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
    fun sourceStoryKeyIsUniqueInDatabase() {
        insertIdentity("source-story:v1:${"1".repeat(64)}", "source-a", "item-a")

        assertConstraintFailure {
            insertIdentity("source-story:v1:${"2".repeat(64)}", "source-a", "item-a")
        }
    }

    @Test
    fun storyIdIsUniqueInDatabase() {
        val storyId = "source-story:v1:${"1".repeat(64)}"
        insertIdentity(storyId, "source-a", "item-a")

        assertConstraintFailure {
            insertIdentity(storyId, "source-b", "item-b")
        }
    }

    @Test
    fun differentSourceStoryKeyCannotReusePersistedStoryId() = runBlocking {
        val incoming = card("item-b", CatalogSectionKind.POPULAR, 0)
        insertIdentity(incoming.ref.storyId.value, "fixture.source", "item-a")

        val failure = assertCatalogFailure {
            store.publishDiscover(publication(listOf(incoming)), emptySet())
        }

        assertEquals(CatalogFailure.IdentityCollision(incoming.ref.storyId.value), failure)
        assertEquals(
            DiscoverPersistenceState.Absent,
            store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first(),
        )
    }

    @Test
    fun discoverPositionAndStoryUniquenessAreDatabaseBacked() = runBlocking {
        val first = card("item-a", CatalogSectionKind.POPULAR, 0)
        store.publishDiscover(publication(listOf(first)), emptySet())
        insertIdentity("source-story:v1:${"2".repeat(64)}", "fixture.source", "item-b")

        assertConstraintFailure {
            insertDiscoverCard(
                sourceStoryId = "item-b",
                storyId = "source-story:v1:${"2".repeat(64)}",
                itemPosition = 0,
            )
        }
        assertConstraintFailure {
            insertDiscoverCard(
                sourceStoryId = "item-a",
                storyId = first.ref.storyId.value,
                itemPosition = 1,
            )
        }
    }

    @Test
    fun invalidPositionIsRejectedAtStoreBoundaryWithoutCommit() = runBlocking {
        CatalogSectionKind.entries.forEach { kind ->
            listOf(-1, INVALID_POSITIONS.getValue(kind)).forEach { invalidPosition ->
                val mutableCards = mutableListOf(card("valid-$kind-$invalidPosition", kind, 0))
                val command = publication(mutableCards)
                mutableCards[0] = card("invalid-$kind-$invalidPosition", kind, invalidPosition)

                val failure = assertCatalogFailure {
                    store.publishDiscover(command, emptySet())
                }

                assertEquals(
                    CatalogFailure.Validation(
                        field = "cards[$kind].itemPosition",
                        reason = CatalogValidationReason.INVARIANT_VIOLATION,
                    ),
                    failure,
                )
                assertEquals(
                    DiscoverPersistenceState.Absent,
                    store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first(),
                )
            }
        }
    }

    @Test
    fun sectionMembershipOutsideCapIsRejectedAtStoreBoundaryWithoutCommit() = runBlocking {
        CatalogSectionKind.entries.forEach { kind ->
            val mutableCards = mutableListOf(card("valid-cap-$kind", kind, 0))
            val command = publication(mutableCards)
            mutableCards.clear()
            repeat(CatalogSectionCaps.cap(kind) + 1) { position ->
                mutableCards += card("over-cap-$kind-$position", kind, position)
            }

            val failure = assertCatalogFailure {
                store.publishDiscover(command, emptySet())
            }

            assertEquals(
                CatalogFailure.Validation(
                    field = "cards[$kind]",
                    reason = CatalogValidationReason.OVER_LIMIT,
                ),
                failure,
            )
            assertEquals(
                DiscoverPersistenceState.Absent,
                store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first(),
            )
        }
    }

    @Test
    fun generationOverflowFailsClosedWithoutReplacingPublishedState() = runBlocking {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO catalog_source_state(
                source_key, media_type, source_version, published_generation,
                published_at_epoch_ms, last_success_epoch_ms
            ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            arrayOf<Any?>("fixture.source", "MANGA", "fixture-v0", Long.MAX_VALUE, 1L, 1L),
        )

        val failure = assertCatalogFailure {
            store.publishDiscover(publication(emptyList()), emptySet())
        }

        assertEquals(CatalogFailure.InternalInvariant("discover_generation_overflow"), failure)
        val published = store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first()
            as DiscoverPersistenceState.Published
        assertEquals(Long.MAX_VALUE, published.generation)
        assertEquals("fixture-v0", published.provenance.sourceVersion)
    }

    private fun insertIdentity(storyId: String, sourceKey: String, sourceStoryId: String) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO story_source_identity(story_id, source_key, source_story_id) VALUES (?, ?, ?)",
            arrayOf(storyId, sourceKey, sourceStoryId),
        )
    }

    private fun insertDiscoverCard(
        sourceStoryId: String,
        storyId: String,
        itemPosition: Int,
    ) {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO discover_card(
                source_key, media_type, source_version, generation, section_kind, item_position,
                story_id, source_story_id, title, content_type, cover_locator_type,
                cover_locator_value, cover_locator_aux, cover_revision, rating_value, rating_scale,
                publication_status_summary, latest_update_epoch_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
            """.trimIndent(),
            arrayOf<Any?>(
                "fixture.source",
                "MANGA",
                "fixture-v1",
                1L,
                "POPULAR",
                itemPosition,
                storyId,
                sourceStoryId,
                "Injected",
                "MANGA",
            ),
        )
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
        val ref = StorySourceRef(
            storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, sourceStoryId)),
            catalogSourceKey = SOURCE_KEY,
            sourceStoryId = sourceStoryId,
        )
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

    private fun assertConstraintFailure(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected SQLite constraint failure")
        } catch (error: SQLiteConstraintException) {
            assertTrue(error.message.orEmpty().isNotEmpty())
        }
    }

    private suspend fun assertCatalogFailure(block: suspend () -> Unit): CatalogFailure = try {
        block()
        throw AssertionError("Expected CatalogFailureException")
    } catch (error: CatalogFailureException) {
        error.failure
    }

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("fixture.source")
        val INVALID_POSITIONS = mapOf(
            CatalogSectionKind.POPULAR to 5,
            CatalogSectionKind.LATEST_UPDATES to 9,
            CatalogSectionKind.TOP_RATED to 5,
        )
    }
}
