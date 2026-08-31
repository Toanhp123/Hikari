package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.common.dispatchers.FixedAppDispatchers
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverProjectionPipelineTest {
    @Test
    fun projectionBoundaryJoinsHomeFeedWithCanonicalPresentation() = runTest {
        val pipeline = pipeline()
        val storyId = StoryId("story:one")
        val canonical = projection(storyId, "Canonical One")

        val projected = pipeline.project(
            homes = listOf(snapshot(listOf(storyId))),
            projections = listOf(canonical),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(storyId to projected(canonical)),
        )

        assertEquals(listOf("Canonical One"), projected.content.popular.map { it.title })
        assertEquals(false, projected.content.sourceEmpty)
        assertEquals(0, projected.pendingSlots)
        assertEquals(1, projected.expectedSlots)
    }

    @Test
    fun unresolvedPopularLeaderPreventsLowerRankPromotion() = runTest {
        val leader = StoryId("story:leader")
        val follower = StoryId("story:follower")
        val followerProjection = projection(follower, "Follower")

        val result = pipeline().project(
            homes = listOf(snapshot(listOf(leader, follower))),
            projections = listOf(followerProjection),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(follower to projected(followerProjection)),
        )

        assertTrue(result.content.popular.isEmpty())
        assertEquals(1, result.pendingSlots)
        assertEquals(2, result.expectedSlots)
    }

    @Test
    fun unresolvedPopularLeaderDoesNotBlockStableLatestSection() = runTest {
        val popularLeader = StoryId("story:popular-pending")
        val latestStory = StoryId("story:latest-ready")
        val latestProjection = projection(latestStory, "Latest")
        val home = CatalogHomeSnapshot(
            pluginId = PluginId("catalog.one"),
            pluginVersion = "1.0.0",
            sections = listOf(
                CatalogHomeSection(
                    sourceId = "popular",
                    title = "Popular",
                    items = listOf(
                        CatalogEntry(
                            pluginId = PluginId("catalog.one"),
                            sourceId = "popular:0",
                            storyId = popularLeader,
                            title = "Pending popular",
                            contentType = ContentType.MANGA,
                            popularityRank = 1L,
                        ),
                    ),
                    kind = CatalogFeedKind.POPULAR,
                ),
                CatalogHomeSection(
                    sourceId = "latest",
                    title = "Latest",
                    items = listOf(
                        CatalogEntry(
                            pluginId = PluginId("catalog.one"),
                            sourceId = "latest:0",
                            storyId = latestStory,
                            title = "Latest raw",
                            contentType = ContentType.MANGA,
                            latestUpdate = CatalogLatestUpdate(100L, "1"),
                        ),
                    ),
                    kind = CatalogFeedKind.LATEST_UPDATES,
                ),
            ),
            refreshedAtEpochMillis = 1L,
        )

        val result = pipeline().project(
            homes = listOf(home),
            projections = listOf(latestProjection),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(latestStory to projected(latestProjection)),
        )

        assertTrue(result.content.popular.isEmpty())
        assertEquals(listOf(latestStory), result.content.latestUpdates.map { it.storyId })
        assertEquals(1, result.pendingSlots)
        assertEquals(2, result.expectedSlots)
    }

    @Test
    fun resolvedExcludedLeaderAllowsNextStoryToBecomeStableLeader() = runTest {
        val leader = StoryId("story:excluded")
        val follower = StoryId("story:follower")
        val followerProjection = projection(follower, "Follower")

        val result = pipeline().project(
            homes = listOf(snapshot(listOf(leader, follower))),
            projections = listOf(followerProjection),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(
                leader to DiscoverCanonicalSettlement.ResolvedExcluded(
                    leader,
                    DiscoverExclusionReason.CONTENT_TYPE_MISMATCH,
                ),
                follower to projected(followerProjection),
            ),
        )

        assertEquals(listOf(follower), result.content.popular.map { it.storyId })
        assertEquals(0, result.pendingSlots)
    }

    @Test
    fun failedLeaderAllowsNextStoryAfterFailureIsTerminal() = runTest {
        val leader = StoryId("story:failed")
        val follower = StoryId("story:follower")
        val failure = CatalogUiFailure("catalog.discover.canonical_bootstrap_failed", retryable = true)
        val followerProjection = projection(follower, "Follower")

        val result = pipeline().project(
            homes = listOf(snapshot(listOf(leader, follower))),
            projections = listOf(followerProjection),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(
                leader to DiscoverCanonicalSettlement.Failed(leader, failure),
                follower to projected(followerProjection),
            ),
        )

        assertEquals(listOf(follower), result.content.popular.map { it.storyId })
        assertEquals(mapOf(leader to failure), result.failures)
        assertEquals(0, result.pendingSlots)
    }

    @Test
    fun unresolvedLaterSlotDoesNotHideStableEarlierPrefix() = runTest {
        val leader = StoryId("story:leader")
        val later = StoryId("story:later")
        val leaderProjection = projection(leader, "Leader")

        val result = pipeline().project(
            homes = listOf(snapshot(listOf(leader, later))),
            projections = listOf(leaderProjection),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(leader to projected(leaderProjection)),
        )

        assertEquals(listOf(leader), result.content.popular.map { it.storyId })
        assertEquals(1, result.pendingSlots)
    }

    @Test
    fun allResolvedExcludedProducesAuthoritativeEmpty() = runTest {
        val storyId = StoryId("story:excluded")

        val result = pipeline().project(
            homes = listOf(snapshot(listOf(storyId))),
            projections = emptyList(),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(
                storyId to DiscoverCanonicalSettlement.ResolvedExcluded(
                    storyId,
                    DiscoverExclusionReason.CONTENT_TYPE_MISMATCH,
                ),
            ),
        )

        assertTrue(result.content.popular.isEmpty())
        assertEquals(false, result.content.sourceEmpty)
        assertEquals(0, result.pendingSlots)
        assertTrue(result.failures.isEmpty())
        assertEquals(1, result.expectedSlots)
    }

    @Test
    fun terminalFailuresWithNoProjectedItemsAreNotFalseEmpty() = runTest {
        val storyId = StoryId("story:failed")
        val failure = CatalogUiFailure("catalog.discover.projection_missing", retryable = true)

        val result = pipeline().project(
            homes = listOf(snapshot(listOf(storyId))),
            projections = emptyList(),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(storyId to DiscoverCanonicalSettlement.Failed(storyId, failure)),
        )

        assertTrue(result.content.popular.isEmpty())
        assertEquals(false, result.content.sourceEmpty)
        assertEquals(0, result.pendingSlots)
        assertEquals(mapOf(storyId to failure), result.failures)
        assertEquals(1, result.expectedSlots)
    }

    @Test
    fun settlementFailuresOutsideCurrentSlotsDoNotLeakIntoResult() = runTest {
        val visible = StoryId("story:visible")
        val stale = StoryId("story:stale")
        val projection = projection(visible, "Visible")
        val staleFailure = CatalogUiFailure("catalog.discover.stale", retryable = true)

        val result = pipeline().project(
            homes = listOf(snapshot(listOf(visible))),
            projections = listOf(projection),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(
                visible to projected(projection),
                stale to DiscoverCanonicalSettlement.Failed(stale, staleFailure),
            ),
        )

        assertTrue(result.failures.isEmpty())
        assertEquals(listOf(visible), result.content.popular.map { it.storyId })
    }

    @Test
    fun settledProjectionRetainsPresentationWhenLiveProjectionTemporarilyMissing() = runTest {
        val storyId = StoryId("story:retained")
        val settledProjection = projection(storyId, "Retained")

        val result = pipeline().project(
            homes = listOf(snapshot(listOf(storyId))),
            projections = emptyList(),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(storyId to projected(settledProjection)),
        )

        assertEquals(listOf("Retained"), result.content.popular.map { it.title })
    }

    @Test
    fun liveProjectionSupersedesSettledSnapshotWithoutChangingRank() = runTest {
        val storyId = StoryId("story:updated")
        val settledProjection = projection(storyId, "Settled")
        val liveProjection = projection(storyId, "Live")

        val result = pipeline().project(
            homes = listOf(snapshot(listOf(storyId))),
            projections = listOf(liveProjection),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(storyId to projected(settledProjection)),
        )

        assertEquals(listOf(storyId), result.content.popular.map { it.storyId })
        assertEquals(listOf("Live"), result.content.popular.map { it.title })
    }

    @Test
    fun terminalLeaderDoesNotAllowPromotionPastNextPendingSlot() = runTest {
        val failed = StoryId("story:failed")
        val pending = StoryId("story:pending")
        val later = StoryId("story:later")
        val failure = CatalogUiFailure("catalog.discover.canonical_bootstrap_failed", retryable = true)
        val laterProjection = projection(later, "Later")

        val result = pipeline().project(
            homes = listOf(snapshot(listOf(failed, pending, later))),
            projections = listOf(laterProjection),
            selectedContentType = ContentType.MANGA,
            settlements = mapOf(
                failed to DiscoverCanonicalSettlement.Failed(failed, failure),
                later to projected(laterProjection),
            ),
        )

        assertTrue(result.content.popular.isEmpty())
        assertEquals(1, result.pendingSlots)
        assertEquals(mapOf(failed to failure), result.failures)
    }

    private fun kotlinx.coroutines.test.TestScope.pipeline(): DiscoverProjectionPipeline {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return DiscoverProjectionPipeline(FixedAppDispatchers(dispatcher, dispatcher, dispatcher))
    }
}

private fun snapshot(storyIds: List<StoryId>) = CatalogHomeSnapshot(
    pluginId = PluginId("catalog.one"),
    pluginVersion = "1.0.0",
    sections = listOf(
        CatalogHomeSection(
            sourceId = "popular",
            title = "Popular",
            items = storyIds.mapIndexed { index, storyId ->
                CatalogEntry(
                    pluginId = PluginId("catalog.one"),
                    sourceId = "entry:$index",
                    storyId = storyId,
                    title = "Raw Story $index",
                    contentType = ContentType.MANGA,
                    popularityRank = index.toLong() + 1,
                )
            },
            kind = CatalogFeedKind.POPULAR,
        ),
    ),
    refreshedAtEpochMillis = 1L,
)

private fun projection(storyId: StoryId, title: String) = CatalogStoryProjection(
    storyId = storyId,
    title = title,
    contentType = ContentType.MANGA,
    coverUrl = "canonical.jpg",
)

private fun projected(projection: CatalogStoryProjection) =
    DiscoverCanonicalSettlement.Projected(projection.storyId, projection)
