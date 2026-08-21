package app.openstory.storage.room.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.evidence.CatalogEvidenceFingerprints
import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Story
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCatalogRepositoryTest {
    @Test
    fun sourceRecordsPersistCurrentExternalIdentifiersAndMetadataProvenance() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val key = CatalogMetadataKey(PluginId("a"), "a-1")
            val first = mutation("a", listOf("a-1"), 10)
            val identifiers = setOf(
                ExternalIdentifier("work", "work-1", ExternalIdentifierScope.WORK),
                ExternalIdentifier("edition", "edition-1", ExternalIdentifierScope.EDITION),
            )
            val entry = first.entries.single().copy(externalIdentifiers = identifiers)
            repository.commitHomeRefresh(
                first.copy(
                    entries = listOf(entry),
                    sections = listOf(first.sections.single().copy(items = listOf(entry))),
                ),
            )

            val summaryRecord = requireNotNull(repository.sourceRecord(key))
            assertEquals(identifiers, summaryRecord.entry.externalIdentifiers)
            assertEquals(10L, summaryRecord.summary.resolvedAtEpochMillis)
            assertEquals(null, summaryRecord.full)
            assertEquals(CatalogEvidenceFingerprints.identity(summaryRecord.entry), summaryRecord.identityFingerprint)
            assertEquals(
                CatalogEvidenceFingerprints.fusion(requireNotNull(repository.metadataSnapshot(key))),
                summaryRecord.fusionFingerprint,
            )

            val fullIdentifiers = setOf(
                ExternalIdentifier("work", "work-2", ExternalIdentifierScope.WORK),
            )
            repository.commitDetails(
                CatalogDetailsMutation(
                    summaryRecord.storyId,
                    summaryRecord.entry.copy(externalIdentifiers = fullIdentifiers, description = "full"),
                    "2.0.0",
                    20,
                ),
            )

            val fullRecord = requireNotNull(repository.sourceRecord(key))
            assertEquals(fullIdentifiers, fullRecord.entry.externalIdentifiers)
            assertEquals(20L, fullRecord.full?.resolvedAtEpochMillis)
            assertEquals(listOf(fullRecord), repository.sourceRecords(fullRecord.storyId))
            assertEquals(listOf(fullRecord), repository.sourceRecords())
        }
    }

    @Test
    fun newHomeStoryCreatesCanonicalStateAndFusionWorkInSameCommit() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 42))

            val state = requireNotNull(database.canonicalCatalogDao().canonicalState("story:a-1"))
            assertEquals("AUTO", state.preferenceMode)
            assertEquals("REEVALUATING", state.health)
            assertEquals(42L, state.createdAtEpochMillis)
            assertEquals(0L, state.identityRevision)
            val work = requireNotNull(database.canonicalCatalogDao().work("story:a-1", "FUSION_REBUILD"))
            assertEquals("story-created", work.reason)
        }
    }

    @Test
    fun newDetailsStoryCreatesCanonicalStateWithDetailsResolutionTime() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val storyId = StoryId("story:details-new")
            val entry = CatalogEntry(
                storyId = storyId,
                pluginId = PluginId("details"),
                sourceId = "source-1",
                title = "Details",
                contentType = ContentType.MANGA,
            )

            repository.commitDetails(CatalogDetailsMutation(storyId, entry, "1.0.0", 77))

            val state = requireNotNull(database.canonicalCatalogDao().canonicalState(storyId.value))
            assertEquals(77L, state.createdAtEpochMillis)
            assertEquals("AUTO", state.preferenceMode)
            assertEquals("story-created", database.canonicalCatalogDao().work(storyId.value, "FUSION_REBUILD")?.reason)
        }
    }

    @Test
    fun semanticHomeCommitReplacesOnlyOnePluginAndKeepsRemovedEntry() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            repository.commitHomeRefresh(mutation("b", listOf("b-1"), 2))
            repository.commitHomeRefresh(mutation("a", listOf("a-2"), 3))

            val homes = repository.observeHomes().first()
            assertEquals(listOf("a", "b"), homes.map { it.pluginId.value })
            assertEquals(listOf("a-2"), homes.first().sections.single().items.map { it.sourceId })
            assertEquals("a-1", repository.observeStory(StoryId("story:a-1")).first()!!.entries.single().sourceId)
        }
    }

    @Test
    fun semanticMetadataRoundTripsAndSparseRefreshPreservesNewerValues() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val base = mutation("a", listOf("a-1"), 1)
            val richEntry = base.entries.single().copy(
                publicationStatus = PublicationStatus.ONGOING,
                latestUpdate = CatalogLatestUpdate(500L, "128"),
            )
            repository.commitHomeRefresh(
                base.copy(
                    entries = listOf(richEntry),
                    sections = listOf(
                        CatalogHomeSection(
                            "section",
                            "Section",
                            listOf(richEntry),
                            CatalogFeedKind.TOP_RATED,
                        ),
                    ),
                ),
            )

            val home = repository.observeHomes().first().single()
            assertEquals(CatalogFeedKind.TOP_RATED, home.sections.single().kind)
            assertEquals(PublicationStatus.ONGOING, home.sections.single().items.single().publicationStatus)
            assertEquals(CatalogLatestUpdate(500L, "128"), home.sections.single().items.single().latestUpdate)

            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 2))
            var stored = repository.observeStory(StoryId("story:a-1")).first()!!.entries.single()
            assertEquals(PublicationStatus.ONGOING, stored.publicationStatus)
            assertEquals(CatalogLatestUpdate(500L, "128"), stored.latestUpdate)

            val older = mutation("a", listOf("a-1"), 3)
            val olderEntry = older.entries.single().copy(latestUpdate = CatalogLatestUpdate(400L, "120"))
            repository.commitHomeRefresh(
                older.copy(
                    entries = listOf(olderEntry),
                    sections = listOf(older.sections.single().copy(items = listOf(olderEntry))),
                ),
            )
            stored = repository.observeStory(StoryId("story:a-1")).first()!!.entries.single()
            assertEquals(CatalogLatestUpdate(500L, "128"), stored.latestUpdate)

            val newer = mutation("a", listOf("a-1"), 4)
            val newerEntry = newer.entries.single().copy(
                publicationStatus = PublicationStatus.COMPLETED,
                latestUpdate = CatalogLatestUpdate(600L, "130"),
            )
            repository.commitHomeRefresh(
                newer.copy(
                    entries = listOf(newerEntry),
                    sections = listOf(newer.sections.single().copy(items = listOf(newerEntry))),
                ),
            )
            stored = repository.observeStory(StoryId("story:a-1")).first()!!.entries.single()
            assertEquals(PublicationStatus.COMPLETED, stored.publicationStatus)
            assertEquals(CatalogLatestUpdate(600L, "130"), stored.latestUpdate)
        }
    }

    @Test
    fun homeCommitWritesSummaryWithoutRefreshingFull() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val key = CatalogMetadataKey(PluginId("a"), "a-1")
            val first = mutation("a", listOf("a-1"), 10)
            val withCover = first.entries.single().copy(coverUrl = "cover-10")
            repository.commitHomeRefresh(
                first.copy(
                    entries = listOf(withCover),
                    sections = listOf(first.sections.single().copy(items = listOf(withCover))),
                ),
            )

            var snapshot = requireNotNull(repository.metadataSnapshot(key))
            assertEquals("1.0.0", snapshot.summary.pluginVersion)
            assertEquals(10L, snapshot.summary.resolvedAtEpochMillis)
            assertEquals(null, snapshot.full)

            repository.commitDetails(
                CatalogDetailsMutation(
                    snapshot.entry.storyId,
                    snapshot.entry.copy(description = "details"),
                    "2.0.0",
                    20,
                ),
            )
            snapshot = requireNotNull(repository.metadataSnapshot(key))
            assertEquals(20L, snapshot.full?.resolvedAtEpochMillis)

            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 30))
            snapshot = requireNotNull(repository.metadataSnapshot(key))
            assertEquals(30L, snapshot.summary.resolvedAtEpochMillis)
            assertEquals("cover-10", snapshot.entry.coverUrl)
            assertEquals(20L, snapshot.full?.resolvedAtEpochMillis)
            assertEquals("details", snapshot.entry.description)
        }
    }

    @Test
    fun detailsCommitResolvesFullEvenWhenCoverIsNull() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            val key = CatalogMetadataKey(PluginId("a"), "a-1")
            val cached = requireNotNull(repository.metadataSnapshot(key)).entry

            repository.commitDetails(
                CatalogDetailsMutation(
                    cached.storyId,
                    cached.copy(coverUrl = null, description = "resolved"),
                    "2.0.0",
                    50,
                ),
            )

            val snapshot = requireNotNull(repository.metadataSnapshot(key))
            assertEquals("2.0.0", snapshot.summary.pluginVersion)
            assertEquals(50L, snapshot.summary.resolvedAtEpochMillis)
            assertEquals("2.0.0", snapshot.full?.pluginVersion)
            assertEquals(50L, snapshot.full?.resolvedAtEpochMillis)
        }
    }

    @Test
    fun detailsCommitPreservesExistingStoryIdWhenMutationProposesDifferentStory() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val existingStoryId = StoryId("story:existing")
            val proposedStoryId = StoryId("story:new")
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1, existingStoryId))
            val persisted = requireNotNull(
                repository.metadataSnapshot(CatalogMetadataKey(PluginId("a"), "a-1")),
            ).entry

            val result = repository.commitDetails(
                CatalogDetailsMutation(
                    proposedStoryId,
                    persisted.copy(storyId = proposedStoryId, description = "details"),
                    "2.0.0",
                    2,
                ),
            )

            assertEquals(existingStoryId, assertIs<Outcome.Success<StoryId>>(result).value)
            val snapshot = requireNotNull(
                repository.metadataSnapshot(CatalogMetadataKey(PluginId("a"), "a-1")),
            )
            assertEquals(existingStoryId, snapshot.entry.storyId)
            assertEquals("details", snapshot.entry.description)
        }
    }

    @Test
    fun detailsCommitReturnsDurableHomeIdentityWhenHomeWinsRace() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val homeStoryId = StoryId("story:home")
            val proposedStoryId = StoryId("story:details")
            val pluginId = PluginId("a")
            val sourceId = "a-1"
            val detailsEntry = CatalogEntry(
                storyId = proposedStoryId,
                pluginId = pluginId,
                sourceId = sourceId,
                title = "details",
                description = "resolved",
                contentType = ContentType.MANGA,
            )

            // The details caller computed proposedStoryId while the source was absent.
            repository.commitHomeRefresh(mutation("a", listOf(sourceId), 1, homeStoryId))
            val result = repository.commitDetails(
                CatalogDetailsMutation(proposedStoryId, detailsEntry, "2.0.0", 2),
            )

            assertEquals(homeStoryId, assertIs<Outcome.Success<StoryId>>(result).value)
            assertEquals(
                homeStoryId,
                requireNotNull(repository.metadataSnapshot(CatalogMetadataKey(pluginId, sourceId))).entry.storyId,
            )
        }
    }

    @Test
    fun homeCommitPreservesDurableDetailsIdentityWhenStaleHomeCommitsLater() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val pluginId = PluginId("a")
            val sourceId = "a-1"
            val detailsStoryId = StoryId("story:details")
            val staleHomeStoryId = StoryId("story:home")
            val detailsEntry = CatalogEntry(
                storyId = detailsStoryId,
                pluginId = pluginId,
                sourceId = sourceId,
                title = "details",
                description = "resolved",
                contentType = ContentType.MANGA,
            )

            repository.commitDetails(
                CatalogDetailsMutation(detailsStoryId, detailsEntry, "2.0.0", 2),
            )
            repository.commitHomeRefresh(mutation("a", listOf(sourceId), 3, staleHomeStoryId))

            val snapshot = requireNotNull(repository.metadataSnapshot(CatalogMetadataKey(pluginId, sourceId)))
            assertEquals(detailsStoryId, snapshot.entry.storyId)
            assertEquals("resolved", snapshot.entry.description)
            assertEquals(null, database.catalogDao().findStory(staleHomeStoryId.value))
        }
    }

    @Test
    fun storyProjectionObservationScopesRowsToRequestedStories() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            repository.commitHomeRefresh(mutation("b", listOf("b-1"), 2))
            val projections = RoomCatalogStoryProjectionRepository(database)

            val observed = projections.observeForStories(setOf(StoryId("story:a-1"))).first()

            assertEquals(listOf("story:a-1"), observed.map { it.storyId.value })
            assertEquals(emptyList(), projections.observeForStories(emptySet()).first())
        }
    }

    @Test
    fun detailEnrichmentDoesNotAlterHomeMembership() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            val before = repository.observeHomes().first()
            val entry = before.single().sections.single().items.single()

            val result = repository.commitDetails(
                CatalogDetailsMutation(
                    entry.storyId,
                    entry.copy(description = "rich details"),
                    "2.0.0",
                    2,
                ),
            )

            assertIs<Outcome.Success<StoryId>>(result)
            assertEquals(
                before.single().sections.flatMap { it.items }.map { it.pluginId to it.sourceId },
                repository.observeHomes().first().single().sections.flatMap { it.items }
                    .map { it.pluginId to it.sourceId },
            )
            assertEquals("rich details", repository.observeStory(entry.storyId).first()!!.entries.single().description)
        }
    }

    @Test
    fun detailEnrichmentDoesNotOverwriteCanonicalStoryContentType() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            val stored = repository.observeStory(StoryId("story:a-1")).first()!!

            repository.commitDetails(
                CatalogDetailsMutation(
                    stored.story.id,
                    stored.entries.single().copy(contentType = ContentType.WEB_NOVEL),
                    "2.0.0",
                    2,
                ),
            )

            val enriched = repository.observeStory(stored.story.id).first()!!
            assertEquals(ContentType.MANGA, enriched.story.contentType)
            assertEquals(ContentType.WEB_NOVEL, enriched.entries.single().contentType)
        }
    }

    @Test
    fun failedHomeMutationLeavesPreviousSnapshotAndFreshness() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            val before = repository.observeHomes().first()
            database.openHelper.writableDatabase.execSQL(
                """CREATE TRIGGER reject_snapshot_update
                   BEFORE INSERT ON catalog_home_snapshots
                   WHEN NEW.refreshed_at_epoch_millis = 2
                   BEGIN SELECT RAISE(ABORT, 'forced failure'); END""",
            )

            val failed = repository.commitHomeRefresh(
                mutation("a", listOf("a-2"), 2),
            )

            assertIs<Outcome.Failure<*>>(failed)
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_snapshot_update")
            assertEquals(before, repository.observeHomes().first())
        }
    }

    @Test
    fun matchSnapshotCollapsesSourceEntriesIntoOneCanonicalCandidate() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1, StoryId("story:shared")))
            repository.commitHomeRefresh(mutation("b", listOf("b-1"), 2, StoryId("story:shared")))

            val candidate = repository.matchSnapshot().candidates.single()

            assertEquals(StoryId("story:shared"), candidate.story.id)
            assertEquals(setOf("a", "b"), candidate.sourceKeys.map { it.pluginId.value }.toSet())
            assertEquals(setOf("a-1", "b-1"), candidate.sourceKeys.map { it.sourceId }.toSet())
            assertEquals(2, candidate.evidence.size)
        }
    }

    private fun mutation(
        plugin: String,
        sourceIds: List<String>,
        timestamp: Long,
        canonicalStoryId: StoryId? = null,
    ): CatalogHomeMutation {
        val pluginId = PluginId(plugin)
        val entries = sourceIds.map { sourceId ->
            CatalogEntry(
                canonicalStoryId ?: StoryId("story:$sourceId"),
                pluginId,
                sourceId,
                sourceId,
                contentType = ContentType.MANGA,
            )
        }
        return CatalogHomeMutation(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = timestamp,
            stories = entries.map { Story(it.storyId, it.contentType) },
            entries = entries,
            sections = listOf(CatalogHomeSection("section", "Section", entries)),
            orderedSourceItemIds = mapOf("section" to sourceIds),
        )
    }

    private suspend fun withDatabase(block: suspend (OpenStoryDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }
}
