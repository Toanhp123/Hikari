package app.openstory.storage.room.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
import app.openstory.common.FakeClock
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCanonicalEngineStateTest {

    @Test
    fun canonicalStatePreferenceInvariantIsEnforcedOnFreshDatabaseWrites() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a")
            val dao = database.canonicalCatalogDao()

            assertFailsWith<IllegalArgumentException> {
                dao.upsertCanonicalState(
                    StoryCanonicalStateEntity(
                        "story:a", null, "REEVALUATING", "AUTO", "plugin:one", "source:one", 0, 0, 1,
                    ),
                )
            }
            assertFailsWith<IllegalArgumentException> {
                dao.upsertCanonicalState(
                    StoryCanonicalStateEntity(
                        "story:a", null, "REEVALUATING", "PINNED", null, null, 0, 0, 1,
                    ),
                )
            }
        }
    }

    @Test
    fun redirectInvariantRejectsSelfRedirectBeforePersistence() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a")

            assertFailsWith<IllegalArgumentException> {
                database.canonicalCatalogDao().upsertRedirect(
                    StoryRedirectEntity("story:a", "story:a", "merge:1", 1),
                )
            }
        }
    }

    @Test
    fun dirtyWorkCoalescesAndRetryUpdatesOneRowDeterministically() = runTest {
        withDatabase { database ->
            seedStory(database, "story:1")
            val repository = RoomCanonicalEngineWorkRepository(database, FakeClock(0))
            repeat(10) { index ->
                repository.markDirty(
                    StoryId("story:1"),
                    CanonicalEngineWorkType.FUSION_REBUILD,
                    reason = "change:$index",
                    requiredPolicyVersion = index + 1,
                )
            }

            val claimed = repository.claimReady(nowEpochMillis = 9, limit = 10).single()
            assertEquals("change:9", claimed.reason)
            assertEquals(10, claimed.requiredPolicyVersion)
            assertEquals(0, claimed.attemptCount)

            repository.retry(claimed, "temporary", nextAttemptAtEpochMillis = 50)
            assertTrue(repository.claimReady(nowEpochMillis = 49, limit = 10).isEmpty())
            val retried = repository.claimReady(nowEpochMillis = 50, limit = 10).single()
            assertEquals(1, retried.attemptCount)
            assertEquals("temporary", retried.lastFailureCode)

            assertTrue(repository.complete(retried))
            assertTrue(repository.claimReady(nowEpochMillis = 100, limit = 10).isEmpty())
        }
    }

    @Test
    fun identicalDirtyEventGetsNewQueueRevisionSoStaleCompletionCannotDeleteIt() = runTest {
        withDatabase { database ->
            seedStory(database, "story:identical-race")
            val clock = FakeClock(100)
            val repository = RoomCanonicalEngineWorkRepository(database, clock)
            val stale = repository.markDirty(
                StoryId("story:identical-race"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "same",
                requiredPolicyVersion = 1,
            )

            val newer = repository.markDirty(
                StoryId("story:identical-race"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "same",
                requiredPolicyVersion = 1,
            )
            assertEquals(false, repository.complete(stale))

            assertEquals(100L, stale.nextAttemptAtEpochMillis)
            assertEquals(101L, newer.nextAttemptAtEpochMillis)
            assertEquals(newer, repository.claimReady(101, 1).single())
        }
    }

    @Test
    fun completingStaleSnapshotDoesNotDeleteNewlyMarkedWork() = runTest {
        withDatabase { database ->
            seedStory(database, "story:race")
            val repository = RoomCanonicalEngineWorkRepository(database, FakeClock(0))
            repository.markDirty(
                StoryId("story:race"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "old",
                requiredPolicyVersion = 1,
            )
            val stale = repository.claimReady(0, 1).single()

            repository.markDirty(
                StoryId("story:race"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "new",
                requiredPolicyVersion = 2,
            )
            assertEquals(false, repository.complete(stale))

            val current = repository.claimReady(1, 1).single()
            assertEquals("new", current.reason)
            assertEquals(2, current.requiredPolicyVersion)
        }
    }

    @Test
    fun retryingStaleSnapshotDoesNotOverwriteNewlyMarkedWork() = runTest {
        withDatabase { database ->
            seedStory(database, "story:retry-race")
            val repository = RoomCanonicalEngineWorkRepository(database, FakeClock(0))
            val stale = repository.markDirty(
                StoryId("story:retry-race"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "old",
                requiredPolicyVersion = 1,
            )

            repository.markDirty(
                StoryId("story:retry-race"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "new",
                requiredPolicyVersion = 3,
            )
            repository.retry(stale, "stale-failure", nextAttemptAtEpochMillis = 50)

            val current = repository.claimReady(1, 1).single()
            assertEquals("new", current.reason)
            assertEquals(3, current.requiredPolicyVersion)
            assertEquals(0, current.attemptCount)
            assertEquals(null, current.lastFailureCode)
        }
    }

    @Test
    fun rekeyingIdenticalSurvivorWorkCreatesFreshSnapshotBeforeStaleCompletion() = runTest {
        withDatabase { database ->
            seedStory(database, "story:survivor-race")
            seedStory(database, "story:retired-race")
            val repository = RoomCanonicalEngineWorkRepository(database, FakeClock(100))
            repository.markDirty(
                StoryId("story:survivor-race"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "same",
                requiredPolicyVersion = 1,
            )
            repository.markDirty(
                StoryId("story:retired-race"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "same",
                requiredPolicyVersion = 1,
            )
            val staleSurvivor = repository.claimReady(100, 2).single {
                it.storyId == StoryId("story:survivor-race")
            }

            database.canonicalCatalogDao().rekeyRetiredStoryState(
                "story:retired-race",
                "story:survivor-race",
                nowEpochMillis = 100,
            )
            assertEquals(false, repository.complete(staleSurvivor))

            val current = repository.claimReady(101, 1).single()
            assertEquals(StoryId("story:survivor-race"), current.storyId)
            assertEquals(101L, current.nextAttemptAtEpochMillis)
        }
    }

    @Test
    fun rekeyingWorkDoesNotUnparkInvariantBlockedSource() = runTest {
        withDatabase { database ->
            seedStory(database, "story:survivor")
            seedStory(database, "story:retired")
            val dao = database.canonicalCatalogDao()
            dao.upsertWork(
                CanonicalEngineWorkEntity(
                    "story:survivor",
                    CanonicalEngineWorkType.FUSION_REBUILD.name,
                    "survivor-dirty",
                    0,
                    0,
                    null,
                    1,
                ),
            )
            dao.upsertWork(
                CanonicalEngineWorkEntity(
                    "story:retired",
                    CanonicalEngineWorkType.FUSION_REBUILD.name,
                    "retired-invariant",
                    2,
                    Long.MAX_VALUE,
                    "canonical.invariant.identity",
                    2,
                ),
            )

            dao.rekeyRetiredStoryState("story:retired", "story:survivor", nowEpochMillis = 0)

            val current = requireNotNull(dao.work("story:survivor", CanonicalEngineWorkType.FUSION_REBUILD.name))
            assertEquals(Long.MAX_VALUE, current.nextAttemptAtEpochMillis)
            assertEquals("canonical.invariant.identity", current.lastErrorCode)
            assertEquals(2, current.requiredPolicyVersion)
        }
    }

    @Test
    fun rekeyingDifferentParkedFailuresDoesNotBecomePolicyRecoverable() = runTest {
        withDatabase { database ->
            seedStory(database, "story:survivor-mixed-park")
            seedStory(database, "story:retired-mixed-park")
            val repository = RoomCanonicalEngineWorkRepository(database, FakeClock(0))
            val survivor = repository.markDirty(
                StoryId("story:survivor-mixed-park"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "policy-block",
                requiredPolicyVersion = 99,
            )
            repository.blockInvariant(
                survivor,
                "canonical.maintenance.unsupported_required_policy_version",
            )
            val retired = repository.markDirty(
                StoryId("story:retired-mixed-park"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "identity-block",
                requiredPolicyVersion = 1,
            )
            repository.blockInvariant(retired, "canonical.maintenance.identity_invariant")

            database.canonicalCatalogDao().rekeyRetiredStoryState(
                "story:retired-mixed-park",
                "story:survivor-mixed-park",
                nowEpochMillis = 0,
            )

            val current = requireNotNull(
                database.canonicalCatalogDao().work(
                    "story:survivor-mixed-park",
                    CanonicalEngineWorkType.FUSION_REBUILD.name,
                ),
            )
            assertEquals("canonical.maintenance.coalesced_invariant", current.lastErrorCode)
            assertTrue(
                repository.blocked(
                    setOf("canonical.maintenance.unsupported_required_policy_version"),
                    limit = 1,
                ).isEmpty(),
            )
        }
    }

    @Test
    fun blockedPolicyRecoveryRequeuesOnlyExactParkedSnapshot() = runTest {
        withDatabase { database ->
            seedStory(database, "story:recoverable")
            val repository = RoomCanonicalEngineWorkRepository(database, FakeClock(100))
            val initial = repository.markDirty(
                StoryId("story:recoverable"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "old",
                requiredPolicyVersion = 1,
            )
            repository.blockInvariant(initial, "canonical.maintenance.unsupported_required_policy_version")
            val recoverableCodes = setOf("canonical.maintenance.unsupported_required_policy_version")
            val staleParked = repository.blocked(recoverableCodes, 1).single()

            repository.markDirty(
                StoryId("story:recoverable"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "new",
                requiredPolicyVersion = 1,
            )
            assertEquals(null, repository.requeueBlocked(staleParked))

            val currentParked = repository.blocked(recoverableCodes, 1).single()
            val requeued = requireNotNull(repository.requeueBlocked(currentParked))
            assertEquals("new", requeued.reason)
            assertEquals(null, requeued.lastFailureCode)
            assertEquals(100L, requeued.nextAttemptAtEpochMillis)
            assertEquals(requeued, repository.claimReady(100, 1).single())
        }
    }

    @Test
    fun invariantBlockSurvivesLaterDirtyMarksUntilExplicitRepair() = runTest {
        withDatabase { database ->
            seedStory(database, "story:blocked")
            val repository = RoomCanonicalEngineWorkRepository(database, FakeClock(0))
            repository.markDirty(
                StoryId("story:blocked"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "first",
                requiredPolicyVersion = 1,
            )
            val claimed = repository.claimReady(0, 1).single()
            repository.blockInvariant(claimed, "canonical.invariant.redirect_cycle")

            repository.markDirty(
                StoryId("story:blocked"),
                CanonicalEngineWorkType.FUSION_REBUILD,
                reason = "later",
                requiredPolicyVersion = 2,
            )

            assertTrue(repository.claimReady(Long.MAX_VALUE - 1, 1).isEmpty())
            assertEquals(null, repository.nextAttemptAtEpochMillis())
            val parked = requireNotNull(
                database.canonicalCatalogDao().work("story:blocked", CanonicalEngineWorkType.FUSION_REBUILD.name),
            )
            assertEquals(Long.MAX_VALUE, parked.nextAttemptAtEpochMillis)
            assertEquals("canonical.invariant.redirect_cycle", parked.lastErrorCode)
            assertEquals(2, parked.requiredPolicyVersion)
        }
    }

    @Test
    fun caseRevisionsKeepHistoricalStoryIdsAndCurrentPairIsCanonicalized() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a")
            seedStory(database, "story:b")
            val dao = database.canonicalCatalogDao()
            dao.upsertReconciliationCase(
                ReconciliationCaseEntity("case:1", "story:b", "story:a", "PENDING", null, null, 1, 1),
            )
            dao.insertReconciliationRevision(
                ReconciliationCaseRevisionEntity(
                    "revision:1", "case:1", "story:b", "story:a", "REVIEW", "fingerprint", 1,
                    0.8, 0.9, 0.5, setOf("similar"), emptySet(), null, 1,
                ),
            )
            dao.insertReconciliationRevision(
                ReconciliationCaseRevisionEntity(
                    "revision:2", "case:1", "story:a", "story:b", "SEPARATE", "fingerprint-2", 1,
                    0.1, 0.2, null, setOf("different"), emptySet(), "USER", 2,
                ),
            )

            val stored = requireNotNull(dao.reconciliationCase("case:1"))
            assertEquals("story:a", stored.leftStoryId)
            assertEquals("story:b", stored.rightStoryId)
            assertEquals(2, dao.reconciliationRevisions("case:1").size)

            database.openHelper.writableDatabase.execSQL("DELETE FROM stories WHERE story_id = 'story:b'")
            assertEquals("story:b", dao.reconciliationRevisions("case:1").first().leftStoryId)
        }
    }

    @Test
    fun mergeAuditSurvivesRetiredStoryDeletion() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a")
            seedStory(database, "story:b")
            val dao = database.canonicalCatalogDao()
            dao.upsertMergeEvent(
                StoryMergeEventEntity(
                    "merge:1", "story:a", "story:b", "TEST", null, "fingerprint", 1, 1,
                    "REVERSIBLE", 1, "{}",
                ),
            )

            database.openHelper.writableDatabase.execSQL("DELETE FROM stories WHERE story_id = 'story:b'")

            assertEquals("story:b", dao.mergeEvent("merge:1")?.retiredStoryId)
        }
    }

    private suspend fun seedStory(database: OpenStoryDatabase, storyId: String) {
        database.catalogDao().upsertStories(listOf(StoryEntity(storyId, "MANGA")))
        database.canonicalCatalogDao().upsertCanonicalState(
            StoryCanonicalStateEntity(storyId, null, "REEVALUATING", "AUTO", null, null, 0, 0, 1),
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
