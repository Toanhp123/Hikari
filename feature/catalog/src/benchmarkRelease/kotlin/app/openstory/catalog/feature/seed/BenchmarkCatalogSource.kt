package app.openstory.catalog.feature.seed

import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.source.CatalogAcquisitionSource
import app.openstory.catalog.domain.source.DiscoverAcquisition
import app.openstory.catalog.domain.source.DiscoverAcquisitionItem
import app.openstory.catalog.domain.source.DiscoverAcquisitionSection
import app.openstory.catalog.domain.source.StoryDetailAcquisition

internal class BenchmarkCatalogSource(
    private val catalogSourceKey: CatalogSourceKey,
) : CatalogAcquisitionSource {
    override suspend fun acquireDiscover(mediaType: CatalogMediaType): DiscoverAcquisition =
        benchmarkStories.getValue(mediaType).toDiscoverAcquisition()

    override suspend fun acquireStoryDetail(ref: StorySourceRef): StoryDetailAcquisition {
        require(ref.catalogSourceKey == catalogSourceKey)
        return benchmarkStories.values.flatten()
            .single { it.sourceStoryId == ref.sourceStoryId }
            .toStoryDetail()
    }
}

private data class BenchmarkStory(
    val sourceStoryId: String,
    val title: String,
    val contentType: CatalogMediaType,
    val logicalAssetId: String,
    val rating: Double,
    val latestUpdateEpochMs: Long,
)

private fun List<BenchmarkStory>.toDiscoverAcquisition() = DiscoverAcquisition(
    sections = listOf(
        DiscoverAcquisitionSection(CatalogSectionKind.POPULAR, take(5).map(BenchmarkStory::toItem)),
        DiscoverAcquisitionSection(CatalogSectionKind.LATEST_UPDATES, map(BenchmarkStory::toItem)),
        DiscoverAcquisitionSection(
            CatalogSectionKind.TOP_RATED,
            sortedByDescending(BenchmarkStory::rating).take(5).map(BenchmarkStory::toItem),
        ),
    ),
)

private fun BenchmarkStory.toItem() = DiscoverAcquisitionItem(
    sourceStoryId = sourceStoryId,
    title = title,
    contentType = contentType,
    cover = AcquisitionCoverInput.TrustedLocal(logicalAssetId, ASSET_VERSION),
    rating = CatalogRating(rating, 10.0),
    publicationStatusSummary = "Ongoing",
    latestUpdateEpochMs = latestUpdateEpochMs,
)

private fun BenchmarkStory.toStoryDetail() = StoryDetailAcquisition(
    sourceStoryId = sourceStoryId,
    title = title,
    contentType = contentType,
    cover = AcquisitionCoverInput.TrustedLocal(logicalAssetId, ASSET_VERSION),
    rating = CatalogRating(rating, 10.0),
    publicationStatusSummary = "Ongoing",
    latestUpdateEpochMs = latestUpdateEpochMs,
    description = "A deterministic benchmark story imported through the production Catalog pipeline.",
    authors = listOf("OpenStory Bench Lab"),
    artists = listOf("Hikari Studio"),
    genres = listOf("Adventure", "Mystery"),
    publicationStatus = "Ongoing",
    language = if (contentType == CatalogMediaType.MANGA) "Japanese" else "English",
)

private val benchmarkStories = mapOf(
    CatalogMediaType.MANGA to benchmarkStories(
        mediaType = CatalogMediaType.MANGA,
        idPrefix = "benchmark-manga",
        coverPrefix = "benchmark:manga",
        titles = listOf(
            "Aster Gate",
            "Blue Hour Ronin",
            "City of Small Suns",
            "Dream Cartographer",
            "Echoes in Cedar",
            "Foxfire Dispatch",
            "Garden of Comets",
            "Harbor of Ink",
            "Iron Cicada",
        ),
    ),
    CatalogMediaType.LIGHT_NOVEL to benchmarkStories(
        mediaType = CatalogMediaType.LIGHT_NOVEL,
        idPrefix = "benchmark-light-novel",
        coverPrefix = "benchmark:light-novel",
        titles = listOf(
            "The Library Beyond Rain",
            "Tomorrow's Familiar",
            "A Minor God of Trains",
            "Winter Protocol",
            "The Orchard Between Worlds",
            "Letters from Orbit",
            "The Alchemist's Second Name",
            "One Thousand Quiet Doors",
            "The Violetless Crown",
        ),
    ),
)

private fun benchmarkStories(
    mediaType: CatalogMediaType,
    idPrefix: String,
    coverPrefix: String,
    titles: List<String>,
): List<BenchmarkStory> = titles.mapIndexed { index, title ->
    BenchmarkStory(
        sourceStoryId = if (index == 0) "$idPrefix-星-01" else "$idPrefix-${index + 1}",
        title = title,
        contentType = mediaType,
        logicalAssetId = "$coverPrefix:cover-${if (index % 2 == 0) "a" else "b"}",
        rating = 7.2 + (index * 0.3),
        latestUpdateEpochMs = 1_790_000_000_000L + (index * 86_400_000L),
    )
}

private const val ASSET_VERSION = "1"
