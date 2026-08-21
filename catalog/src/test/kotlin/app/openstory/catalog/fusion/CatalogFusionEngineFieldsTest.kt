package app.openstory.catalog.fusion

import app.openstory.catalog.canonical.CanonicalFieldKey
import app.openstory.catalog.canonical.CanonicalSourcePreference
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.CatalogLatestUpdate
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.PublicationStatus
import app.openstory.catalog.model.Score
import app.openstory.catalog.model.Story
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogFusionEngineFieldsTest {
    private val storyId = StoryId("story:fields")

    @Test
    fun primaryScalarsFallbackAndProvenanceUseActualSource() {
        val primary = source("provider.a", description = null, sourceUrl = null, popularityRank = null)
        val fallback = source(
            "provider.b",
            description = "Fallback description",
            sourceUrl = "https://example.test/b",
            popularityRank = 7,
        )
        val candidate = fuse(listOf(primary, fallback), primary.sourceKey)

        assertEquals(primary.record.entry.title, candidate.metadata.title)
        assertEquals("Fallback description", candidate.metadata.description)
        assertEquals("https://example.test/b", candidate.metadata.sourceUrl)
        assertEquals(7L, candidate.metadata.popularityRank)
        assertEquals(
            listOf(fallback.sourceKey),
            candidate.provenance.getValue(CanonicalFieldKey.DESCRIPTION).contributors.map { it.sourceKey },
        )
    }

    @Test
    fun coverPrefersPrimaryThenBestQualifiedFallbackIndependentOfInputOrder() {
        val primary = source("provider.a", coverUrl = null)
        val fallback = source("provider.b", coverUrl = "https://example.test/b.jpg")
        val first = fuse(listOf(primary, fallback), primary.sourceKey)
        val second = fuse(listOf(fallback, primary), primary.sourceKey)
        assertEquals("https://example.test/b.jpg", first.metadata.coverUrl)
        assertEquals(first.metadata.coverUrl, second.metadata.coverUrl)
    }

    @Test
    fun collectionsUseNormalizedDeterministicUnionWithoutFuzzyAuthorCollapse() {
        val a = source(
            "provider.a",
            title = "Canonical",
            aliases = setOf(" Alias  ", "canonical"),
            authors = setOf("John Smith"),
            genres = setOf(" Drama "),
            languageTags = setOf("EN"),
        )
        val b = source(
            "provider.b",
            title = "Other title",
            aliases = setOf("alias", "Second"),
            authors = setOf("Jon Smith"),
            genres = setOf("drama", "Action"),
            languageTags = setOf(" en ", "ja"),
        )
        val candidate = fuse(listOf(b, a), a.sourceKey)

        assertEquals(listOf("Alias", "Other title", "Second"), candidate.metadata.aliases)
        assertEquals(listOf("John Smith", "Jon Smith"), candidate.metadata.authors)
        assertEquals(listOf("Action", "Drama"), candidate.metadata.genres)
        assertEquals(listOf("EN", "ja"), candidate.metadata.languageTags)
        assertEquals(2, candidate.provenance.getValue(CanonicalFieldKey.AUTHORS).contributors.size)
    }

    @Test
    fun publicationStatusUsesFullThenFreshnessThenPrimaryThenStableKey() {
        val primarySummary = source(
            "provider.a",
            publicationStatus = PublicationStatus.ONGOING,
            full = false,
        )
        val full = source(
            "provider.b",
            publicationStatus = PublicationStatus.COMPLETED,
            full = true,
            freshness = CatalogSourceFreshness.STALE,
        )
        assertEquals(PublicationStatus.COMPLETED, fuse(listOf(primarySummary, full), primarySummary.sourceKey).metadata.publicationStatus)

        val stalePrimary = source(
            "provider.a",
            publicationStatus = PublicationStatus.ONGOING,
            freshness = CatalogSourceFreshness.STALE,
        )
        val fresh = source("provider.b", publicationStatus = PublicationStatus.HIATUS)
        assertEquals(PublicationStatus.HIATUS, fuse(listOf(stalePrimary, fresh), stalePrimary.sourceKey).metadata.publicationStatus)
    }

    @Test
    fun latestUpdateKeepsTimestampAndLabelFromTheSameSource() {
        val a = source("provider.a", latestUpdate = CatalogLatestUpdate(200L, "Ch. 20"))
        val b = source("provider.b", latestUpdate = CatalogLatestUpdate(100L, "Vol. 2 Ch. 10"))
        val candidate = fuse(listOf(a, b), a.sourceKey)

        assertEquals(CatalogLatestUpdate(200L, "Ch. 20"), candidate.metadata.latestUpdate)
        assertEquals(
            listOf(a.sourceKey),
            candidate.provenance.getValue(CanonicalFieldKey.LATEST_UPDATE).contributors.map { it.sourceKey },
        )
    }

    @Test
    fun latestUpdateTieUsesEffectivePrimaryAndLabelStaysOpaque() {
        val a = source("provider.a", latestUpdate = CatalogLatestUpdate(200L, "Vol. 2 Ch. 10"))
        val b = source("provider.b", latestUpdate = CatalogLatestUpdate(200L, "Episode 10"))
        assertEquals(
            CatalogLatestUpdate(200L, "Episode 10"),
            fuse(listOf(a, b), b.sourceKey).metadata.latestUpdate,
        )
    }

    @Test
    fun scoreNormalizesAndUsesUnweightedMeanAcrossUsableSources() {
        val sources = listOf(
            source("provider.a", score = Score(8.0, 10.0)),
            source("provider.b", score = Score(4.0, 5.0)),
            source("provider.c", score = Score(90.0, 100.0)),
            source("provider.d", score = Score(10.0, 10.0), usability = CatalogSourceUsability.UNAVAILABLE),
        )
        val score = fuse(sources, sources.first().sourceKey).metadata.score!!
        assertTrue(kotlin.math.abs(score.normalizedValue - (5.0 / 6.0)) < 1e-12)
        assertEquals(3, score.contributorCount)
    }

    private fun fuse(sources: List<FusionSource>, primary: SourceKey) = CatalogFusionEngine().fuse(
        FusionInput(
            story = Story(storyId, ContentType.MANGA),
            sources = sources,
            previousGeneration = null,
            preference = CanonicalSourcePreference(
                storyId,
                CanonicalSourcePreferenceMode.PINNED,
                primary,
                1,
            ),
            evaluatedAtEpochMillis = 500L,
        ),
    )

    private fun source(
        plugin: String,
        title: String = "Canonical",
        aliases: Set<String> = emptySet(),
        authors: Set<String> = emptySet(),
        description: String? = "Description",
        genres: Set<String> = emptySet(),
        languageTags: Set<String> = emptySet(),
        coverUrl: String? = "https://example.test/default.jpg",
        sourceUrl: String? = "https://example.test/default",
        popularityRank: Long? = 1,
        publicationStatus: PublicationStatus? = PublicationStatus.ONGOING,
        latestUpdate: CatalogLatestUpdate? = null,
        score: Score? = null,
        full: Boolean = true,
        usability: CatalogSourceUsability = CatalogSourceUsability.ACTIVE,
        freshness: CatalogSourceFreshness = CatalogSourceFreshness.FRESH,
    ): FusionSource {
        val key = SourceKey(PluginId(plugin), "source")
        val entry = CatalogEntry(
            storyId = storyId,
            pluginId = key.pluginId,
            sourceId = key.sourceId,
            title = title,
            aliases = aliases,
            authors = authors,
            description = description,
            genres = genres,
            contentType = ContentType.MANGA,
            languageTags = languageTags,
            coverUrl = coverUrl,
            sourceUrl = sourceUrl,
            score = score,
            popularityRank = popularityRank,
            publicationStatus = publicationStatus,
            latestUpdate = latestUpdate,
        )
        return FusionSource(
            CatalogSourceRecord(
                key,
                storyId,
                entry,
                CatalogMetadataStamp("1", 10L),
                if (full) CatalogMetadataStamp("1", 20L) else null,
                "identity:$plugin",
                "fusion:$plugin",
            ),
            usability,
            freshness,
        )
    }
}
