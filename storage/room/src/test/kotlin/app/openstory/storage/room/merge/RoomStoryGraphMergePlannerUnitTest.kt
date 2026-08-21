package app.openstory.storage.room.merge

import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.identity.CanonicalIdentityState
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.identity.StoryIdentityRepository
import app.openstory.catalog.identity.StoryMergeOrigin
import app.openstory.catalog.identity.StoryMergeRequest
import app.openstory.catalog.identity.StoryMergeResolution
import app.openstory.catalog.identity.ProtectedContentMappingConflict
import app.openstory.catalog.model.ContentType
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryStatus
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RoomStoryGraphMergePlannerUnitTest {
    @Test
    fun catalogOnlyPreparationIsDeterministicAndMovesRetiredSources() = runTest {
        val a = snapshot("story:a", revision = 4, createdAt = 20, sourceId = "a")
        val b = snapshot("story:b", revision = 7, createdAt = 10, sourceId = "b")
        val planner = planner(a, b)

        val first = assertIs<StoryGraphMergePreparation.Ready>(planner.prepare(request("story:a", "story:b"))).plan
        val second = assertIs<StoryGraphMergePreparation.Ready>(planner.prepare(request("story:a", "story:b"))).plan

        assertEquals(StoryId("story:b"), first.survivorStoryId)
        assertEquals(StoryId("story:a"), first.retiredStoryId)
        assertEquals(setOf(SourceKey(PluginId("plugin:test"), "a")), first.sourceKeysToMove)
        assertEquals(7, first.expectedVersion.survivorIdentityRevision)
        assertEquals(4, first.expectedVersion.retiredIdentityRevision)
        assertEquals(first, second)
    }

    @Test
    fun protectedMappingConflictRequiresTypedReviewUnlessResolutionIsValid() = runTest {
        val plugin = PluginId("plugin:content")
        val a = snapshot("story:a", sourceId = "a").copy(
            mappings = listOf(mapping("story:a", plugin, "source-x")),
        )
        val b = snapshot("story:b", sourceId = "b").copy(
            mappings = listOf(mapping("story:b", plugin, "source-y")),
        )
        val planner = planner(a, b)

        val blocked = assertIs<StoryGraphMergePreparation.ReviewRequired>(
            planner.prepare(request("story:a", "story:b")),
        )
        assertEquals(
            listOf(ProtectedContentMappingConflict(plugin, setOf("source-x", "source-y"))),
            blocked.protectedContentMappingConflicts,
        )

        val resolved = assertIs<StoryGraphMergePreparation.Ready>(
            planner.prepare(
                request("story:a", "story:b").copy(
                    resolutions = listOf(StoryMergeResolution.ContentMappingTarget(plugin, "source-x")),
                ),
            ),
        ).plan
        assertEquals("source-x", resolved.mappingPlan.mappings.single().sourceStoryId)
    }

    @Test
    fun authoritativeFingerprintChangesWhenLibraryChangesWithoutIdentityRevision() = runTest {
        val base = snapshot("story:a", revision = 3, sourceId = "a")
        val changed = base.copy(
            libraryEntry = LibraryEntry(StoryId("story:a"), LibraryStatus.READING, 1, 2),
        )
        val baseFingerprint = base.authoritativeFingerprint()
        val changedFingerprint = changed.authoritativeFingerprint()

        assertNotEquals(baseFingerprint, changedFingerprint)
        assertEquals(base.identityRevision, changed.identityRevision)
    }

    @Test
    fun historicalInputsResolveBeforePlanningAndCanShortCircuitAlreadyCanonical() = runTest {
        val a = snapshot("story:a", sourceId = "a")
        val c = snapshot("story:c", sourceId = "c")
        val identity = FakeIdentity(mapOf(StoryId("story:b") to StoryId("story:a")))
        val planner = RoomStoryGraphMergePlanner(identity, FakeReader(mapOf(a.storyId to a, c.storyId to c)))

        val ready = assertIs<StoryGraphMergePreparation.Ready>(planner.prepare(request("story:b", "story:c")))
        assertTrue(setOf(ready.plan.survivorStoryId, ready.plan.retiredStoryId) == setOf(a.storyId, c.storyId))
        assertEquals(
            StoryId("story:a"),
            assertIs<StoryGraphMergePreparation.AlreadyCanonical>(
                planner.prepare(request("story:b", "story:a")),
            ).survivorStoryId,
        )
    }

    private fun planner(vararg snapshots: StoryMergeSnapshot): RoomStoryGraphMergePlanner =
        RoomStoryGraphMergePlanner(FakeIdentity(), FakeReader(snapshots.associateBy(StoryMergeSnapshot::storyId)))

    private fun request(left: String, right: String) = StoryMergeRequest(
        requestId = "request:$left:$right",
        leftStoryId = StoryId(left),
        rightStoryId = StoryId(right),
        origin = StoryMergeOrigin.AUTO_RECONCILIATION,
        reconciliationCaseId = null,
        evidenceFingerprint = "evidence",
        reconciliationPolicyVersion = 1,
    )

    private fun snapshot(
        id: String,
        revision: Long = 0,
        createdAt: Long? = null,
        sourceId: String,
    ): StoryMergeSnapshot {
        val storyId = StoryId(id)
        return StoryMergeSnapshot(
            storyId = storyId,
            contentType = ContentType.MANGA,
            identityRevision = revision,
            createdAtEpochMillis = createdAt,
            sourceKeys = setOf(SourceKey(PluginId("plugin:test"), sourceId)),
            sourcePreference = CanonicalSourcePreference(
                storyId,
                CanonicalSourcePreferenceMode.AUTO,
                null,
                revision = 0,
            ),
            libraryEntry = null,
            mappings = emptyList(),
            rejections = emptyList(),
            chapterGraph = ChapterGraphSnapshot(emptyList(), emptyList(), emptyList()),
            syncStates = emptyList(),
            readingProgress = emptyList(),
        )
    }

    private fun mapping(storyId: String, pluginId: PluginId, sourceId: String) = ContentMapping(
        StoryId(storyId),
        pluginId,
        sourceId,
        ContentMappingOrigin.USER_APPROVED,
        policyVersion = 1,
        updatedAt = 1,
    )

    private class FakeReader(
        private val snapshots: Map<StoryId, StoryMergeSnapshot>,
    ) : StoryMergeSnapshotReader {
        override suspend fun read(storyId: StoryId): StoryMergeSnapshot? = snapshots[storyId]
    }

    private class FakeIdentity(
        private val redirects: Map<StoryId, StoryId> = emptyMap(),
    ) : StoryIdentityRepository {
        override fun observeResolved(storyId: StoryId): Flow<StoryId> = flowOf(resolveNow(storyId))

        override suspend fun resolve(storyId: StoryId): StoryId = resolveNow(storyId)

        override suspend fun identityState(storyId: StoryId): CanonicalIdentityState? = null

        private fun resolveNow(storyId: StoryId): StoryId = redirects[storyId] ?: storyId
    }
}
