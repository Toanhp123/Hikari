package app.openstory.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogEntryTest {
    @Test
    fun catalogMetadataIsRetainedWithoutFlatteningSourceValues() {
        val entry = CatalogEntry(
            id = CatalogEntryId("catalog:story-1"),
            catalogPluginId = PluginId("catalog.example"),
            externalStoryId = "story-1",
            sourceUrl = "https://catalog.example/story-1",
            title = "Example",
            aliases = linkedSetOf("Example Alias"),
            authors = linkedSetOf("Author A", "Author B"),
            description = "Description",
            genres = linkedSetOf("Fantasy", "Adventure"),
            contentType = ContentType.WEB_NOVEL,
            languageTags = linkedSetOf(LanguageTag("en")),
            coverReference = "https://catalog.example/cover.jpg",
            publicationStatus = "ONGOING",
            score = 8.4,
            scoreScale = 10.0,
            popularityRank = 12L,
            pluginVersion = "1.2.3",
            fetchedAtEpochMillis = 1_234L,
        )

        assertEquals("story-1", entry.externalStoryId)
        assertEquals(setOf("Example Alias"), entry.aliases)
        assertEquals(setOf("Author A", "Author B"), entry.authors)
        assertEquals(setOf("Fantasy", "Adventure"), entry.genres)
        assertEquals(ContentType.WEB_NOVEL, entry.contentType)
        assertEquals(setOf(LanguageTag("en")), entry.languageTags)
        assertEquals("ONGOING", entry.publicationStatus)
        assertEquals(12L, entry.popularityRank)
        assertEquals("1.2.3", entry.pluginVersion)
        assertEquals(1_234L, entry.fetchedAtEpochMillis)
    }

    @Test
    fun blankExternalStoryIdIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            catalogEntry(externalStoryId = " ")
        }
    }

    @Test
    fun blankPluginVersionIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            catalogEntry(pluginVersion = " ")
        }
    }

    @Test
    fun scoreAndScaleMustBeProvidedTogether() {
        assertFailsWith<IllegalArgumentException> {
            catalogEntry(score = 8.4, scoreScale = null)
        }
        assertFailsWith<IllegalArgumentException> {
            catalogEntry(score = null, scoreScale = 10.0)
        }
    }

    @Test
    fun snapshotRejectsBlankPluginVersion() {
        assertFailsWith<IllegalArgumentException> {
            CatalogSnapshot(
                pluginId = PluginId("catalog.example"),
                pluginVersion = " ",
                sections = emptyList(),
            )
        }
    }

    @Test
    fun snapshotRejectsDuplicateSectionIds() {
        val section = snapshotSection("popular")

        assertFailsWith<IllegalArgumentException> {
            CatalogSnapshot(
                pluginId = PluginId("catalog.example"),
                pluginVersion = "1.0.0",
                sections = listOf(section, section),
            )
        }
    }

    @Test
    fun snapshotRejectsDuplicateItemIdsWithinSection() {
        val item = snapshotItem("story-1")

        assertFailsWith<IllegalArgumentException> {
            CatalogSnapshotSection(
                sourceId = "popular",
                title = "Popular",
                items = listOf(item, item),
            )
        }
    }

    @Test
    fun snapshotRejectsBlankSectionAndItemIds() {
        assertFailsWith<IllegalArgumentException> {
            snapshotSection(" ")
        }
        assertFailsWith<IllegalArgumentException> {
            snapshotItem(" ")
        }
    }

    @Test
    fun snapshotRejectsInvalidScoreScalePairs() {
        assertFailsWith<IllegalArgumentException> {
            snapshotItem(
                sourceId = "story-1",
                score = 8.4,
                scoreScale = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            snapshotItem(
                sourceId = "story-1",
                score = 11.0,
                scoreScale = 10.0,
            )
        }
    }

    private fun catalogEntry(
        externalStoryId: String = "story-1",
        score: Double? = 8.4,
        scoreScale: Double? = 10.0,
        pluginVersion: String = "1.0.0",
    ): CatalogEntry =
        CatalogEntry(
            id = CatalogEntryId("catalog:story-1"),
            catalogPluginId = PluginId("catalog.example"),
            externalStoryId = externalStoryId,
            sourceUrl = null,
            title = "Example",
            aliases = emptySet(),
            authors = emptySet(),
            description = null,
            genres = emptySet(),
            contentType = ContentType.WEB_NOVEL,
            languageTags = emptySet(),
            coverReference = null,
            publicationStatus = null,
            score = score,
            scoreScale = scoreScale,
            popularityRank = null,
            pluginVersion = pluginVersion,
            fetchedAtEpochMillis = 1_000L,
        )

    private fun snapshotSection(
        sourceId: String,
    ): CatalogSnapshotSection =
        CatalogSnapshotSection(
            sourceId = sourceId,
            title = "Popular",
            items = listOf(snapshotItem("story-1")),
        )

    private fun snapshotItem(
        sourceId: String,
        score: Double? = 8.4,
        scoreScale: Double? = 10.0,
    ): CatalogSnapshotItem =
        CatalogSnapshotItem(
            sourceId = sourceId,
            title = "Example",
            contentType = ContentType.WEB_NOVEL,
            authors = listOf("Author"),
            coverReference = null,
            score = score,
            scoreScale = scoreScale,
        )
}
