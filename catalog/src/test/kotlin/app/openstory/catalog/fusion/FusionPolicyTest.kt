package app.openstory.catalog.fusion

import app.openstory.catalog.evidence.CatalogSourceRecord
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataStamp
import app.openstory.catalog.model.CatalogEntry
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

class FusionPolicyTest {
    @Test
    fun explicitQualityRanksMatchNormativeOrder() {
        assertTrue(CatalogSourceUsability.ACTIVE.rank() > CatalogSourceUsability.STALE.rank())
        assertTrue(CatalogSourceUsability.STALE.rank() > CatalogSourceUsability.TEMPORARILY_UNAVAILABLE.rank())
        assertTrue(CatalogSourceUsability.TEMPORARILY_UNAVAILABLE.rank() > CatalogSourceUsability.UNAVAILABLE.rank())
        assertTrue(CatalogSourceUsability.UNAVAILABLE.rank() > CatalogSourceUsability.RETIRED.rank())
        assertTrue(CatalogMetadataLevel.Full.rank() > CatalogMetadataLevel.Summary.rank())
        assertTrue(CatalogSourceFreshness.FRESH.rank() > CatalogSourceFreshness.STALE.rank())
        assertTrue(CatalogSourceFreshness.STALE.rank() > CatalogSourceFreshness.UNKNOWN.rank())
    }

    @Test
    fun primaryCoverageCountsExactlyNineOptionalPresentationFieldsAndNotTitle() {
        val empty = source(entry = entry(title = "Required title"))
        val fullCoverage = source(
            entry = entry(
                title = "Different required title",
                description = "Description",
                coverUrl = "https://example.test/cover.jpg",
                sourceUrl = "https://example.test/work",
                authors = setOf("Author"),
                aliases = setOf("Alias"),
                genres = setOf("Drama"),
                publicationStatus = PublicationStatus.ONGOING,
                latestUpdate = CatalogLatestUpdate(10L, "Ch. 1"),
                score = Score(8.0, 10.0),
            ),
        )

        assertEquals(0, empty.primaryQuality().primaryFieldCoverage)
        assertEquals(9, fullCoverage.primaryQuality().primaryFieldCoverage)
    }

    @Test
    fun stableSourceKeyIsOnlyFinalTieBreakAfterObjectiveQuality() {
        val a = source(pluginId = "provider.a")
        val b = source(pluginId = "provider.b")
        val lowerQuality = a.copy(freshness = CatalogSourceFreshness.STALE)

        assertTrue(primaryQualityComparator.compare(a, b) < 0)
        assertTrue(primaryQualityComparator.compare(b, lowerQuality) < 0)
    }

    @Test
    fun fusionSourceDoesNotExposeProviderConfidenceOrWeight() {
        val names = FusionSource::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(names.any { "confidence" in it })
        assertFalse(names.any { "weight" in it || "qualityscore" in it })
    }

    private fun source(
        pluginId: String = "provider.a",
        entry: CatalogEntry = entry(),
        usability: CatalogSourceUsability = CatalogSourceUsability.ACTIVE,
        freshness: CatalogSourceFreshness = CatalogSourceFreshness.FRESH,
        full: Boolean = true,
    ): FusionSource {
        val adjusted = entry.copy(pluginId = PluginId(pluginId), sourceId = "source")
        return FusionSource(
            record = CatalogSourceRecord(
                key = SourceKey(adjusted.pluginId, adjusted.sourceId),
                storyId = adjusted.storyId,
                entry = adjusted,
                summary = CatalogMetadataStamp("1.0.0", 1L),
                full = if (full) CatalogMetadataStamp("1.0.0", 2L) else null,
                identityFingerprint = "identity",
                fusionFingerprint = "fusion:$pluginId",
            ),
            usability = usability,
            freshness = freshness,
        )
    }

    private fun entry(
        title: String = "Title",
        description: String? = null,
        coverUrl: String? = null,
        sourceUrl: String? = null,
        authors: Set<String> = emptySet(),
        aliases: Set<String> = emptySet(),
        genres: Set<String> = emptySet(),
        publicationStatus: PublicationStatus? = null,
        latestUpdate: CatalogLatestUpdate? = null,
        score: Score? = null,
    ) = CatalogEntry(
        storyId = StoryId("story:1"),
        pluginId = PluginId("provider.a"),
        sourceId = "source",
        title = title,
        aliases = aliases,
        authors = authors,
        description = description,
        genres = genres,
        contentType = ContentType.MANGA,
        coverUrl = coverUrl,
        sourceUrl = sourceUrl,
        score = score,
        publicationStatus = publicationStatus,
        latestUpdate = latestUpdate,
    )
}
