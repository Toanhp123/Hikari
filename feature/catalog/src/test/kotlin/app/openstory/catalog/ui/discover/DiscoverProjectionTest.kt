package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogFeedKind
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoverProjectionTest {
    @Test
    fun semanticProjectionAppliesSectionLimitsAndMediaPolicy() {
        val state = projectSemanticDiscoverState(
            homes = listOf(
                snapshot(
                    plugin = "catalog.a",
                    sections = listOf(
                        section(CatalogFeedKind.POPULAR, entries("popular", 6)),
                        section(
                            CatalogFeedKind.LATEST_UPDATES,
                            entries("latest", 10) { index ->
                                copy(latestUpdate = CatalogLatestUpdate(1_000L - index, "ch-$index"))
                            },
                        ),
                        section(CatalogFeedKind.TOP_RATED, entries("rated", 6)),
                    ),
                ),
            ),
            selectedContentType = ContentType.MANGA,
            loading = false,
            refreshing = false,
            refreshReport = null,
        )

        assertEquals(5, state.popular.size)
        assertEquals(9, state.latestUpdates.size)
        assertEquals(5, state.topRated.size)
        assertEquals(ContentType.MANGA, state.selectedContentType)
        assertEquals(
            listOf(ContentType.MANGA, ContentType.LIGHT_NOVEL),
            state.mediaTypeOptions.map(DiscoverMediaTypeOption::contentType),
        )
        assertTrue(state.mediaTypeOptions.single { it.contentType == ContentType.MANGA }.enabled)
        assertFalse(state.mediaTypeOptions.single { it.contentType == ContentType.LIGHT_NOVEL }.enabled)
    }

    @Test
    fun semanticProjectionUsesExplicitFeedKindAndSelectedContentTypeOnly() {
        val manga = entry("catalog.a", "manga", "Manga", ContentType.MANGA)
        val novel = entry("catalog.a", "novel", "Novel", ContentType.LIGHT_NOVEL)
        val other = entry("catalog.a", "other", "Other", ContentType.MANGA)
        val state = projectSemanticDiscoverState(
            homes = listOf(
                snapshot(
                    "catalog.a",
                    listOf(
                        section(CatalogFeedKind.POPULAR, listOf(manga, novel)),
                        section(CatalogFeedKind.OTHER, listOf(other)),
                    ),
                ),
            ),
            selectedContentType = ContentType.MANGA,
            loading = false,
            refreshing = false,
            refreshReport = null,
        )

        assertEquals(listOf(manga.storyId), state.popular.map(DiscoverStoryItem::storyId))
        assertTrue(state.latestUpdates.isEmpty())
        assertTrue(state.topRated.isEmpty())
    }

    @Test
    fun latestRequiresSourceTimestampAndIgnoresCacheRefreshTime() {
        val newest = entry("catalog.a", "newest", "Newest").copy(
            latestUpdate = CatalogLatestUpdate(900L, "9"),
        )
        val middle = entry("catalog.a", "middle", "Middle").copy(
            latestUpdate = CatalogLatestUpdate(800L, "8"),
        )
        val oldest = entry("catalog.a", "oldest", "Oldest").copy(
            latestUpdate = CatalogLatestUpdate(700L, "7"),
        )
        val missing = entry("catalog.a", "missing", "Missing")
        val state = projectSemanticDiscoverState(
            homes = listOf(
                snapshot(
                    plugin = "catalog.a",
                    sections = listOf(
                        section(CatalogFeedKind.LATEST_UPDATES, listOf(oldest, missing, newest, middle)),
                    ),
                    refreshedAt = 99_999L,
                ),
            ),
            selectedContentType = ContentType.MANGA,
            loading = false,
            refreshing = false,
            refreshReport = null,
        )

        assertEquals(
            listOf("Newest", "Middle", "Oldest"),
            state.latestUpdates.map(DiscoverStoryItem::title),
        )
    }

    @Test
    fun topRatedRequiresScoreAndNormalizesDifferentScales() {
        val nineOfTen = entry("catalog.a", "nine", "Nine of ten", score = Score(9.0, 10.0))
        val ninetyFive = entry("catalog.a", "ninety-five", "Ninety five", score = Score(95.0, 100.0))
        val missing = entry("catalog.a", "missing", "Missing", score = null)
        val state = projectSemanticDiscoverState(
            homes = listOf(
                snapshot(
                    "catalog.a",
                    listOf(section(CatalogFeedKind.TOP_RATED, listOf(nineOfTen, missing, ninetyFive))),
                ),
            ),
            selectedContentType = ContentType.MANGA,
            loading = false,
            refreshing = false,
            refreshReport = null,
        )

        assertEquals(listOf("Ninety five", "Nine of ten"), state.topRated.map(DiscoverStoryItem::title))
    }

    @Test
    fun popularUsesPopularityRankBeforeStableFeedPositionFallback() {
        val fallbackFirst = entry("catalog.a", "fallback", "Fallback").copy(popularityRank = null)
        val rankTwo = entry("catalog.a", "two", "Rank two").copy(popularityRank = 2)
        val rankOne = entry("catalog.a", "one", "Rank one").copy(popularityRank = 1)
        val state = projectSemanticDiscoverState(
            homes = listOf(
                snapshot(
                    "catalog.a",
                    listOf(
                        section(
                            CatalogFeedKind.POPULAR,
                            listOf(rankTwo, rankOne, fallbackFirst),
                        ),
                    ),
                ),
            ),
            selectedContentType = ContentType.MANGA,
            loading = false,
            refreshing = false,
            refreshReport = null,
        )

        assertEquals(
            listOf("Rank one", "Rank two", "Fallback"),
            state.popular.map(DiscoverStoryItem::title),
        )
    }

    @Test
    fun canonicalStoryDedupesAndMetadataMergeIsDeterministic() {
        val storyId = StoryId("story:shared")
        val sparse = entry("catalog.a", "shared-a", "Shared A", storyId = storyId).copy(
            genres = setOf("Action", "Fantasy"),
            publicationStatus = PublicationStatus.ONGOING,
            score = Score(8.0, 10.0),
            latestUpdate = CatalogLatestUpdate(700L, "7"),
        )
        val artwork = entry("catalog.b", "shared-b", "Shared B", storyId = storyId).copy(
            coverUrl = "https://example.test/shared.jpg",
            score = Score(90.0, 100.0),
            latestUpdate = CatalogLatestUpdate(900L, "9"),
        )
        val first = snapshot(
            "catalog.a",
            listOf(
                section(CatalogFeedKind.POPULAR, listOf(sparse)),
                section(CatalogFeedKind.LATEST_UPDATES, listOf(sparse)),
                section(CatalogFeedKind.TOP_RATED, listOf(sparse)),
            ),
        )
        val second = snapshot(
            "catalog.b",
            listOf(
                section(CatalogFeedKind.POPULAR, listOf(artwork)),
                section(CatalogFeedKind.LATEST_UPDATES, listOf(artwork)),
                section(CatalogFeedKind.TOP_RATED, listOf(artwork)),
            ),
        )

        val forward = projectSemanticDiscoverState(
            listOf(first, second),
            ContentType.MANGA,
            loading = false,
            refreshing = false,
            refreshReport = null,
        )
        val reversed = projectSemanticDiscoverState(
            listOf(second, first),
            ContentType.MANGA,
            loading = false,
            refreshing = false,
            refreshReport = null,
        )

        assertEquals(forward.popular, reversed.popular)
        assertEquals(forward.latestUpdates, reversed.latestUpdates)
        assertEquals(forward.topRated, reversed.topRated)
        assertEquals(1, forward.popular.size)
        assertEquals("https://example.test/shared.jpg", forward.popular.single().coverUrl)
        assertEquals(listOf("Action", "Fantasy"), forward.popular.single().genres)
        assertEquals(PublicationStatus.ONGOING, forward.popular.single().publicationStatus)
        assertEquals(CatalogLatestUpdate(900L, "9"), forward.popular.single().latestUpdate)
        assertEquals(Score(90.0, 100.0), forward.popular.single().score)
    }

    @Test
    fun partialSemanticFeedsStayEmptyInsteadOfFabricatingFallbackMeaning() {
        val rated = entry("catalog.a", "rated", "Rated")
        val state = projectSemanticDiscoverState(
            homes = listOf(
                snapshot(
                    "catalog.a",
                    listOf(section(CatalogFeedKind.TOP_RATED, listOf(rated))),
                ),
            ),
            selectedContentType = ContentType.MANGA,
            loading = false,
            refreshing = false,
            refreshReport = null,
        )

        assertTrue(state.popular.isEmpty())
        assertTrue(state.latestUpdates.isEmpty())
        assertEquals(listOf("Rated"), state.topRated.map(DiscoverStoryItem::title))
    }

    private fun entries(
        prefix: String,
        count: Int,
        transform: CatalogEntry.(Int) -> CatalogEntry = { this },
    ): List<CatalogEntry> = (0 until count).map { index ->
        entry("catalog.a", "$prefix-$index", "$prefix $index")
            .copy(popularityRank = (index + 1).toLong())
            .transform(index)
    }

    private fun section(kind: CatalogFeedKind, items: List<CatalogEntry>) = CatalogHomeSection(
        sourceId = "section-${kind.name.lowercase()}-${items.firstOrNull()?.sourceId.orEmpty()}",
        title = kind.name,
        items = items,
        kind = kind,
    )

    private fun snapshot(
        plugin: String,
        sections: List<CatalogHomeSection>,
        refreshedAt: Long = 100L,
    ) = CatalogHomeSnapshot(
        pluginId = PluginId(plugin),
        pluginVersion = "1.0.0",
        refreshedAtEpochMillis = refreshedAt,
        sections = sections,
    )

    private fun entry(
        plugin: String,
        source: String,
        title: String,
        contentType: ContentType = ContentType.MANGA,
        score: Score? = Score(8.0, 10.0),
        storyId: StoryId = StoryId("story:$plugin:$source"),
    ) = CatalogEntry(
        storyId = storyId,
        pluginId = PluginId(plugin),
        sourceId = source,
        title = title,
        contentType = contentType,
        score = score,
    )
}
