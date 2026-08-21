package app.openstory.storage.room.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.identity.StoryIdentityInvariantException
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStoryIdentityResolverTest {
    @Test
    fun resolveFollowsRedirectChainAndRejectsCycle() = runTest {
        withDatabase { database ->
            seedStories(database, "story:a", "story:b", "story:c")
            val dao = database.canonicalCatalogDao()
            dao.upsertMergeEvent(mergeEvent("merge:1", "story:a", "story:b"))
            dao.upsertMergeEvent(mergeEvent("merge:2", "story:c", "story:a"))
            dao.upsertRedirect(StoryRedirectEntity("story:b", "story:a", "merge:1", 1))
            dao.upsertRedirect(StoryRedirectEntity("story:a", "story:c", "merge:2", 2))
            val resolver = RoomStoryIdentityResolver(database)

            assertEquals(StoryId("story:c"), resolver.resolve(StoryId("story:b")))

            dao.upsertMergeEvent(mergeEvent("merge:3", "story:b", "story:c"))
            dao.upsertRedirect(StoryRedirectEntity("story:c", "story:b", "merge:3", 3))
            assertFailsWith<StoryIdentityInvariantException> {
                resolver.resolve(StoryId("story:b"))
            }
        }
    }

    @Test
    fun observerOpenedBeforeRedirectUpdateEmitsNewCanonicalTarget() = runTest {
        withDatabase { database ->
            seedStories(database, "story:a", "story:b", "story:c")
            val dao = database.canonicalCatalogDao()
            dao.upsertMergeEvent(mergeEvent("merge:1", "story:a", "story:b"))
            dao.upsertRedirect(StoryRedirectEntity("story:b", "story:a", "merge:1", 1))
            val resolver = RoomStoryIdentityResolver(database)
            val initialObserved = CompletableDeferred<Unit>()
            val observed = async {
                resolver.observeResolved(StoryId("story:b"))
                    .onEach { resolved ->
                        if (resolved == StoryId("story:a")) initialObserved.complete(Unit)
                    }
                    .take(2)
                    .toList()
            }
            initialObserved.await()

            dao.upsertMergeEvent(mergeEvent("merge:2", "story:c", "story:b"))
            dao.upsertRedirect(StoryRedirectEntity("story:b", "story:c", "merge:2", 2))

            assertEquals(
                listOf(StoryId("story:a"), StoryId("story:c")),
                observed.await(),
            )
        }
    }

    private suspend fun seedStories(database: OpenStoryDatabase, vararg storyIds: String) {
        database.catalogDao().upsertStories(storyIds.map { StoryEntity(it, "MANGA") })
        storyIds.forEach { id ->
            database.canonicalCatalogDao().upsertCanonicalState(
                StoryCanonicalStateEntity(id, null, "REEVALUATING", "AUTO", null, null, 0, 0, 1),
            )
        }
    }

    private fun mergeEvent(id: String, survivor: String, retired: String) = StoryMergeEventEntity(
        mergeEventId = id,
        survivorStoryId = survivor,
        retiredStoryId = retired,
        origin = "TEST",
        reconciliationCaseId = null,
        evidenceFingerprint = "fingerprint:$id",
        policyVersion = 1,
        mergedAtEpochMillis = 1,
        reversibilityState = "REVERSIBLE",
        reversalPayloadVersion = 1,
        reversalPayload = "{}",
    )

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
