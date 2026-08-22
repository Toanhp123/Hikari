package app.openstory.storage.room.merge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.catalog.identity.StoryMergeReverseRequest
import app.openstory.catalog.identity.StoryMergeReverseResult
import app.openstory.catalog.identity.StoryMergeReversalAssessmentResult
import app.openstory.catalog.identity.StoryMergeReversibility
import app.openstory.catalog.reconciliation.ReconciliationAssessment
import app.openstory.catalog.reconciliation.ReconciliationCaseKey
import app.openstory.catalog.reconciliation.ReconciliationCaseStatus
import app.openstory.catalog.reconciliation.ReconciliationMergeEligibility
import app.openstory.catalog.reconciliation.ReconciliationReasonCode
import app.openstory.catalog.reconciliation.ReconciliationResolutionOrigin
import app.openstory.catalog.reconciliation.ReconciliationSemanticDecision
import app.openstory.common.FakeClock
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.CanonicalEngineWorkEntity
import app.openstory.storage.room.catalog.CatalogEntryEntity
import app.openstory.storage.room.catalog.CatalogEntryIdentifierEntity
import app.openstory.storage.room.catalog.RoomReconciliationCaseRepository
import app.openstory.storage.room.catalog.RoomStoryIdentityResolver
import app.openstory.storage.room.catalog.StoryCanonicalStateEntity
import app.openstory.storage.room.catalog.StoryEntity
import app.openstory.storage.room.chapters.CanonicalChapterEntity
import app.openstory.storage.room.chapters.ChapterReleaseEntity
import app.openstory.storage.room.library.ContentMappingEntity
import app.openstory.storage.room.library.LibraryEntity
import app.openstory.storage.room.reader.ReadingProgressEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class RoomStoryMergeReversalCoordinatorTest {
    @Test
    fun simpleLosslessMergeCanBeReversedAtomicallyAndRepeatedRequestIsIdempotent() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            val merge = assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:lossless").execute(mergeRequest("story:a", "story:b")),
            )
            val expectedRevision = requireNotNull(
                RoomStoryIdentityResolver(database).identityState(merge.survivorStoryId),
            ).identityRevision
            val request = StoryMergeReverseRequest("merge:lossless", expectedRevision)
            val reversal = reversalCoordinator(database, "reverse:lossless")

            val assessment = assertIs<StoryMergeReversalAssessmentResult.Assessed>(reversal.assess(request))
            assertEquals(StoryMergeReversibility.REVERSIBLE, assessment.assessment.reversibility)

            val first = assertIs<StoryMergeReverseResult.Reversed>(reversal.reverse(request))
            assertEquals(StoryId("story:a"), first.survivingStoryId)
            assertEquals(StoryId("story:b"), first.restoredStoryId)
            assertEquals("reverse:lossless", first.reversalEventId)
            assertNotNull(database.catalogDao().findStory("story:b"))
            assertEquals("story:b", database.catalogDao().findEntry("plugin:catalog", "source:b")?.storyId)
            assertNull(database.canonicalCatalogDao().redirect("story:b"))
            assertEquals("reverse:lossless", database.canonicalCatalogDao().mergeReversalEvent("merge:lossless")?.reversalEventId)
            assertEquals(null, database.canonicalCatalogDao().canonicalState("story:a")?.activeGenerationId)
            assertEquals(null, database.canonicalCatalogDao().canonicalState("story:b")?.activeGenerationId)
            assertEquals("REEVALUATING", database.canonicalCatalogDao().canonicalState("story:a")?.health)
            assertEquals("REEVALUATING", database.canonicalCatalogDao().canonicalState("story:b")?.health)
            assertEquals(
                setOf("FUSION_REBUILD", "RECONCILIATION_REEVALUATION"),
                database.canonicalCatalogDao().workForStory("story:a").mapTo(linkedSetOf()) { it.workType },
            )
            assertEquals(
                setOf("FUSION_REBUILD", "RECONCILIATION_REEVALUATION"),
                database.canonicalCatalogDao().workForStory("story:b").mapTo(linkedSetOf()) { it.workType },
            )
            assertTrue(foreignKeyViolations(database).isEmpty())

            assertEquals(first, reversal.reverse(request))
            assertEquals(1, database.canonicalCatalogDao().mergeReversalEventsForMerge("merge:lossless").size)
        }
    }

    @Test
    fun identityEvidenceCanChangeAfterMergeWithoutBlockingLosslessDomainSplit() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:identity-evidence").execute(mergeRequest("story:a", "story:b")),
            )
            database.catalogDao().insertIdentifiers(
                listOf(
                    CatalogEntryIdentifierEntity(
                        pluginId = "plugin:catalog",
                        sourceId = "source:b",
                        namespace = "work-id",
                        value = "work:b:corrected",
                        scope = "WORK",
                    ),
                ),
            )
            val mergedState = requireNotNull(database.canonicalCatalogDao().canonicalState("story:a"))
            database.canonicalCatalogDao().upsertCanonicalState(
                mergedState.copy(identityRevision = Math.addExact(mergedState.identityRevision, 1L)),
            )
            val expectedRevision = requireNotNull(
                RoomStoryIdentityResolver(database).identityState(StoryId("story:a")),
            ).identityRevision

            val result = reversalCoordinator(database, "reverse:identity-evidence").reverse(
                StoryMergeReverseRequest("merge:identity-evidence", expectedRevision),
            )

            assertIs<StoryMergeReverseResult.Reversed>(result)
            assertEquals("story:b", database.catalogDao().findEntry("plugin:catalog", "source:b")?.storyId)
            assertNull(database.canonicalCatalogDao().redirect("story:b"))
        }
    }

    @Test
    fun reversalRestoresHistoricalLibraryChapterAndProgressOwnership() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            database.libraryDao().insert(LibraryEntity("story:a", "WANT_TO_READ", 4, 4))
            database.libraryDao().insert(LibraryEntity("story:b", "READING", 5, 6))
            val storyAState = requireNotNull(database.canonicalCatalogDao().canonicalState("story:a"))
            database.canonicalCatalogDao().upsertCanonicalState(
                storyAState.copy(
                    preferenceMode = "PINNED",
                    pinnedPluginId = "plugin:catalog",
                    pinnedSourceId = "source:a",
                    preferenceRevision = Math.addExact(storyAState.preferenceRevision, 1L),
                ),
            )
            seedChapter(database, "story:b", "chapter:b", "release:b")
            database.readingProgressDao().upsert(
                ReadingProgressEntity(
                    "story:b", "chapter:b", "release:b", "content:b", "block:b", 3, 0.5f, null, 7,
                ),
            )
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:domain").execute(mergeRequest("story:a", "story:b")),
            )
            val expectedRevision = requireNotNull(
                RoomStoryIdentityResolver(database).identityState(StoryId("story:a")),
            ).identityRevision

            val result = assertIs<StoryMergeReverseResult.Reversed>(
                reversalCoordinator(database, "reverse:domain").reverse(
                    StoryMergeReverseRequest("merge:domain", expectedRevision),
                ),
            )

            assertEquals(StoryId("story:b"), result.restoredStoryId)
            assertEquals("WANT_TO_READ", database.libraryDao().find("story:a")?.status)
            assertEquals("READING", database.libraryDao().find("story:b")?.status)
            assertEquals("story:b", database.chapterDao().findRelease("release:b")?.storyId)
            assertEquals("story:b", database.readingProgressDao().find("story:b", "chapter:b")?.storyId)
            assertTrue(foreignKeyViolations(database).isEmpty())
        }
    }

    @Test
    fun postMergeSourcePreferenceChangeRequiresReviewAndWritesNothing() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:preference").execute(mergeRequest("story:a", "story:b")),
            )
            val dao = database.canonicalCatalogDao()
            val state = requireNotNull(dao.canonicalState("story:a"))
            dao.upsertCanonicalState(
                state.copy(
                    preferenceMode = "PINNED",
                    pinnedPluginId = "plugin:catalog",
                    pinnedSourceId = "source:a",
                    preferenceRevision = Math.addExact(state.preferenceRevision, 1L),
                ),
            )

            val result = reversalCoordinator(database, "reverse:preference").reverse(
                StoryMergeReverseRequest("merge:preference", state.identityRevision),
            )

            val review = assertIs<StoryMergeReverseResult.ReviewRequired>(result)
            assertTrue(review.reasons.any { it.contains("canonical_state_changed") })
            assertNull(database.catalogDao().findStory("story:b"))
            assertEquals("PINNED", dao.canonicalState("story:a")?.preferenceMode)
            assertNull(dao.mergeReversalEvent("merge:preference"))
        }
    }

    @Test
    fun postMergeLibraryEditAfterCoalescingRequiresReviewAndWritesNothing() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            database.libraryDao().insert(LibraryEntity("story:a", "WANT_TO_READ", 4, 4))
            database.libraryDao().insert(LibraryEntity("story:b", "READING", 5, 6))
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:library-edit").execute(mergeRequest("story:a", "story:b")),
            )
            check(database.libraryDao().updateStatus("story:a", "COMPLETED", 50) == 1)
            val expectedRevision = requireNotNull(
                RoomStoryIdentityResolver(database).identityState(StoryId("story:a")),
            ).identityRevision

            val result = reversalCoordinator(database, "reverse:library-edit").reverse(
                StoryMergeReverseRequest("merge:library-edit", expectedRevision),
            )

            val review = assertIs<StoryMergeReverseResult.ReviewRequired>(result)
            assertTrue(review.reasons.any { it.contains("library") })
            assertNull(database.catalogDao().findStory("story:b"))
            assertEquals("COMPLETED", database.libraryDao().find("story:a")?.status)
            assertNull(database.canonicalCatalogDao().mergeReversalEvent("merge:library-edit"))
        }
    }

    @Test
    fun postMergeProtectedMappingMutationRequiresReviewAndWritesNothing() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:mapping").execute(mergeRequest("story:a", "story:b")),
            )
            database.libraryDao().insertMapping(
                ContentMappingEntity("story:a", "plugin:mapping", "mapped:new", "USER_APPROVED", 1, 50),
            )
            val expectedRevision = requireNotNull(
                RoomStoryIdentityResolver(database).identityState(StoryId("story:a")),
            ).identityRevision
            val coordinator = reversalCoordinator(database, "reverse:mapping")
            val request = StoryMergeReverseRequest("merge:mapping", expectedRevision)

            val assessment = assertIs<StoryMergeReversalAssessmentResult.Assessed>(coordinator.assess(request))
            assertEquals(StoryMergeReversibility.REQUIRES_REVIEW_TO_REVERSE, assessment.assessment.reversibility)
            assertTrue(assessment.assessment.reasonCodes.any { it.contains("mapping") || it.contains("graph_changed") })
            assertIs<StoryMergeReverseResult.ReviewRequired>(coordinator.reverse(request))
            assertNull(database.catalogDao().findStory("story:b"))
            assertEquals("story:a", database.canonicalCatalogDao().redirect("story:b")?.canonicalStoryId)
            assertNull(database.canonicalCatalogDao().mergeReversalEvent("merge:mapping"))
        }
    }

    @Test
    fun extraSourceChapterOrProgressMutationAfterMergeBlocksBlindReversal() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            seedChapter(database, "story:a", "chapter:a", "release:a")
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:mutated").execute(mergeRequest("story:a", "story:b")),
            )
            database.catalogDao().upsertEntries(listOf(catalogEntry("story:a", "source:new", 30)))
            database.chapterDao().upsertChapters(
                listOf(CanonicalChapterEntity("chapter:new", "story:a", "NUMBERED", null, "2", null, null, "Chapter 2", false)),
            )
            database.chapterDao().upsertReleases(
                listOf(
                    ChapterReleaseEntity(
                        "release:new", "story:a", "plugin:chapter", "source:story:a", "source:release:new",
                        "Chapter 2", "NUMBERED", null, "2", null, null, "en", 30, "chapter:new",
                    ),
                ),
            )
            database.readingProgressDao().upsert(
                ReadingProgressEntity(
                    "story:a", "chapter:new", "release:new", "content:new", "block:new", 0, 0.1f, null, 30,
                ),
            )
            val expectedRevision = requireNotNull(
                RoomStoryIdentityResolver(database).identityState(StoryId("story:a")),
            ).identityRevision

            val result = reversalCoordinator(database, "reverse:mutated").reverse(
                StoryMergeReverseRequest("merge:mutated", expectedRevision),
            )
            val review = assertIs<StoryMergeReverseResult.ReviewRequired>(result)
            assertTrue(review.reasons.isNotEmpty())
            assertNull(database.catalogDao().findStory("story:b"))
            assertNull(database.canonicalCatalogDao().mergeReversalEvent("merge:mutated"))
        }
    }

    @Test
    fun crossSideChapterReassociationRequiresReviewAndWritesNothing() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            seedChapter(database, "story:a", "chapter:a", "release:a")
            seedChapter(database, "story:b", "chapter:b", "release:b")
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:chapter-partition").execute(mergeRequest("story:a", "story:b")),
            )
            assertEquals(1, database.chapterDao().link("release:b", "chapter:a"))
            val state = requireNotNull(database.canonicalCatalogDao().canonicalState("story:a"))

            val result = reversalCoordinator(database, "reverse:chapter-partition").reverse(
                StoryMergeReverseRequest("merge:chapter-partition", state.identityRevision),
            )

            val review = assertIs<StoryMergeReverseResult.ReviewRequired>(result)
            assertTrue(review.reasons.any { it.contains("chapter.reversal_state_changed") })
            assertNull(database.catalogDao().findStory("story:b"))
            assertEquals("chapter:a", database.chapterDao().findRelease("release:b")?.canonicalChapterId)
            assertNull(database.canonicalCatalogDao().mergeReversalEvent("merge:chapter-partition"))
        }
    }

    @Test
    fun staleIdentityRevisionCannotReverseHistoricalMerge() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:stale").execute(mergeRequest("story:a", "story:b")),
            )

            assertIs<StoryMergeReverseResult.StalePlan>(
                reversalCoordinator(database, "reverse:stale").reverse(
                    StoryMergeReverseRequest("merge:stale", expectedSurvivorIdentityRevision = 0),
                ),
            )
            assertNull(database.catalogDao().findStory("story:b"))
            assertNotNull(database.canonicalCatalogDao().redirect("story:b"))
        }
    }

    @Test
    fun nestedRedirectLineageRequiresReviewInsteadOfBlindSplit() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            seedStory(database, "story:x", "source:x", createdAt = 3)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:b-x").execute(mergeRequest("story:b", "story:x")),
            )
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:a-b").execute(mergeRequest("story:a", "story:b")),
            )
            val expectedRevision = requireNotNull(
                RoomStoryIdentityResolver(database).identityState(StoryId("story:a")),
            ).identityRevision

            val result = reversalCoordinator(database, "reverse:a-b").reverse(
                StoryMergeReverseRequest("merge:a-b", expectedRevision),
            )
            val review = assertIs<StoryMergeReverseResult.ReviewRequired>(result)
            assertTrue(review.reasons.any { it.contains("redirect") || it.contains("lineage") })
            assertNull(database.canonicalCatalogDao().mergeReversalEvent("merge:a-b"))
        }
    }


    @Test
    fun correctionCaseResolutionCommitsAtomicallyWithReversal() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:correction").execute(mergeRequest("story:a", "story:b")),
            )
            val cases = RoomReconciliationCaseRepository(database)
            val key = ReconciliationCaseKey.of(StoryId("story:a"), StoryId("story:b"))
            val correction = requireNotNull(
                cases.recordAssessment(
                    key = key,
                    assessment = correctionAssessment(),
                    evaluatedAtEpochMillis = 150,
                ),
            )
            val identityRevision = requireNotNull(
                RoomStoryIdentityResolver(database).identityState(StoryId("story:a")),
            ).identityRevision

            assertIs<StoryMergeReverseResult.Reversed>(
                reversalCoordinator(database, "reverse:correction").reverse(
                    StoryMergeReverseRequest(
                        mergeEventId = "merge:correction",
                        expectedSurvivorIdentityRevision = identityRevision,
                        expectedReconciliationCaseId = correction.id,
                        expectedReconciliationCaseRevision = correction.revision,
                    ),
                ),
            )

            val resolved = requireNotNull(cases.find(correction.id))
            assertEquals(ReconciliationCaseStatus.RESOLVED_SEPARATE, resolved.status)
            assertEquals(ReconciliationResolutionOrigin.USER, resolved.resolutionOrigin)
            assertEquals(correction.revision + 1L, resolved.revision)
            assertTrue(foreignKeyViolations(database).isEmpty())
        }
    }

    @Test
    fun degradedCanonicalStateRequiresReviewBeforeSplit() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:degraded").execute(mergeRequest("story:a", "story:b")),
            )
            val state = requireNotNull(database.canonicalCatalogDao().canonicalState("story:a"))
            database.canonicalCatalogDao().upsertCanonicalState(state.copy(health = "DEGRADED"))
            val request = StoryMergeReverseRequest("merge:degraded", state.identityRevision)

            val result = reversalCoordinator(database, "reverse:degraded").reverse(request)

            val review = assertIs<StoryMergeReverseResult.ReviewRequired>(result)
            assertTrue(review.reasons.any { it.contains("degraded") })
            assertNull(database.catalogDao().findStory("story:b"))
            assertNotNull(database.canonicalCatalogDao().redirect("story:b"))
        }
    }

    @Test
    fun parkedCanonicalInvariantRequiresReviewBeforeSplit() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:parked").execute(mergeRequest("story:a", "story:b")),
            )
            val dao = database.canonicalCatalogDao()
            val state = requireNotNull(dao.canonicalState("story:a"))
            val work = requireNotNull(dao.work("story:a", "FUSION_REBUILD"))
            dao.upsertWork(
                work.copy(
                    nextAttemptAtEpochMillis = Long.MAX_VALUE,
                    lastErrorCode = "canonical.invariant.seed",
                ),
            )

            val result = reversalCoordinator(database, "reverse:parked").reverse(
                StoryMergeReverseRequest("merge:parked", state.identityRevision),
            )

            val review = assertIs<StoryMergeReverseResult.ReviewRequired>(result)
            assertTrue(review.reasons.any { it.contains("parked_invariant") })
            assertNull(database.catalogDao().findStory("story:b"))
        }
    }

    @Test
    fun mergeRecordedByFutureReconciliationPolicyCannotBeAutoReversed() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:future-event").execute(mergeRequest("story:a", "story:b")),
            )
            database.openHelper.writableDatabase.execSQL(
                "UPDATE story_merge_events SET policy_version = ? WHERE merge_event_id = ?",
                arrayOf<Any?>(Int.MAX_VALUE, "merge:future-event"),
            )
            val state = requireNotNull(database.canonicalCatalogDao().canonicalState("story:a"))

            val result = reversalCoordinator(database, "reverse:future-event").reverse(
                StoryMergeReverseRequest("merge:future-event", state.identityRevision),
            )

            val review = assertIs<StoryMergeReverseResult.ReviewRequired>(result)
            assertTrue(review.reasons.any { it.contains("unsupported_policy") })
            assertNull(database.catalogDao().findStory("story:b"))
        }
    }

    @Test
    fun futureRequiredCanonicalPolicyRequiresReviewBeforeSplit() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:future-policy").execute(mergeRequest("story:a", "story:b")),
            )
            val dao = database.canonicalCatalogDao()
            val state = requireNotNull(dao.canonicalState("story:a"))
            val work = requireNotNull(dao.work("story:a", "FUSION_REBUILD"))
            dao.upsertWork(work.copy(requiredPolicyVersion = Int.MAX_VALUE))

            val result = reversalCoordinator(database, "reverse:future-policy").reverse(
                StoryMergeReverseRequest("merge:future-policy", state.identityRevision),
            )

            val review = assertIs<StoryMergeReverseResult.ReviewRequired>(result)
            assertTrue(review.reasons.any { it.contains("unsupported_policy") })
            assertNull(database.catalogDao().findStory("story:b"))
        }
    }

    @Test
    fun auditStateThatAlreadyRequiresReviewCannotBeAutoReversed() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:audit-review").execute(mergeRequest("story:a", "story:b")),
            )
            val dao = database.canonicalCatalogDao()
            val event = requireNotNull(dao.mergeEvent("merge:audit-review"))
            dao.updateMergeReversibility(
                mergeEventId = event.mergeEventId,
                state = "REQUIRES_REVIEW_TO_REVERSE",
                payloadVersion = event.reversalPayloadVersion,
                payload = event.reversalPayload,
            )
            val state = requireNotNull(dao.canonicalState("story:a"))

            val result = reversalCoordinator(database, "reverse:audit-review").reverse(
                StoryMergeReverseRequest("merge:audit-review", state.identityRevision),
            )

            val review = assertIs<StoryMergeReverseResult.ReviewRequired>(result)
            assertTrue(review.reasons.any { it.contains("audit_requires_review") })
            assertNull(database.catalogDao().findStory("story:b"))
        }
    }

    @Test
    fun failureBeforeReversalAuditRollsBackRestoredStoryAndDomainWrites() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 10)
            seedStory(database, "story:b", "source:b", createdAt = 20)
            assertIs<StoryMergeResult.Merged>(
                mergeCoordinator(database, "merge:rollback").execute(mergeRequest("story:a", "story:b")),
            )
            val expectedRevision = requireNotNull(
                RoomStoryIdentityResolver(database).identityState(StoryId("story:a")),
            ).identityRevision
            val coordinator = RoomStoryMergeReversalCoordinator(
                database = database,
                clock = FakeClock(200),
                reversalEventIdFactory = { "reverse:rollback" },
                beforeAudit = { error("injected") },
            )

            assertFailsWith<IllegalStateException> {
                coordinator.reverse(StoryMergeReverseRequest("merge:rollback", expectedRevision))
            }
            assertNull(database.catalogDao().findStory("story:b"))
            assertEquals("story:a", database.catalogDao().findEntry("plugin:catalog", "source:b")?.storyId)
            assertEquals("story:a", database.canonicalCatalogDao().redirect("story:b")?.canonicalStoryId)
            assertNull(database.canonicalCatalogDao().mergeReversalEvent("merge:rollback"))
            assertTrue(foreignKeyViolations(database).isEmpty())
        }
    }

    private fun correctionAssessment() = ReconciliationAssessment(
        policyVersion = 1,
        semanticDecision = ReconciliationSemanticDecision.REVIEW,
        mergeEligibility = ReconciliationMergeEligibility.INVARIANT_BLOCKED,
        confidence = 0.2,
        titleSimilarity = 0.2,
        authorSimilarity = null,
        winningLead = null,
        matchedIdentifiers = emptySet(),
        conflictingIdentifiers = emptySet(),
        reasons = setOf(ReconciliationReasonCode.WORK_IDENTIFIER_CONFLICT),
        identityEvidenceFingerprint = "correction:fingerprint",
    )

    private fun mergeCoordinator(database: OpenStoryDatabase, mergeEventId: String) = RoomStoryGraphMergeCoordinator(
        database = database,
        clock = FakeClock(100),
        mergeEventIdFactory = { mergeEventId },
    )

    private fun reversalCoordinator(database: OpenStoryDatabase, reversalEventId: String) =
        RoomStoryMergeReversalCoordinator(
            database = database,
            clock = FakeClock(200),
            reversalEventIdFactory = { reversalEventId },
        )

    private fun mergeRequest(left: String, right: String) = StoryMergeRequest(
        requestId = "request:$left:$right",
        leftStoryId = StoryId(left),
        rightStoryId = StoryId(right),
        origin = StoryMergeOrigin.MANUAL_MAINTENANCE,
        reconciliationCaseId = null,
        evidenceFingerprint = "evidence:$left:$right",
        reconciliationPolicyVersion = 1,
    )

    private suspend fun seedStory(
        database: OpenStoryDatabase,
        storyId: String,
        sourceId: String,
        createdAt: Long,
    ) {
        database.catalogDao().upsertStories(listOf(StoryEntity(storyId, "MANGA")))
        database.canonicalCatalogDao().upsertCanonicalState(
            StoryCanonicalStateEntity(storyId, null, "REEVALUATING", "AUTO", null, null, 0, 0, createdAt),
        )
        database.catalogDao().upsertEntries(listOf(catalogEntry(storyId, sourceId, createdAt)))
        database.canonicalCatalogDao().upsertWork(
            CanonicalEngineWorkEntity(storyId, "FUSION_REBUILD", "seed", 0, 0, null, 1),
        )
    }

    private fun catalogEntry(storyId: String, sourceId: String, time: Long) = CatalogEntryEntity(
        pluginId = "plugin:catalog",
        sourceId = sourceId,
        storyId = storyId,
        title = storyId,
        aliases = emptySet(),
        authors = emptySet(),
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

    private suspend fun seedChapter(
        database: OpenStoryDatabase,
        storyId: String,
        chapterId: String,
        releaseId: String,
    ) {
        database.chapterDao().upsertChapters(
            listOf(CanonicalChapterEntity(chapterId, storyId, "NUMBERED", null, "1", null, null, "Chapter 1", false)),
        )
        database.chapterDao().upsertReleases(
            listOf(
                ChapterReleaseEntity(
                    releaseId, storyId, "plugin:chapter", "source:$storyId", "source:$releaseId",
                    "Chapter 1", "NUMBERED", null, "1", null, null, "en", 1, chapterId,
                ),
            ),
        )
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
