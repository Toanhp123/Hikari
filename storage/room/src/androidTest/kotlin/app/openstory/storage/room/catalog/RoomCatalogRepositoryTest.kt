package app.openstory.storage.room.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.canonical.CanonicalFieldContributor
import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalFieldProvenance
import app.openstory.catalog.canonical.CanonicalFieldStrategy
import app.openstory.catalog.canonical.CanonicalGeneration
import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalMetadata
import app.openstory.catalog.evidence.CatalogEvidenceFingerprints
import app.openstory.catalog.identity.ExternalIdentifier
import app.openstory.catalog.identity.ExternalIdentifierScope
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataSnapshot
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Story
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
import app.openstory.catalog.repository.CatalogDetailsMutation
import app.openstory.catalog.repository.CatalogHomeMutation
import app.openstory.catalog.repository.CatalogSearchSummaryCommitResult
import app.openstory.catalog.repository.CatalogSearchSummaryMutation
import app.openstory.common.Outcome
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
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
    fun summaryOmissionPreservesFullIdentifiersAndEmptyDetailsRetractsThem() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val storyId = StoryId("story:a-1")
            val pluginId = PluginId("a")
            val identifier = ExternalIdentifier("work", "work-1", ExternalIdentifierScope.WORK)
            val detailsEntry = CatalogEntry(
                storyId = storyId,
                pluginId = pluginId,
                sourceId = "a-1",
                title = "Details",
                contentType = ContentType.MANGA,
                externalIdentifiers = setOf(identifier),
            )
            repository.commitDetails(CatalogDetailsMutation(storyId, detailsEntry, "2.0.0", 20L))

            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 30L))

            val key = CatalogMetadataKey(pluginId, "a-1")
            assertEquals(setOf(identifier), requireNotNull(repository.metadataSnapshot(key)).entry.externalIdentifiers)

            repository.commitDetails(
                CatalogDetailsMutation(storyId, detailsEntry.copy(externalIdentifiers = emptySet()), "3.0.0", 40L),
            )
            assertEquals(emptySet(), requireNotNull(repository.metadataSnapshot(key)).entry.externalIdentifiers)
        }
    }

    @Test
    fun newHomeStoryCreatesCanonicalStateAndTransactionalOutbox() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 42))

            val state = requireNotNull(database.canonicalCatalogDao().canonicalState("story:a-1"))
            assertEquals("AUTO", state.preferenceMode)
            assertEquals("REEVALUATING", state.health)
            assertEquals(42L, state.createdAtEpochMillis)
            assertEquals(0L, state.identityRevision)
            assertNull(database.canonicalCatalogDao().work("story:a-1", "FUSION_REBUILD"))
            assertNull(database.canonicalCatalogDao().work("story:a-1", "RECONCILIATION_REEVALUATION"))
            assertEquals(1, database.canonicalCatalogDao().pendingOutbox(10).size)
        }
    }

    @Test
    fun materializingOutboxAcknowledgesOnlyAfterQueueRepresentationExists() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 42))
            val outbox = RoomCatalogChangeOutboxRepository(database, app.openstory.common.FakeClock(43))

            assertEquals(1, database.canonicalCatalogDao().pendingOutbox(10).size)
            assertNull(database.canonicalCatalogDao().work("story:a-1", "FUSION_REBUILD"))
            assertEquals(1, outbox.materializePending(10))
            assertTrue(database.canonicalCatalogDao().pendingOutbox(10).isEmpty())
            assertTrue(database.canonicalCatalogDao().work("story:a-1", "FUSION_REBUILD") != null)
            assertTrue(database.canonicalCatalogDao().work("story:a-1", "RECONCILIATION_REEVALUATION") != null)
        }
    }

    @Test
    fun oneThousandCatalogChangesRemainDurableAndMaterializeToTwoWorkRowsPerStory() = runTest {
        withDatabase { database ->
            val sourceIds = (0 until 1_000).map { index -> "scale-$index" }
            val repository = RoomCatalogRepository(database)
            val base = mutation("scale", sourceIds, 42)
            val entries = base.entries.map { entry ->
                entry.copy(
                    externalIdentifiers = setOf(
                        ExternalIdentifier("scale", entry.sourceId, ExternalIdentifierScope.WORK),
                    ),
                )
            }
            val result = repository.commitHomeRefresh(
                base.copy(
                    entries = entries,
                    sections = listOf(base.sections.single().copy(items = entries)),
                ),
            )
            val outbox = RoomCatalogChangeOutboxRepository(database, app.openstory.common.FakeClock(43))
            val work = RoomCanonicalEngineWorkRepository(database, app.openstory.common.FakeClock(43))

            assertIs<Outcome.Success<*>>(result)
            val records = repository.sourceRecords()
            assertEquals(1_000, records.size)
            records.forEach { record ->
                assertEquals(record.storyId, record.entry.storyId)
                assertEquals(setOf(record.key.sourceId), record.entry.externalIdentifiers.map { it.value }.toSet())
                assertEquals(CatalogEvidenceFingerprints.identity(record.entry), record.identityFingerprint)
                assertEquals(
                    CatalogEvidenceFingerprints.fusion(
                        CatalogMetadataSnapshot(record.entry, record.summary, record.full),
                    ),
                    record.fusionFingerprint,
                )
            }
            assertEquals(1_000, database.canonicalCatalogDao().pendingOutbox(1_000).size)
            assertEquals(1_000, outbox.materializePending(1_000))
            assertTrue(database.canonicalCatalogDao().pendingOutbox(1).isEmpty())

            val reconciliation = work.claimReady(43, 1_000)
            val fusion = work.claimReady(43, 1_000)
            assertEquals(1_000, reconciliation.size)
            assertTrue(reconciliation.all { it.type == CanonicalEngineWorkType.RECONCILIATION_REEVALUATION })
            assertEquals(1_000, fusion.size)
            assertTrue(fusion.all { it.type == CanonicalEngineWorkType.FUSION_REBUILD })
        }
    }

    @Test
    fun newDetailsStoryCreatesCanonicalStateAndTransactionalOutbox() = runTest {
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
            assertNull(database.canonicalCatalogDao().work(storyId.value, "FUSION_REBUILD"))
            assertNull(database.canonicalCatalogDao().work(storyId.value, "RECONCILIATION_REEVALUATION"))
            assertEquals(1, database.canonicalCatalogDao().pendingOutbox(10).size)
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
    fun timestampOnlySummaryRefreshReportsNoSemanticFingerprintChange() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 10))

            val second = assertIs<Outcome.Success<app.openstory.catalog.repository.CatalogHomeCommitResult>>(
                repository.commitHomeRefresh(mutation("a", listOf("a-1"), 20)),
            ).value.changes.single()

            assertEquals(false, second.identityFingerprintChanged)
            assertEquals(false, second.fusionFingerprintChanged)
        }
    }

    @Test
    fun presentationOnlySummaryChangeReportsFusionWithoutIdentity() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val first = mutation("a", listOf("a-1"), 10)
            repository.commitHomeRefresh(first)
            val changedEntry = first.entries.single().copy(
                coverUrl = "cover-2",
                publicationStatus = PublicationStatus.ONGOING,
                latestUpdate = CatalogLatestUpdate(99L, "99"),
            )
            val changed = first.copy(
                refreshedAtEpochMillis = 20,
                entries = listOf(changedEntry),
                sections = listOf(first.sections.single().copy(items = listOf(changedEntry))),
            )

            val change = assertIs<Outcome.Success<app.openstory.catalog.repository.CatalogHomeCommitResult>>(
                repository.commitHomeRefresh(changed),
            ).value.changes.single()

            assertEquals(false, change.identityFingerprintChanged)
            assertEquals(true, change.fusionFingerprintChanged)
        }
    }

    @Test
    fun identityEvidenceChangeIsReportedIndependently() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val first = mutation("a", listOf("a-1"), 10)
            repository.commitHomeRefresh(first)
            val changedEntry = first.entries.single().copy(
                aliases = setOf("Alias"),
                authors = setOf("Author"),
                externalIdentifiers = setOf(ExternalIdentifier("work", "W-1", ExternalIdentifierScope.WORK)),
            )
            val changed = first.copy(
                refreshedAtEpochMillis = 20,
                entries = listOf(changedEntry),
                sections = listOf(first.sections.single().copy(items = listOf(changedEntry))),
            )

            val change = assertIs<Outcome.Success<app.openstory.catalog.repository.CatalogHomeCommitResult>>(
                repository.commitHomeRefresh(changed),
            ).value.changes.single()

            assertEquals(true, change.identityFingerprintChanged)
        }
    }

    @Test
    fun richerFullMetadataCanReportIdentityChangeForExistingOwner() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 10))
            val key = CatalogMetadataKey(PluginId("a"), "a-1")
            val summary = requireNotNull(repository.metadataSnapshot(key)).entry

            val result = assertIs<Outcome.Success<app.openstory.catalog.repository.CatalogDetailsCommitResult>>(
                repository.commitDetails(
                    CatalogDetailsMutation(
                        summary.storyId,
                        summary.copy(aliases = setOf("Richer Alias"), authors = setOf("Richer Author")),
                        "2.0.0",
                        20,
                    ),
                ),
            ).value

            assertEquals(summary.storyId, result.storyId)
            assertEquals(true, result.changes.single().identityFingerprintChanged)
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

            assertEquals(existingStoryId, assertIs<Outcome.Success<app.openstory.catalog.repository.CatalogDetailsCommitResult>>(result).value.storyId)
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

            assertEquals(homeStoryId, assertIs<Outcome.Success<app.openstory.catalog.repository.CatalogDetailsCommitResult>>(result).value.storyId)
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
    fun storyProjectionObservationOmitsPreparingAndScopesReadyStories() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            repository.commitHomeRefresh(mutation("a", listOf("a-1"), 1))
            repository.commitHomeRefresh(mutation("b", listOf("b-1"), 2))
            val projections = RoomCatalogStoryProjectionRepository(database)
            val storyId = StoryId("story:a-1")

            assertEquals(emptyList(), projections.observeForStories(setOf(storyId)).first())

            val canonical = RoomCanonicalCatalogRepository(database)
            assertEquals(true, canonical.persistCandidate(projectionGeneration(storyId), null))

            val observed = projections.observeForStories(setOf(storyId)).first()
            assertEquals(listOf(storyId), observed.map { it.storyId })
            assertEquals("Canonical A", observed.single().title)
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

            assertIs<Outcome.Success<app.openstory.catalog.repository.CatalogDetailsCommitResult>>(result)
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

    @Test
    fun searchSummaryCommitKeepsDurableOwnerAndFullProvenanceAndPersistsIdentifiers() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val plugin = PluginId("search")
            val key = SourceKey(plugin, "source-1")
            val durable = StoryId("story:durable")
            val fullEntry = CatalogEntry(
                durable, plugin, key.sourceId, "Full title", description = "Full description",
                contentType = ContentType.MANGA,
            )
            repository.commitDetails(CatalogDetailsMutation(durable, fullEntry, "full-1", 20L))

            val proposed = StoryId("story:proposed")
            val identifiers = setOf(ExternalIdentifier("work", "search-1", ExternalIdentifierScope.WORK))
            val summary = fullEntry.copy(
                storyId = proposed,
                title = "Search title",
                description = null,
                externalIdentifiers = identifiers,
            )
            val result = repository.commitSearchSummaries(
                CatalogSearchSummaryMutation(
                    plugin, "search-2", 30L,
                    stories = listOf(Story(proposed, ContentType.MANGA)),
                    entries = listOf(summary),
                ),
            )

            val committed = assertIs<Outcome.Success<CatalogSearchSummaryCommitResult>>(result).value
            assertEquals(durable, committed.sourceStoryIds.getValue(key))
            assertEquals(true, committed.changes.single().identityFingerprintChanged)
            assertEquals(true, committed.changes.single().fusionFingerprintChanged)
            val snapshot = requireNotNull(repository.metadataSnapshot(CatalogMetadataKey(plugin, key.sourceId)))
            assertEquals(durable, snapshot.entry.storyId)
            assertEquals("Search title", snapshot.entry.title)
            assertEquals("Full description", snapshot.entry.description)
            assertEquals(30L, snapshot.summary.resolvedAtEpochMillis)
            assertEquals(20L, snapshot.full?.resolvedAtEpochMillis)
            assertEquals(identifiers, snapshot.entry.externalIdentifiers)
            assertEquals(2, database.canonicalCatalogDao().pendingOutbox(10).size)
            assertEquals(emptyList(), repository.observeHomes().first())
        }
    }

    @Test
    fun newSearchSummaryCreatesCanonicalStateOwnershipAndTransactionalOutbox() = runTest {
        withDatabase { database ->
            val repository = RoomCatalogRepository(database)
            val storyId = StoryId("story:search-new")
            val plugin = PluginId("search")
            val entry = CatalogEntry(storyId, plugin, "new", "Search new", contentType = ContentType.MANGA)

            val result = repository.commitSearchSummaries(
                CatalogSearchSummaryMutation(
                    plugin, "1.0.0", 44L,
                    stories = listOf(Story(storyId, ContentType.MANGA)),
                    entries = listOf(entry),
                ),
            )

            val committed = assertIs<Outcome.Success<CatalogSearchSummaryCommitResult>>(result).value
            assertEquals(storyId, committed.sourceStoryIds.getValue(SourceKey(plugin, "new")))
            val state = requireNotNull(database.canonicalCatalogDao().canonicalState(storyId.value))
            assertEquals("AUTO", state.preferenceMode)
            assertEquals("REEVALUATING", state.health)
            assertEquals(44L, state.createdAtEpochMillis)
            assertNull(database.canonicalCatalogDao().work(storyId.value, "FUSION_REBUILD"))
            assertNull(database.canonicalCatalogDao().work(storyId.value, "RECONCILIATION_REEVALUATION"))
            assertEquals(1, database.canonicalCatalogDao().pendingOutbox(10).size)
            assertEquals(emptyList(), repository.observeHomes().first())
        }
    }

    private fun projectionGeneration(storyId: StoryId): CanonicalGeneration {
        val source = SourceKey(PluginId("a"), "a-1")
        return CanonicalGeneration(
            id = "gen:projection-a",
            storyId = storyId,
            fusionPolicyVersion = 1,
            primarySelectionPolicyVersion = 1,
            fusionFingerprint = "fusion:projection-a",
            effectivePrimary = source,
            metadata = CanonicalMetadata(
                title = "Canonical A",
                description = null,
                coverUrl = "https://example.test/canonical-a.jpg",
                sourceUrl = null,
                popularityRank = null,
                aliases = emptyList(),
                authors = emptyList(),
                genres = emptyList(),
                languageTags = emptyList(),
                publicationStatus = PublicationStatus.ONGOING,
                latestUpdate = null,
                score = null,
            ),
            health = CanonicalHealth.FRESH,
            provenance = mapOf(
                CanonicalFieldKey.TITLE to CanonicalFieldProvenance(
                    field = CanonicalFieldKey.TITLE,
                    strategy = CanonicalFieldStrategy.PRIMARY_WITH_FALLBACK,
                    contributors = listOf(
                        CanonicalFieldContributor(source, "source-fusion", CatalogMetadataLevel.Summary),
                    ),
                    reasonCodes = listOf("primary"),
                    policyVersion = 1,
                ),
            ),
            createdAtEpochMillis = 10L,
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
