package app.openstory.storage.room.merge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.reconciliation.CatalogReconciliationEngine
import app.openstory.catalog.reconciliation.CatalogReconciliationService
import app.openstory.catalog.reconciliation.InMemoryCatalogCandidateIndex
import app.openstory.catalog.reconciliation.ReconciliationAssessment
import app.openstory.catalog.reconciliation.ReconciliationExecutionMode
import app.openstory.catalog.reconciliation.ReconciliationPolicy
import app.openstory.catalog.reconciliation.ReconciliationRunResult
import app.openstory.catalog.reconciliation.ReconciliationCaseKey
import app.openstory.catalog.reconciliation.ReconciliationCaseStatus
import app.openstory.catalog.reconciliation.ReconciliationResolutionOrigin
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.catalog.reconciliation.ReconciliationReasonCode
import app.openstory.catalog.reconciliation.ProtectedMappingResolution
import app.openstory.catalog.reconciliation.ReconciliationReviewAction
import app.openstory.catalog.reconciliation.ReconciliationReviewCommand
import app.openstory.catalog.reconciliation.ReconciliationReviewResult
import app.openstory.catalog.reconciliation.ReconciliationReviewService
import app.openstory.catalog.reconciliation.ReconciliationSemanticDecision
import app.openstory.common.FakeClock
import app.openstory.common.id.PluginId
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryStatus
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.storage.room.catalog.RoomCanonicalCatalogRepository
import app.openstory.storage.room.catalog.RoomCatalogRepository
import app.openstory.storage.room.catalog.RoomCanonicalEngineWorkRepository
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.CanonicalEngineWorkEntity
import app.openstory.storage.room.catalog.CatalogEntryEntity
import app.openstory.storage.room.catalog.RoomStoryIdentityResolver
import app.openstory.storage.room.catalog.RoomReconciliationCaseRepository
import app.openstory.storage.room.catalog.StoryCanonicalStateEntity
import app.openstory.storage.room.catalog.StoryEntity
import app.openstory.storage.room.chapters.CanonicalChapterEntity
import app.openstory.storage.room.chapters.ChapterAggregationOverrideEntity
import app.openstory.storage.room.chapters.ChapterReleaseEntity
import app.openstory.storage.room.chapters.ChapterSyncStateEntity
import app.openstory.storage.room.downloads.ChapterStorageEntryEntity
import app.openstory.storage.room.library.ContentMappingEntity
import app.openstory.storage.room.library.LibraryEntity
import app.openstory.storage.room.library.RoomContentMappingRepository
import app.openstory.storage.room.library.RoomLibraryRepository
import app.openstory.storage.room.reader.ReadingProgressEntity
import app.openstory.storage.room.reader.RoomReadingProgressRepository
import app.openstory.storage.room.chapters.RoomChapterRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStoryGraphMergeCoordinatorTest {
    @Test
    fun fullGraphMergeIsAtomicPreservesStableIdsAndEnqueuesDerivedWork() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10, pinned = true)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            database.libraryDao().insert(LibraryEntity("story:a", "READING", 1, 5))
            database.libraryDao().insertMapping(
                ContentMappingEntity("story:a", "plugin:content", "mapped:a", "USER_APPROVED", 1, 5),
            )
            database.libraryDao().insertMapping(
                ContentMappingEntity("story:b", "plugin:content", "mapped:b", "AUTOMATED", 1, 6),
            )
            seedChapterGraph(database, "story:a", "chapter:a", "release:a", withOverride = false)
            seedChapterGraph(database, "story:b", "chapter:b", "release:b", withOverride = true)
            database.chapterSyncDao().upsert(
                ChapterSyncStateEntity("story:b", "plugin:chapter", "source:b", "FULL", "cursor", null, "fp", 10),
            )
            database.readingProgressDao().upsert(
                ReadingProgressEntity(
                    "story:b", "chapter:b", "release:b", "content:b", "block:b", 7, 0.5f, null, 10,
                ),
            )
            database.downloadDao().upsert(
                ChapterStorageEntryEntity(
                    "EXPLICIT_DOWNLOAD", "release:b", "content:b", "checksum", 12, 10,
                    pinned = true, current = true, downloadState = "COMPLETED", failureReason = null,
                    attempt = 0, updatedAtEpochMillis = 10,
                ),
            )
            val coordinator = coordinator(database, "merge:full")

            val result = assertIs<StoryMergeResult.Merged>(coordinator.execute(request("story:a", "story:b")))

            assertEquals(StoryId("story:a"), result.survivorStoryId)
            assertNull(database.catalogDao().findStory("story:b"))
            assertEquals(
                listOf("story:a", "story:a"),
                database.catalogDao().entries().map { it.storyId }.sorted(),
            )
            assertEquals("story:a", database.chapterDao().releases("story:a").first { it.chapterReleaseId == "release:b" }.storyId)
            assertNotNull(database.chapterDao().groups("story:a").firstOrNull { it.chapter.canonicalChapterId == "chapter:b" })
            assertEquals("release:b", database.downloadDao().findDownload("release:b")?.chapterReleaseId)
            assertEquals("story:a", database.readingProgressDao().progressForStory("story:a").single().storyId)
            assertEquals("story:a", database.canonicalCatalogDao().redirect("story:b")?.canonicalStoryId)
            val event = requireNotNull(database.canonicalCatalogDao().mergeEvent("merge:full"))
            assertEquals("story:b", event.retiredStoryId)
            assertTrue(event.reversalPayload.contains("story:b"))
            assertTrue(event.reversalPayload.contains("source:b"))
            assertTrue(event.reversalPayload.contains("MANGA"))
            assertEquals(1L, database.canonicalCatalogDao().canonicalState("story:a")?.identityRevision)
            assertEquals(1, database.canonicalCatalogDao().workForStory("story:a").count { it.workType == "FUSION_REBUILD" })
            assertEquals(1, database.canonicalCatalogDao().workForStory("story:a").count { it.workType == "POST_MERGE_DERIVED" })
            assertTrue(foreignKeyViolations(database).isEmpty())
        }
    }

    @Test
    fun reconciliationApplyModeUsesRoomCoordinatorAndProtectedConflictStaysPending() = runTest {
        withDatabase { database ->
            seedStory(
                database,
                "story:a",
                "source:a",
                createdAt = 1,
                title = "Shared Title",
                authors = setOf("Shared Author"),
            )
            seedStory(
                database,
                "story:b",
                "source:b",
                createdAt = 2,
                title = "Shared Title",
                authors = setOf("Shared Author"),
            )
            val service = autoMergeService(database)

            val result = service.reconcile(SourceKey(PluginId("plugin:catalog"), "source:a"))

            assertEquals(ReconciliationRunResult.AutoMergeApplied(StoryId("story:a")), result)
            assertNull(database.catalogDao().findStory("story:b"))
            assertEquals("story:a", database.canonicalCatalogDao().redirect("story:b")?.canonicalStoryId)
        }

        withDatabase { database ->
            seedStory(
                database,
                "story:a",
                "source:a",
                createdAt = 1,
                title = "Shared Title",
                authors = setOf("Shared Author"),
            )
            seedStory(
                database,
                "story:b",
                "source:b",
                createdAt = 2,
                title = "Shared Title",
                authors = setOf("Shared Author"),
            )
            database.libraryDao().insertMapping(
                ContentMappingEntity("story:a", "plugin:protected", "target:a", "USER_APPROVED", 1, 5),
            )
            database.libraryDao().insertMapping(
                ContentMappingEntity("story:b", "plugin:protected", "target:b", "USER_URL", 1, 6),
            )
            val service = autoMergeService(database)

            val result = service.reconcile(SourceKey(PluginId("plugin:catalog"), "source:a"))

            assertIs<ReconciliationRunResult.ReviewRecorded>(result)
            assertNotNull(database.catalogDao().findStory("story:a"))
            assertNotNull(database.catalogDao().findStory("story:b"))
            assertNull(database.canonicalCatalogDao().redirect("story:b"))
        }
    }

    @Test
    fun mergeWithoutDerivedConflictsDoesNotEnqueuePostMergeDerivedWork() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)

            assertIs<StoryMergeResult.Merged>(coordinator(database, "merge:no-derived").execute(request("story:a", "story:b")))

            assertEquals(
                0,
                database.canonicalCatalogDao().workForStory("story:a")
                    .count { it.workType == "POST_MERGE_DERIVED" },
            )
        }
    }

    @Test
    fun retiredStoryIdRemainsValidAcrossStoryRepositoryBoundaries() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10, pinned = true)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            database.libraryDao().insert(LibraryEntity("story:a", "READING", 1, 1))
            database.libraryDao().insertMapping(
                ContentMappingEntity("story:a", "plugin:protected", "mapped:a", "USER_APPROVED", 1, 1),
            )
            seedChapterGraph(database, "story:b", "chapter:b", "release:b", withOverride = false)
            database.readingProgressDao().upsert(
                ReadingProgressEntity(
                    "story:b", "chapter:b", "release:b", "content:b", "block:b", 0, 0.25f, null, 10,
                ),
            )
            assertIs<StoryMergeResult.Merged>(coordinator(database, "merge:old-id").execute(request("story:a", "story:b")))
            val retired = StoryId("story:b")

            val catalog = RoomCatalogRepository(database)
            assertTrue(catalog.sourceRecords(retired).all { it.storyId == StoryId("story:a") })
            assertEquals(StoryId("story:a"), catalog.observeStory(retired).first()?.story?.id)

            val canonical = RoomCanonicalCatalogRepository(database)
            assertEquals(StoryId("story:a"), canonical.state(retired)?.story?.id)
            assertTrue(canonical.sourceRecords(retired).all { it.storyId == StoryId("story:a") })

            val library = RoomLibraryRepository(database)
            assertEquals(
                StoryId("story:a"),
                library.changeStatus(retired, LibraryStatus.COMPLETED, 30)?.storyId,
            )

            val mappings = RoomContentMappingRepository(database)
            val write = mappings.compareAndWrite(
                ContentMapping(
                    storyId = retired,
                    pluginId = PluginId("plugin:new"),
                    sourceStoryId = "mapped:new",
                    origin = ContentMappingOrigin.AUTOMATED,
                    policyVersion = 1,
                    updatedAt = 30,
                ),
                replaceableOrigins = setOf(ContentMappingOrigin.AUTOMATED),
            )
            assertEquals(StoryId("story:a"), write.mapping.storyId)
            assertTrue(mappings.observe(retired).first().all { it.storyId == StoryId("story:a") })

            val chapters = RoomChapterRepository(database)
            assertTrue(chapters.snapshot(retired).chapters.all { it.storyId == StoryId("story:a") })
            assertTrue(chapters.observe(retired).first().all { it.chapter.storyId == StoryId("story:a") })

            val progress = RoomReadingProgressRepository(database)
            assertEquals(
                StoryId("story:a"),
                progress.find(retired, CanonicalChapterId("chapter:b"))?.storyId,
            )
            assertEquals(
                StoryId("story:a"),
                progress.observe(retired, CanonicalChapterId("chapter:b")).first()?.storyId,
            )
        }
    }

    @Test
    fun injectedFailureNearAuditRollsBackEveryAuthoritativeWrite() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            val coordinator = RoomStoryGraphMergeCoordinator(
                database = database,
                clock = FakeClock(100),
                mergeEventIdFactory = { "merge:rollback" },
                beforeAudit = { error("injected") },
            )

            assertFailsWith<IllegalStateException> {
                coordinator.execute(request("story:a", "story:b"))
            }

            assertNotNull(database.catalogDao().findStory("story:a"))
            assertNotNull(database.catalogDao().findStory("story:b"))
            assertEquals("story:b", database.catalogDao().findEntry("plugin:catalog", "source:b")?.storyId)
            assertNull(database.canonicalCatalogDao().redirect("story:b"))
            assertNull(database.canonicalCatalogDao().mergeEvent("merge:rollback"))
            assertTrue(foreignKeyViolations(database).isEmpty())
        }
    }

    @Test
    fun stalePreparedPlanCannotOverwriteLaterLibraryWrite() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            val identity = RoomStoryIdentityResolver(database)
            val readers = RoomStoryMergeReaders(database)
            val planner = RoomStoryGraphMergePlanner(identity, readers)
            val writer = RoomStoryMergeWriter(database, identity, readers, FakeClock(100), { "merge:stale" })
            val prepared = assertIs<StoryGraphMergePreparation.Ready>(
                planner.prepare(request("story:a", "story:b")),
            ).plan
            database.libraryDao().insert(LibraryEntity("story:b", "READING", 50, 50))

            assertIs<StoryMergeResult.StalePlan>(writer.commit(prepared))
            assertNotNull(database.catalogDao().findStory("story:b"))
            assertNull(database.canonicalCatalogDao().mergeEvent("merge:stale"))
        }
    }

    @Test
    fun mergePreservesAuthorizingCaseHistoryAndRekeysPendingCasesAndWork() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            seedStory(database, "story:x", "source:x", createdAt = 3)
            val cases = RoomReconciliationCaseRepository(database)
            val mergeCase = requireNotNull(
                cases.recordAssessment(
                    ReconciliationCaseKey.of(StoryId("story:a"), StoryId("story:b")),
                    assessment(
                        decision = ReconciliationSemanticDecision.SAME_WORK,
                        fingerprint = "merge-evidence",
                    ),
                    evaluatedAtEpochMillis = 10,
                ),
            )
            val survivorRelation = requireNotNull(
                cases.recordAssessment(
                    ReconciliationCaseKey.of(StoryId("story:a"), StoryId("story:x")),
                    assessment(ReconciliationSemanticDecision.REVIEW, "relation:a:x"),
                    evaluatedAtEpochMillis = 11,
                ),
            )
            cases.recordAssessment(
                ReconciliationCaseKey.of(StoryId("story:b"), StoryId("story:x")),
                assessment(ReconciliationSemanticDecision.REVIEW, "relation:b:x"),
                evaluatedAtEpochMillis = 12,
            )
            database.canonicalCatalogDao().upsertWork(
                CanonicalEngineWorkEntity(
                    "story:b",
                    "RECONCILIATION_REEVALUATION",
                    "retired-work",
                    2,
                    50,
                    "old-error",
                    1,
                ),
            )

            val result = coordinator(database, "merge:case-work").execute(
                request(
                    left = "story:a",
                    right = "story:b",
                    caseId = mergeCase.id,
                    evidenceFingerprint = mergeCase.evidenceFingerprint,
                ),
            )
            assertIs<StoryMergeResult.Merged>(result)

            val dao = database.canonicalCatalogDao()
            val historicalMergeCase = requireNotNull(dao.reconciliationCase(mergeCase.id))
            assertEquals("RESOLVED_MERGED", historicalMergeCase.status)
            assertEquals("story:a", historicalMergeCase.leftStoryId)
            assertEquals("story:b", historicalMergeCase.rightStoryId)
            assertTrue(
                dao.reconciliationRevisions(mergeCase.id)
                    .all { it.leftStoryId == "story:a" && it.rightStoryId == "story:b" },
            )

            val normalized = requireNotNull(dao.reconciliationCase("story:a", "story:x"))
            assertEquals(survivorRelation.id, normalized.caseId)
            assertEquals("PENDING", normalized.status)
            assertTrue(
                dao.reconciliationRevisions(normalized.caseId)
                    .any { it.leftStoryId == "story:b" && it.rightStoryId == "story:x" },
            )
            assertEquals(1, dao.reconciliationCasesForStory("story:x").count { it.status == "PENDING" })

            assertEquals(emptyList(), dao.workForStory("story:b"))
            assertEquals(1, dao.workForStory("story:a").count { it.workType == "FUSION_REBUILD" })
            assertEquals(1, dao.workForStory("story:a").count { it.workType == "RECONCILIATION_REEVALUATION" })
        }
    }

    @Test
    fun rekeyingPendingCaseCannotReopenResolvedRelation() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            seedStory(database, "story:x", "source:x", createdAt = 3)
            val cases = RoomReconciliationCaseRepository(database)
            val mergeCase = requireNotNull(
                cases.recordAssessment(
                    ReconciliationCaseKey.of(StoryId("story:a"), StoryId("story:b")),
                    assessment(ReconciliationSemanticDecision.SAME_WORK, "merge-evidence"),
                    evaluatedAtEpochMillis = 10,
                ),
            )
            val resolvedTarget = requireNotNull(
                cases.recordAssessment(
                    ReconciliationCaseKey.of(StoryId("story:a"), StoryId("story:x")),
                    assessment(ReconciliationSemanticDecision.REVIEW, "resolved:a:x"),
                    evaluatedAtEpochMillis = 11,
                ),
            )
            assertTrue(
                cases.resolveSeparate(
                    caseId = resolvedTarget.id,
                    expectedRevision = resolvedTarget.revision,
                    origin = ReconciliationResolutionOrigin.USER,
                    resolvedAtEpochMillis = 12,
                ),
            )
            cases.recordAssessment(
                ReconciliationCaseKey.of(StoryId("story:b"), StoryId("story:x")),
                assessment(ReconciliationSemanticDecision.REVIEW, "pending:b:x"),
                evaluatedAtEpochMillis = 20,
            )

            assertIs<StoryMergeResult.Merged>(
                coordinator(database, "merge:resolved-target").execute(
                    request(
                        left = "story:a",
                        right = "story:b",
                        caseId = mergeCase.id,
                        evidenceFingerprint = mergeCase.evidenceFingerprint,
                    ),
                ),
            )

            val normalized = requireNotNull(database.canonicalCatalogDao().reconciliationCase("story:a", "story:x"))
            assertEquals(resolvedTarget.id, normalized.caseId)
            assertEquals(ReconciliationCaseStatus.RESOLVED_SEPARATE.name, normalized.status)
            assertEquals(0, database.canonicalCatalogDao().reconciliationCasesForStory("story:x").count {
                it.status == ReconciliationCaseStatus.PENDING.name
            })
        }
    }


    @Test
    fun userReviewServiceResolvesProtectedMappingConflictAtomicallyAndRepeatMergeIsIdempotent() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            val pluginId = PluginId("plugin:content")
            database.libraryDao().insertMapping(
                ContentMappingEntity("story:a", pluginId.value, "mapped:a", "USER_APPROVED", 1, 5),
            )
            database.libraryDao().insertMapping(
                ContentMappingEntity("story:b", pluginId.value, "mapped:b", "USER_APPROVED", 1, 6),
            )
            val cases = RoomReconciliationCaseRepository(database)
            val reviewCase = requireNotNull(
                cases.recordAssessment(
                    ReconciliationCaseKey.of(StoryId("story:a"), StoryId("story:b")),
                    assessment(ReconciliationSemanticDecision.REVIEW, "review-evidence"),
                    evaluatedAtEpochMillis = 10,
                ),
            )
            val clock = FakeClock(100)
            val service = ReconciliationReviewService(
                cases = cases,
                mergeExecutor = RoomStoryGraphMergeCoordinator(
                    database = database,
                    clock = clock,
                    mergeEventIdFactory = { "merge:user-review" },
                ),
                clock = clock,
            )
            val unresolved = ReconciliationReviewCommand(
                caseId = reviewCase.id,
                expectedCaseRevision = reviewCase.revision,
                action = ReconciliationReviewAction.MERGE,
            )

            val conflict = assertIs<ReconciliationReviewResult.ConflictResolutionRequired>(service.resolve(unresolved))
            assertEquals(pluginId, conflict.conflicts.single().pluginId)
            assertEquals(setOf("mapped:a", "mapped:b"), conflict.conflicts.single().candidateSourceStoryIds)
            assertEquals(ReconciliationCaseStatus.PENDING, requireNotNull(cases.find(reviewCase.id)).status)
            assertNull(database.canonicalCatalogDao().mergeEvent("merge:user-review"))

            val resolvedCommand = unresolved.copy(
                protectedMappingResolutions = listOf(ProtectedMappingResolution(pluginId, "mapped:a")),
            )
            val merged = assertIs<ReconciliationReviewResult.Merged>(service.resolve(resolvedCommand))
            assertEquals(ReconciliationCaseStatus.RESOLVED_MERGED, requireNotNull(cases.find(reviewCase.id)).status)
            val event = requireNotNull(database.canonicalCatalogDao().mergeEvent("merge:user-review"))
            assertEquals(StoryMergeOrigin.USER_REVIEW_APPROVAL.name, event.origin)
            assertEquals(reviewCase.id, event.reconciliationCaseId)
            assertEquals(1, database.canonicalCatalogDao().mergeEventsForStory(merged.survivorStoryId.value).size)

            assertEquals(merged, service.resolve(resolvedCommand))
            assertEquals(1, database.canonicalCatalogDao().mergeEventsForStory(merged.survivorStoryId.value).size)
        }
    }

    @Test
    fun redirectChainsAreFlattenedAndRepeatExecutionIsIdempotent() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            val first = coordinator(database, "merge:1")
            assertIs<StoryMergeResult.Merged>(first.execute(request("story:a", "story:b")))
            assertIs<StoryMergeResult.AlreadyMerged>(first.execute(request("story:b", "story:a")))

            seedStory(database, "story:c", "source:c", createdAt = 3)
            database.libraryDao().insert(LibraryEntity("story:c", "READING", 1, 1))
            val second = coordinator(database, "merge:2")
            val result = assertIs<StoryMergeResult.Merged>(second.execute(request("story:a", "story:c")))
            assertEquals(StoryId("story:c"), result.survivorStoryId)

            val dao = database.canonicalCatalogDao()
            assertEquals("story:c", dao.redirect("story:a")?.canonicalStoryId)
            assertEquals("story:c", dao.redirect("story:b")?.canonicalStoryId)
            assertNotNull(dao.mergeEvent("merge:1"))
            assertNotNull(dao.mergeEvent("merge:2"))
            assertEquals(StoryId("story:c"), RoomStoryIdentityResolver(database).resolve(StoryId("story:b")))
        }
    }

    private fun autoMergeService(database: OpenStoryDatabase) = CatalogReconciliationService(
        catalog = RoomCatalogRepository(database),
        identity = RoomStoryIdentityResolver(database),
        candidateIndex = InMemoryCatalogCandidateIndex(),
        engine = CatalogReconciliationEngine(ReconciliationPolicy()),
        cases = RoomReconciliationCaseRepository(database),
        clock = FakeClock(100),
        executionMode = ReconciliationExecutionMode.APPLY_ELIGIBLE_AUTO_MERGES,
        mergeExecutor = RoomStoryGraphMergeCoordinator(database, FakeClock(100)),
        work = RoomCanonicalEngineWorkRepository(database),
    )

    private fun coordinator(database: OpenStoryDatabase, mergeEventId: String) = RoomStoryGraphMergeCoordinator(
        database = database,
        clock = FakeClock(100),
        mergeEventIdFactory = { mergeEventId },
    )

    private fun request(
        left: String,
        right: String,
        caseId: String? = null,
        evidenceFingerprint: String = "evidence:$left:$right",
    ) = StoryMergeRequest(
        requestId = "request:$left:$right",
        leftStoryId = StoryId(left),
        rightStoryId = StoryId(right),
        origin = StoryMergeOrigin.AUTO_RECONCILIATION,
        reconciliationCaseId = caseId,
        evidenceFingerprint = evidenceFingerprint,
        reconciliationPolicyVersion = 1,
    )

    private fun assessment(
        decision: ReconciliationSemanticDecision,
        fingerprint: String,
    ) = ReconciliationAssessment(
        policyVersion = 1,
        semanticDecision = decision,
        mergeEligibility = ReconciliationMergeEligibility.MERGEABLE,
        confidence = 1.0,
        titleSimilarity = 1.0,
        authorSimilarity = 1.0,
        winningLead = 1.0,
        matchedIdentifiers = emptySet(),
        conflictingIdentifiers = emptySet(),
        reasons = setOf(ReconciliationReasonCode.TITLE_EXACT),
        identityEvidenceFingerprint = fingerprint,
    )

    private suspend fun seedStory(
        database: OpenStoryDatabase,
        storyId: String,
        sourceId: String,
        createdAt: Long,
        pinned: Boolean = false,
        title: String = storyId,
        authors: Set<String> = emptySet(),
    ) {
        database.catalogDao().upsertStories(listOf(StoryEntity(storyId, "MANGA")))
        database.canonicalCatalogDao().upsertCanonicalState(
            StoryCanonicalStateEntity(
                storyId,
                null,
                "REEVALUATING",
                if (pinned) "PINNED" else "AUTO",
                if (pinned) "plugin:catalog" else null,
                if (pinned) sourceId else null,
                0,
                0,
                createdAt,
            ),
        )
        database.catalogDao().upsertEntries(listOf(catalogEntry(storyId, sourceId, createdAt, title, authors)))
        database.canonicalCatalogDao().upsertWork(
            CanonicalEngineWorkEntity(storyId, "FUSION_REBUILD", "seed", 0, 0, null, 1),
        )
    }

    private fun catalogEntry(
        storyId: String,
        sourceId: String,
        time: Long,
        title: String,
        authors: Set<String>,
    ) = CatalogEntryEntity(
        pluginId = "plugin:catalog",
        sourceId = sourceId,
        storyId = storyId,
        title = title,
        aliases = emptySet(),
        authors = authors,
        description = null,
        genres = emptySet(),
        contentType = "MANGA",
        languageTags = emptySet(),
        coverUrl = null,
        sourceUrl = null,
        scoreValue = null,
        scoreScale = null,
        popularityRank = null,
        publicationStatus = null,
        latestUpdateAtEpochMillis = null,
        latestUpdateReleaseLabel = null,
        pluginVersion = "1",
        fetchedAtEpochMillis = time,
        fullPluginVersion = null,
        fullResolvedAtEpochMillis = null,
    )

    private suspend fun seedChapterGraph(
        database: OpenStoryDatabase,
        storyId: String,
        chapterId: String,
        releaseId: String,
        withOverride: Boolean,
    ) {
        database.chapterDao().upsertChapters(
            listOf(CanonicalChapterEntity(chapterId, storyId, "NUMBERED", null, "1", null, null, "Chapter 1", false)),
        )
        database.chapterDao().upsertReleases(
            listOf(
                ChapterReleaseEntity(
                    releaseId,
                    storyId,
                    "plugin:chapter",
                    "source:$storyId",
                    "source:$releaseId",
                    "Chapter 1",
                    "NUMBERED",
                    null,
                    "1",
                    null,
                    null,
                    "en",
                    1,
                    chapterId,
                ),
            ),
        )
        if (withOverride) {
            database.chapterSyncDao().upsertOverride(
                ChapterAggregationOverrideEntity(storyId, releaseId, chapterId, "FORCE_LINK"),
            )
        }
    }

    private fun foreignKeyViolations(database: OpenStoryDatabase): List<String> = buildList {
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
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
