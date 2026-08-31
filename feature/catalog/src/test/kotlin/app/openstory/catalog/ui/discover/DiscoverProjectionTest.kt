package app.openstory.catalog.ui.discover

import app.openstory.catalog.canonical.CanonicalHealth
import app.openstory.catalog.canonical.CanonicalScore
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoverProjectionTest {
    @Test
    fun canonicalBootstrapPriorityUsesVisibleFeedOrderAndDeduplicatesStories() {
        val shared = StoryId("story:shared")
        val popularSecond = entry("catalog.a", "popular-second", "Popular second", StoryId("story:popular-second"))
            .copy(popularityRank = 2)
        val popularFirst = entry("catalog.a", "popular-first", "Popular first", shared)
            .copy(popularityRank = 1)
        val latest = entry("catalog.a", "latest", "Latest", StoryId("story:latest"))
            .copy(latestUpdate = CatalogLatestUpdate(300L, "latest"))
        val sharedLatest = entry("catalog.a", "shared-latest", "Shared latest", shared)
            .copy(latestUpdate = CatalogLatestUpdate(200L, "shared"))
        val top = entry("catalog.a", "top", "Top", StoryId("story:top"))
            .copy(score = Score(9.0, 10.0))

        val homes = listOf(
            snapshot(CatalogFeedKind.POPULAR, listOf(popularSecond, popularFirst)),
            snapshot(CatalogFeedKind.LATEST_UPDATES, listOf(sharedLatest, latest)),
            snapshot(CatalogFeedKind.TOP_RATED, listOf(top)),
        )

        val slots = discoverFeedSlots(homes, ContentType.MANGA)

        assertEquals(listOf(shared, popularSecond.storyId), slots.popular)
        assertEquals(listOf(latest.storyId, shared), slots.latestUpdates)
        assertEquals(listOf(top.storyId), slots.topRated)
        assertEquals(5, slots.size)
        assertEquals(
            listOf(shared, popularSecond.storyId, latest.storyId, top.storyId),
            slots.expectedStoryIds,
        )
        assertEquals(slots.expectedStoryIds, discoverCanonicalBootstrapStoryIds(homes, ContentType.MANGA))
    }

    @Test
    fun canonicalProjectionOwnsPresentationEvenWhenLegacySourceWouldWin() {
        val storyId = StoryId("story:shared")
        val rawA = entry("catalog.a", "a", "Raw A", storyId).copy(
            coverUrl = "raw-a.jpg",
            publicationStatus = PublicationStatus.ONGOING,
            score = Score(9.9, 10.0),
            latestUpdate = CatalogLatestUpdate(100L, "Raw A release"),
        )
        val rawB = entry("catalog.b", "b", "Raw B", storyId)
        val canonical = projection(
            storyId,
            title = "Canonical B",
            cover = "canonical-b.jpg",
            status = PublicationStatus.COMPLETED,
            score = CanonicalScore(0.75, 2),
            latest = CatalogLatestUpdate(90L, "Canonical release"),
        )

        val state = projectSemanticDiscoverContent(
            homes = listOf(snapshot(CatalogFeedKind.POPULAR, listOf(rawA, rawB))),
            projections = listOf(canonical),
            selectedContentType = ContentType.MANGA,
        )

        val item = state.popular.single()
        assertEquals("Canonical B", item.title)
        assertEquals("canonical-b.jpg", item.coverUrl)
        assertEquals(PublicationStatus.COMPLETED, item.publicationStatus)
        assertEquals(Score(7.5, 10.0), item.score)
        assertEquals(CatalogLatestUpdate(90L, "Canonical release"), item.latestUpdate)
    }

    @Test
    fun popularRankingStillComesFromHomeFeedRank() {
        val first = entry("catalog.a", "first", "Raw first", StoryId("story:first")).copy(popularityRank = 2)
        val second = entry("catalog.a", "second", "Raw second", StoryId("story:second")).copy(popularityRank = 1)

        val state = projectSemanticDiscoverContent(
            homes = listOf(snapshot(CatalogFeedKind.POPULAR, listOf(first, second))),
            projections = listOf(
                projection(first.storyId, "Canonical first"),
                projection(second.storyId, "Canonical second"),
            ),
            selectedContentType = ContentType.MANGA,
        )

        assertEquals(listOf(second.storyId, first.storyId), state.popular.map { it.storyId })
    }

    @Test
    fun latestOrderingUsesHomeContributionTimestampButDisplaysCanonicalLatestObject() {
        val newer = entry("catalog.a", "new", "Raw new", StoryId("story:new")).copy(
            latestUpdate = CatalogLatestUpdate(200L, "raw-new"),
        )
        val older = entry("catalog.a", "old", "Raw old", StoryId("story:old")).copy(
            latestUpdate = CatalogLatestUpdate(100L, "raw-old"),
        )
        val canonicalNew = CatalogLatestUpdate(150L, "canonical-new")

        val state = projectSemanticDiscoverContent(
            homes = listOf(snapshot(CatalogFeedKind.LATEST_UPDATES, listOf(older, newer))),
            projections = listOf(
                projection(newer.storyId, "Canonical new", latest = canonicalNew),
                projection(older.storyId, "Canonical old", latest = CatalogLatestUpdate(300L, "canonical-old")),
            ),
            selectedContentType = ContentType.MANGA,
        )

        assertEquals(listOf(newer.storyId, older.storyId), state.latestUpdates.map { it.storyId })
        assertEquals(canonicalNew, state.latestUpdates.first().latestUpdate)
    }

    @Test
    fun topRatedRankingKeepsFeedAggregateWhilePresentationScoreIsCanonical() {
        val high = entry("catalog.a", "high", "Raw high", StoryId("story:high")).copy(score = Score(9.0, 10.0))
        val low = entry("catalog.a", "low", "Raw low", StoryId("story:low")).copy(score = Score(7.0, 10.0))

        val state = projectSemanticDiscoverContent(
            homes = listOf(snapshot(CatalogFeedKind.TOP_RATED, listOf(low, high))),
            projections = listOf(
                projection(high.storyId, "Canonical high", score = CanonicalScore(0.5, 3)),
                projection(low.storyId, "Canonical low", score = CanonicalScore(1.0, 1)),
            ),
            selectedContentType = ContentType.MANGA,
        )

        assertEquals(listOf(high.storyId, low.storyId), state.topRated.map { it.storyId })
        assertEquals(Score(5.0, 10.0), state.topRated.first().score)
    }

    private fun projection(
        storyId: StoryId,
        title: String,
        cover: String? = null,
        status: PublicationStatus? = null,
        score: CanonicalScore? = null,
        latest: CatalogLatestUpdate? = null,
    ) = CatalogStoryProjection(
        storyId,
        title,
        ContentType.MANGA,
        cover,
        publicationStatus = status,
        latestUpdate = latest,
        score = score,
        health = CanonicalHealth.FRESH,
    )

    private fun snapshot(kind: CatalogFeedKind, items: List<CatalogEntry>) = CatalogHomeSnapshot(
        PluginId("catalog.a"),
        "1.0.0",
        100L,
        listOf(CatalogHomeSection(kind.name, kind.name, items, kind)),
    )

    private fun entry(plugin: String, source: String, title: String, storyId: StoryId) = CatalogEntry(
        storyId,
        PluginId(plugin),
        source,
        title,
        contentType = ContentType.MANGA,
    )
}
