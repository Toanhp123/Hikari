package app.openstory.catalog.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.write.CatalogMutationBounds
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DiscoverPublicationRetentionInstrumentedTest {
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

    @Test
    fun failedReplacementLeavesPreviousGenerationAndCardsVisible() = runBlocking {
        store.publishDiscover(publication(listOf(card(ref("one"))), "discover-v1", 10L), emptySet())
        val before = store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first()
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_discover_insert
            BEFORE INSERT ON discover_card
            WHEN NEW.source_version = 'broken-v2'
            BEGIN
                SELECT RAISE(ABORT, 'forced publication failure');
            END
            """.trimIndent(),
        )

        val failure = assertCatalogFailure {
            store.publishDiscover(
                publication(listOf(card(ref("two"), sourceVersion = "broken-v2")), "broken-v2", 20L),
                emptySet(),
            )
        }

        assertEquals(CatalogFailure.Storage(CatalogStorageOperation.PUBLISH_DISCOVER), failure)
        assertEquals(before, store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first())
    }

    @Test
    fun successfulReplacementDeletesTheObsoleteGeneration() = runBlocking {
        store.publishDiscover(publication(listOf(card(ref("one"))), "discover-v1", 10L), emptySet())
        store.publishDiscover(
            publication(listOf(card(ref("two"), sourceVersion = "discover-v2")), "discover-v2", 20L),
            emptySet(),
        )

        assertEquals(listOf(2L), discoverGenerations())
    }

    @Test
    fun publicStoreBoundaryRejectsMutatedDuplicatePositionsAndStories() = runBlocking {
        val duplicatePositionCards = mutableListOf(card(ref("one")))
        val duplicatePosition = publication(duplicatePositionCards, "discover-v1", 10L)
        duplicatePositionCards += card(ref("two"))

        assertEquals(
            CatalogFailure.Validation("cards[POPULAR].itemPosition", CatalogValidationReason.INVARIANT_VIOLATION),
            assertCatalogFailure { store.publishDiscover(duplicatePosition, emptySet()) },
        )

        val duplicateStoryCards = mutableListOf(card(ref("same")))
        val duplicateStory = publication(duplicateStoryCards, "discover-v1", 10L)
        duplicateStoryCards += card(ref("same"), itemPosition = 1)

        assertEquals(
            CatalogFailure.Validation("cards[POPULAR].storyId", CatalogValidationReason.INVARIANT_VIOLATION),
            assertCatalogFailure { store.publishDiscover(duplicateStory, emptySet()) },
        )
        assertEquals(DiscoverPersistenceState.Absent, store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first())
    }

    @Test
    fun identityCollisionFailsClosedWithoutChangingPublishedState() = runBlocking {
        val incoming = ref("key-b")
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO story_source_identity(story_id, source_key, source_story_id) VALUES (?, ?, ?)",
            arrayOf(incoming.storyId.value, SOURCE_KEY.value, "key-a"),
        )

        val failure = assertCatalogFailure {
            store.publishDiscover(publication(listOf(card(incoming)), "discover-v1", 10L), emptySet())
        }

        assertEquals(CatalogFailure.IdentityCollision(incoming.storyId.value), failure)
        assertEquals(DiscoverPersistenceState.Absent, store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first())
        assertEquals(1, identityCount())
    }

    @Test
    fun publicationRejectsAnUnboundedRetentionProtectionSnapshot() = runBlocking {
        val protected = (0..CatalogMutationBounds.MAX_RETENTION_PROTECTED_STORY_IDS)
            .mapTo(linkedSetOf()) { ref("protected-$it").storyId }

        val failure = assertCatalogFailure {
            store.publishDiscover(publication(emptyList(), "discover-v1", 10L), protected)
        }

        assertEquals(CatalogFailure.InternalInvariant("retention_protected_story_limit"), failure)
        assertEquals(DiscoverPersistenceState.Absent, store.observe(SOURCE_KEY, CatalogMediaType.MANGA).first())
    }

    @Test
    fun existingStoryContentTypeCannotBeRewrittenByAnotherMediaScope() = runBlocking {
        val shared = ref("shared")
        store.publishDiscover(
            publication(listOf(card(shared, sourceVersion = "manga-v1")), "manga-v1", 10L),
            emptySet(),
        )

        val failure = assertCatalogFailure {
            store.publishDiscover(
                publication(
                    listOf(
                        card(
                            shared,
                            sourceVersion = "ln-v1",
                            contentType = CatalogMediaType.LIGHT_NOVEL,
                        ),
                    ),
                    "ln-v1",
                    20L,
                    CatalogMediaType.LIGHT_NOVEL,
                ),
                emptySet(),
            )
        }

        assertEquals(
            CatalogFailure.Validation("cards.contentType", CatalogValidationReason.AUTHORITY_MISMATCH),
            failure,
        )
        assertEquals(DiscoverPersistenceState.Absent, store.observe(SOURCE_KEY, CatalogMediaType.LIGHT_NOVEL).first())
    }

    @Test
    fun detailPublicationCannotRewriteAnExistingStoryContentType() = runBlocking {
        val shared = ref("shared")
        store.publishDiscover(
            publication(listOf(card(shared, sourceVersion = "manga-v1")), "manga-v1", 10L),
            emptySet(),
        )
        val conflicting = detailCommand(shared).let { command ->
            command.copy(summary = command.summary.copy(contentType = CatalogMediaType.LIGHT_NOVEL))
        }

        val failure = assertCatalogFailure { store.publishStoryDetail(conflicting) }

        assertEquals(
            CatalogFailure.Validation("summary.contentType", CatalogValidationReason.AUTHORITY_MISMATCH),
            failure,
        )
        assertEquals(CatalogMediaType.MANGA, store.observe(shared).first()?.summary?.contentType)
    }

    @Test
    fun repeatedStorySummaryUsesFixedSemanticPriorityInsteadOfInputOrder() = runBlocking {
        val shared = ref("shared")
        store.publishDiscover(
            publication(
                listOf(
                    card(shared, sectionKind = CatalogSectionKind.TOP_RATED, title = "Top-rated title"),
                    card(shared, sectionKind = CatalogSectionKind.POPULAR, title = "Popular title"),
                ),
                "discover-v1",
                10L,
            ),
            emptySet(),
        )

        assertEquals("Popular title", summaryTitle(shared))
    }

    @Test
    fun removedStoriesFollowCrossMediaPinnedSummaryOnlyAndDetailBranches() = runBlocking {
        val shared = ref("shared")
        val pinned = ref("pinned")
        val summaryOnly = ref("summary-only")
        val detailed = ref("detailed")
        store.publishDiscover(
            publication(
                listOf(
                    card(shared, itemPosition = 0),
                    card(pinned, itemPosition = 1),
                    card(summaryOnly, itemPosition = 2),
                    card(detailed, itemPosition = 3),
                ),
                "discover-v1",
                10L,
            ),
            emptySet(),
        )
        store.publishStoryDetail(detailCommand(detailed))
        insertRawCurrentDiscoverReachability(shared, CatalogMediaType.LIGHT_NOVEL)
        database.storyRetentionDao().touchOrphan(shared.storyId.value, 1L)

        val diagnostics = store.publishDiscover(
            publication(emptyList(), "discover-v2", 20L),
            setOf(pinned.storyId),
        )

        assertTrue(diagnostics.touchedStoryIds.size <= CatalogMutationBounds.MAX_DISCOVER_TOUCHED_STORY_IDS)
        assertTrue(identityExists(shared))
        assertFalse(orphanExists(shared))
        assertTrue(identityExists(pinned))
        assertTrue(orphanExists(pinned))
        assertFalse(identityExists(summaryOnly))
        assertFalse(orphanExists(summaryOnly))
        assertTrue(identityExists(detailed))
        assertTrue(orphanExists(detailed))

        val release = store.releaseStoryDemand(pinned, emptySet(), 30L)
        assertTrue(release.touchedStoryIds.size <= CatalogMutationBounds.MAX_RELEASE_TOUCHED_STORY_IDS)
        assertFalse(identityExists(pinned))
        assertFalse(orphanExists(pinned))
    }

    @Test
    fun maximumReplacementTouchesOnlyBoundedSnapshotsAndRetentionOverflow() = runBlocking {
        repeat(64) { index ->
            val command = detailCommand(ref("retained-$index"), acquiredAtEpochMs = index.toLong())
            store.publishStoryDetail(command)
            store.releaseStoryDemand(command.ref, emptySet(), index.toLong())
        }
        repeat(1_000) { index -> insertRawSummary(ref("historical-$index"), index.toLong()) }
        val previous = maxSnapshot("previous", "discover-v1")
        val current = maxSnapshot("current", "discover-v2")
        store.publishDiscover(publication(previous, "discover-v1", 100L), emptySet())
        previous.map { it.ref }.distinct().forEach { ref ->
            store.publishStoryDetail(detailCommand(ref, acquiredAtEpochMs = 110L))
        }

        val diagnostics = store.publishDiscover(publication(current, "discover-v2", 200L), emptySet())

        assertEquals(CatalogMutationBounds.MAX_DISCOVER_TOUCHED_STORY_IDS, diagnostics.touchedStoryIds.size)
        assertEquals(64, orphanCount())
        assertTrue(previous.all { it.ref.storyId in diagnostics.touchedStoryIds })
        assertTrue(current.all { it.ref.storyId in diagnostics.touchedStoryIds })
        assertTrue(identityExists(ref("historical-999")))
    }

    @Test
    fun abandonedPinnedCandidateCanBeEvictedByALaterBoundedMutation() = runBlocking {
        repeat(64) { index ->
            val command = detailCommand(ref("retained-$index"), acquiredAtEpochMs = 100L + index)
            store.publishStoryDetail(command)
            store.releaseStoryDemand(command.ref, emptySet(), 100L + index)
        }
        val abandoned = ref("abandoned")
        store.publishDiscover(publication(listOf(card(abandoned)), "discover-v1", 0L), emptySet())
        store.publishDiscover(publication(emptyList(), "discover-v2", 1L), setOf(abandoned.storyId))
        assertTrue(identityExists(abandoned))
        assertTrue(orphanExists(abandoned))

        val newcomer = detailCommand(ref("newcomer"), acquiredAtEpochMs = 200L)
        store.publishStoryDetail(newcomer)
        store.releaseStoryDemand(newcomer.ref, emptySet(), 200L)

        assertFalse(identityExists(abandoned))
        assertEquals(64, orphanCount())
    }

    private fun card(
        ref: StorySourceRef,
        sourceVersion: String = "discover-v1",
        itemPosition: Int = 0,
        sectionKind: CatalogSectionKind = CatalogSectionKind.POPULAR,
        contentType: CatalogMediaType = CatalogMediaType.MANGA,
        title: String = "Discover title",
    ) = DiscoverCard(
        ref = ref,
        sectionKind = sectionKind,
        itemPosition = itemPosition,
        title = title,
        contentType = contentType,
        sourceVersion = sourceVersion,
        coverLocator = null,
        coverAssetKey = null,
        rating = null,
        publicationStatusSummary = "Discover status",
        latestUpdateEpochMs = 10,
    )

    private fun detailCommand(
        ref: StorySourceRef,
        acquiredAtEpochMs: Long = 20L,
    ) = StoryDetailPublicationCommand(
        ref = ref,
        provenance = AcquisitionProvenance(SOURCE_KEY, "detail-v2", acquiredAtEpochMs),
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

    private fun publication(
        cards: List<DiscoverCard>,
        sourceVersion: String,
        acquiredAtEpochMs: Long,
        mediaType: CatalogMediaType = CatalogMediaType.MANGA,
    ) = DiscoverPublicationCommand(
        catalogSourceKey = SOURCE_KEY,
        mediaType = mediaType,
        provenance = AcquisitionProvenance(SOURCE_KEY, sourceVersion, acquiredAtEpochMs),
        cards = cards,
    )

    private fun maxSnapshot(prefix: String, sourceVersion: String): List<DiscoverCard> = buildList {
        val sections = listOf(
            CatalogSectionKind.POPULAR to 5,
            CatalogSectionKind.LATEST_UPDATES to 9,
            CatalogSectionKind.TOP_RATED to 5,
        )
        sections.forEach { (kind, count) ->
            repeat(count) { position ->
                add(
                    card(
                        ref = ref("$prefix-${kind.name.lowercase()}-$position"),
                        sourceVersion = sourceVersion,
                        itemPosition = position,
                        sectionKind = kind,
                    ),
                )
            }
        }
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

    private fun insertRawCurrentDiscoverReachability(
        ref: StorySourceRef,
        mediaType: CatalogMediaType,
    ) {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO catalog_source_state(
                source_key, media_type, source_version, published_generation,
                published_at_epoch_ms, last_success_epoch_ms
            ) VALUES (?, ?, 'raw-v1', 1, 1, 1)
            """.trimIndent(),
            arrayOf(SOURCE_KEY.value, mediaType.name),
        )
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO discover_card(
                source_key, media_type, source_version, generation, section_kind, item_position,
                story_id, source_story_id, title, content_type, cover_locator_type,
                cover_locator_value, cover_locator_aux, cover_revision, rating_value, rating_scale,
                publication_status_summary, latest_update_epoch_ms
            ) VALUES (?, ?, 'raw-v1', 1, 'POPULAR', 0, ?, ?, 'Raw reachable', 'MANGA',
                NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
            """.trimIndent(),
            arrayOf(SOURCE_KEY.value, mediaType.name, ref.storyId.value, ref.sourceStoryId),
        )
    }

    private fun discoverGenerations(): List<Long> = database.openHelper.readableDatabase.query(
        "SELECT DISTINCT generation FROM discover_card ORDER BY generation",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.getLong(0))
        }
    }

    private fun identityCount(): Int = database.openHelper.readableDatabase.query(
        "SELECT COUNT(*) FROM story_source_identity",
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun identityExists(ref: StorySourceRef): Boolean = database.openHelper.readableDatabase.query(
        "SELECT 1 FROM story_source_identity WHERE story_id = ?",
        arrayOf(ref.storyId.value),
    ).use { it.moveToFirst() }

    private fun orphanExists(ref: StorySourceRef): Boolean = database.openHelper.readableDatabase.query(
        "SELECT 1 FROM story_orphan_retention WHERE story_id = ?",
        arrayOf(ref.storyId.value),
    ).use { it.moveToFirst() }

    private fun orphanCount(): Int = database.openHelper.readableDatabase.query(
        "SELECT COUNT(*) FROM story_orphan_retention",
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun summaryTitle(ref: StorySourceRef): String = database.openHelper.readableDatabase.query(
        "SELECT title FROM story_source_summary WHERE story_id = ?",
        arrayOf(ref.storyId.value),
    ).use { cursor -> cursor.moveToFirst(); cursor.getString(0) }

    private suspend fun assertCatalogFailure(block: suspend () -> Unit): CatalogFailure = try {
        block()
        throw AssertionError("Expected CatalogFailureException")
    } catch (error: CatalogFailureException) {
        error.failure
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
