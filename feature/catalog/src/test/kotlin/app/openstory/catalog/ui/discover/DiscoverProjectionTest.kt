package app.openstory.catalog.ui.discover

import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogHomeSection
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.Score
import app.openstory.catalog.ranking.CatalogRankContribution
import app.openstory.catalog.ranking.RankedCatalogStory
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoverProjectionTest {
    @Test
    fun featuredPrefersArtworkThenScoreThenStableIdentity() {
        val noArtwork = entry("plugin.a", "source-a", "A", score = 10.0)
        val laterIdentity = entry("plugin.b", "source-b", "B", coverUrl = "b.jpg", score = 8.0)
        val stableWinner = entry("plugin.a", "source-a", "C", coverUrl = "c.jpg", score = 8.0)

        assertEquals(
            stableWinner,
            selectFeatured(
                listOf(
                    ranked(noArtwork, 1.0),
                    ranked(laterIdentity, 0.8),
                    ranked(stableWinner, 0.8),
                ),
            ),
        )
    }

    @Test
    fun featuredFallsBackToHighestRankedEntryWithoutArtwork() {
        val lower = entry("plugin.a", "source-a", "Lower", score = 7.0)
        val higher = entry("plugin.b", "source-b", "Higher", score = 9.0)

        assertEquals(higher, selectFeatured(listOf(ranked(lower, 0.7), ranked(higher, 0.9))))
    }

    @Test
    fun featuredIgnoresBlankArtworkReferences() {
        val blankArtwork = entry("plugin.a", "source-a", "Blank", coverUrl = "  ")
        val usableArtwork = entry("plugin.b", "source-b", "Usable", coverUrl = "cover.jpg")

        assertEquals(
            usableArtwork,
            selectFeatured(listOf(ranked(blankArtwork, 1.0), ranked(usableArtwork, 0.8))),
        )
    }

    @Test
    fun combinedShelfPrefersArtworkThenScoreThenStableIdentity() {
        val plain = entry("plugin.a", "source-a", "Plain", score = 9.0)
        val artwork = entry("plugin.b", "source-b", "Artwork", coverUrl = "cover.jpg", score = 7.0)
        val state = projectDiscoverState(
            catalogs = emptyList(),
            rankedStories = listOf(rankedWithContributions(0.9, plain, artwork)),
        )

        assertEquals(artwork, state.shelves.single().entries.single())
    }

    @Test
    fun partialRefreshKeepsCachedShelvesVisible() {
        val cached = entry("plugin.a", "source-a", "Cached", coverUrl = "cached.jpg")
        val state = projectDiscoverState(
            catalogs = listOf(snapshot("plugin.a", "source-a", cached)),
            rankedStories = listOf(ranked(cached, 0.8)),
            refreshing = true,
            refreshReport = DiscoverRefreshReport(
                failed = mapOf(PluginId("plugin.b") to "catalog.offline"),
            ),
        )

        assertEquals("Cached", state.shelves.first().entries.single().title)
        assertEquals("catalog.offline", state.refreshReport?.failed?.get(PluginId("plugin.b")))
    }

    @Test
    fun sourceSectionsPreservePluginOrderAndIdentity() {
        val first = entry("plugin.b", "source-2", "Second plugin")
        val second = entry("plugin.a", "source-1", "First plugin")
        val state = projectDiscoverState(
            catalogs = listOf(
                snapshot("plugin.b", "source-2", first),
                snapshot("plugin.a", "source-1", second),
            ),
            rankedStories = emptyList(),
        )

        assertEquals(
            listOf("plugin.b:source-2", "plugin.a:source-1"),
            state.shelves.map(DiscoverShelf::key),
        )
    }

    @Test
    fun quickCategoriesUseExecutableSourceGroupsInRepositoryOrder() {
        val first = entry("plugin.b", "source-2", "Second plugin")
        val second = entry("plugin.a", "source-1", "First plugin")
        val state = projectDiscoverState(
            catalogs = listOf(
                snapshot("plugin.b", "source-2", first),
                snapshot("plugin.a", "source-1", second),
            ),
            rankedStories = emptyList(),
        )

        assertEquals(
            listOf("Trending" to PluginId("plugin.b"), "Trending" to PluginId("plugin.a")),
            state.quickCategories.map { it.label to it.pluginId },
        )
    }

    @Test
    fun selectedQuickCategoryProjectsOnlyItsOwnedSourceSection() {
        val trending = entry("plugin.a", "trending", "Trending story")
        val latest = entry("plugin.a", "latest", "Latest story")
        val catalog = CatalogHomeSnapshot(
            pluginId = PluginId("plugin.a"),
            pluginVersion = "1.0.0",
            refreshedAtEpochMillis = 100L,
            sections = listOf(
                CatalogHomeSection("trending", "Trending", listOf(trending)),
                CatalogHomeSection("latest", "Latest", listOf(latest)),
            ),
        )
        val state = projectDiscoverState(
            catalogs = listOf(catalog),
            rankedStories = emptyList(),
            selectedCatalogId = PluginId("plugin.a"),
            selectedSourceId = "latest",
        )

        assertEquals(listOf("plugin.a:latest"), state.shelves.map(DiscoverShelf::key))
    }

    @Test
    fun missingSelectedCatalogFallsBackToCombinedProjection() {
        val available = entry("plugin.a", "source-1", "Available")
        val state = projectDiscoverState(
            catalogs = listOf(snapshot("plugin.a", "source-1", available)),
            rankedStories = listOf(ranked(available, 0.8)),
            selectedCatalogId = PluginId("plugin.missing"),
        )

        assertEquals(null, state.selectedCatalogId)
        assertEquals("Across catalogs", state.shelves.first().title)
    }

    private fun ranked(entry: CatalogEntry, score: Double) = RankedCatalogStory(
        storyId = entry.storyId,
        orderingScore = score,
        contributions = listOf(CatalogRankContribution(entry, score, 1.0)),
    )

    private fun rankedWithContributions(score: Double, vararg entries: CatalogEntry) = RankedCatalogStory(
        storyId = entries.first().storyId,
        orderingScore = score,
        contributions = entries.map { CatalogRankContribution(it, score, 1.0) },
    )

    private fun snapshot(plugin: String, source: String, entry: CatalogEntry) = CatalogHomeSnapshot(
        pluginId = PluginId(plugin),
        pluginVersion = "1.0.0",
        refreshedAtEpochMillis = 100L,
        sections = listOf(CatalogHomeSection(source, "Trending", listOf(entry))),
    )

    private fun entry(
        plugin: String,
        source: String,
        title: String,
        coverUrl: String? = null,
        score: Double = 8.0,
    ) = CatalogEntry(
        storyId = StoryId("${plugin}_${source}_${title.lowercase().replace(' ', '_')}"),
        pluginId = PluginId(plugin),
        sourceId = source,
        title = title,
        contentType = ContentType.WEB_NOVEL,
        coverUrl = coverUrl,
        score = Score(score, 10.0),
    )
}
