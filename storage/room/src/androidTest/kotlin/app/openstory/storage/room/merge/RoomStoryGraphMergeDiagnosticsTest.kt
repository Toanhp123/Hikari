package app.openstory.storage.room.merge

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.catalog.diagnostics.CanonicalDecisionTrace
import app.openstory.catalog.diagnostics.CanonicalDiagnostics
import app.openstory.catalog.diagnostics.CanonicalTraceKind
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResult
import app.openstory.common.FakeClock
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.catalog.CanonicalEngineWorkEntity
import app.openstory.storage.room.catalog.CatalogEntryEntity
import app.openstory.storage.room.catalog.StoryCanonicalStateEntity
import app.openstory.storage.room.catalog.StoryEntity
import app.openstory.storage.room.library.ContentMappingEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomStoryGraphMergeDiagnosticsTest {
    @Test
    fun mergeCommitTraceIsStructuredAndSinkFailureCannotChangeDatabaseMutation() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            val traces = mutableListOf<CanonicalDecisionTrace>()
            val coordinator = coordinator(
                database,
                "merge:diagnostic",
                CanonicalDiagnostics(traces::add),
            )

            val result = assertIs<StoryMergeResult.Merged>(
                coordinator.execute(request("story:a", "story:b", "evidence:diagnostic")),
            )

            assertEquals(StoryId("story:a"), result.survivorStoryId)
            assertEquals(CanonicalTraceKind.MERGE_COMMITTED, traces.single().kind)
            assertEquals(setOf(StoryId("story:a"), StoryId("story:b")), traces.single().storyIds)
            assertEquals(listOf("story_merge.committed"), traces.single().reasonCodes)

            seedStory(database, "story:c", "source:c", createdAt = 3)
            val failOpen = coordinator(
                database,
                "merge:diagnostic-fail-open",
                CanonicalDiagnostics { error("sink failed") },
            )
            assertIs<StoryMergeResult.Merged>(failOpen.execute(request("story:a", "story:c")))
            assertNull(database.catalogDao().findStory("story:c"))
        }
    }

    @Test
    fun protectedConflictEmitsBlockedTraceWithoutMutatingStoryOwnership() = runTest {
        withDatabase { database ->
            seedStory(database, "story:a", "source:a", createdAt = 1)
            seedStory(database, "story:b", "source:b", createdAt = 2)
            database.libraryDao().insertMapping(
                ContentMappingEntity("story:a", "plugin:protected", "target:a", "USER_APPROVED", 1, 5),
            )
            database.libraryDao().insertMapping(
                ContentMappingEntity("story:b", "plugin:protected", "target:b", "USER_URL", 1, 6),
            )
            val traces = mutableListOf<CanonicalDecisionTrace>()
            val coordinator = coordinator(database, "merge:blocked", CanonicalDiagnostics(traces::add))

            assertIs<StoryMergeResult.ReviewRequired>(coordinator.execute(request("story:a", "story:b")))

            assertEquals(CanonicalTraceKind.MERGE_BLOCKED, traces.single().kind)
            assertEquals(setOf(StoryId("story:a"), StoryId("story:b")), traces.single().storyIds)
            assertTrue(traces.single().reasonCodes.isNotEmpty())
            assertNotNull(database.catalogDao().findStory("story:a"))
            assertNotNull(database.catalogDao().findStory("story:b"))
        }
    }

    private fun coordinator(
        database: OpenStoryDatabase,
        mergeEventId: String,
        diagnostics: CanonicalDiagnostics,
    ) = RoomStoryGraphMergeCoordinator(
        database = database,
        clock = FakeClock(100),
        mergeEventIdFactory = { mergeEventId },
        diagnostics = diagnostics,
    )

    private fun request(
        left: String,
        right: String,
        fingerprint: String = "evidence:$left:$right",
    ) = StoryMergeRequest(
        requestId = "request:$left:$right",
        leftStoryId = StoryId(left),
        rightStoryId = StoryId(right),
        origin = StoryMergeOrigin.AUTO_RECONCILIATION,
        reconciliationCaseId = null,
        evidenceFingerprint = fingerprint,
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
