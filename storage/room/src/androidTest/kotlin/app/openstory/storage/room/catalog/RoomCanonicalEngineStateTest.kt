package app.openstory.storage.room.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.orchestration.CanonicalEngineWorkType
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
            val repository = RoomCanonicalEngineWorkRepository(database)
            repository.markDirty(StoryId("story:1"), CanonicalEngineWorkType.FUSION_REBUILD, "summary")
            repository.markDirty(StoryId("story:1"), CanonicalEngineWorkType.FUSION_REBUILD, "full")

            val claimed = repository.claimReady(nowEpochMillis = 0, limit = 10).single()
            assertEquals("full", claimed.reason)
            assertEquals(0, claimed.attemptCount)

            repository.retry(claimed, "temporary", nextAttemptAtEpochMillis = 50)
            assertTrue(repository.claimReady(nowEpochMillis = 49, limit = 10).isEmpty())
            val retried = repository.claimReady(nowEpochMillis = 50, limit = 10).single()
            assertEquals(1, retried.attemptCount)
            assertEquals("temporary", retried.lastFailureCode)

            repository.complete(retried)
            assertTrue(repository.claimReady(nowEpochMillis = 100, limit = 10).isEmpty())
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
