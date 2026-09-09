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

internal class LocalSeedCatalogSource(
    private val catalogSourceKey: CatalogSourceKey,
) : CatalogAcquisitionSource {
    override suspend fun acquireDiscover(mediaType: CatalogMediaType): DiscoverAcquisition =
        debugStories.getValue(mediaType).toDiscoverAcquisition()

    override suspend fun acquireStoryDetail(ref: StorySourceRef): StoryDetailAcquisition {
        require(ref.catalogSourceKey == catalogSourceKey)
        return debugStories.values.flatten()
            .single { it.sourceStoryId == ref.sourceStoryId }
            .toStoryDetail()
    }
}

private data class DebugStory(
    val sourceStoryId: String,
    val title: String,
    val contentType: CatalogMediaType,
    val logicalAssetId: String,
    val rating: Double,
    val latestUpdateEpochMs: Long,
)

private fun List<DebugStory>.toDiscoverAcquisition() = DiscoverAcquisition(
    sections = listOf(
        DiscoverAcquisitionSection(CatalogSectionKind.POPULAR, take(5).map(DebugStory::toItem)),
        DiscoverAcquisitionSection(CatalogSectionKind.LATEST_UPDATES, map(DebugStory::toItem)),
        DiscoverAcquisitionSection(
            CatalogSectionKind.TOP_RATED,
            sortedByDescending(DebugStory::rating).take(5).map(DebugStory::toItem),
        ),
    ),
)

private fun DebugStory.toItem() = DiscoverAcquisitionItem(
    sourceStoryId = sourceStoryId,
    title = title,
    contentType = contentType,
    cover = AcquisitionCoverInput.TrustedLocal(logicalAssetId, ASSET_VERSION),
    rating = CatalogRating(rating, 10.0),
    publicationStatusSummary = "Ongoing",
    latestUpdateEpochMs = latestUpdateEpochMs,
)

private fun DebugStory.toStoryDetail() = StoryDetailAcquisition(
    sourceStoryId = sourceStoryId,
    title = title,
    contentType = contentType,
    cover = AcquisitionCoverInput.TrustedLocal(logicalAssetId, ASSET_VERSION),
    rating = CatalogRating(rating, 10.0),
    publicationStatusSummary = "Ongoing",
    latestUpdateEpochMs = latestUpdateEpochMs,
    description = "A deterministic local story used to exercise the complete Catalog import path.",
    authors = listOf("Hikari Fixture Team"),
    artists = listOf("Hikari Studio"),
    genres = listOf("Adventure", "Drama"),
    publicationStatus = "Ongoing",
    language = if (contentType == CatalogMediaType.MANGA) "Japanese" else "English",
)

private val debugStories = mapOf(
    CatalogMediaType.MANGA to debugStories(
        mediaType = CatalogMediaType.MANGA,
        idPrefix = "manga",
        coverPrefix = "debug:manga",
        titles = listOf(
            "Kintsugi Days",
            "Night Market Atlas",
            "The Glass Shogun",
            "Paper Lantern Signal",
            "Moss and Meteor",
            "Clockwork Shrine",
            "Saltwind Courier",
            "Neon Rain Garden",
            "Dawn Over Kameoka",
        ),
    ),
    CatalogMediaType.LIGHT_NOVEL to debugStories(
        mediaType = CatalogMediaType.LIGHT_NOVEL,
        idPrefix = "light-novel",
        coverPrefix = "debug:light-novel",
        titles = listOf(
            "The Archive at World's End",
            "A Map for Tomorrow",
            "Tea with the Last Dragon",
            "Signal from the Moon Library",
            "The Quiet Alchemist",
            "Borrowed Stars",
            "Courier of the Inland Sea",
            "Axiom of Falling Snow",
            "The Ninth Bell",
        ),
    ),
)

private fun debugStories(
    mediaType: CatalogMediaType,
    idPrefix: String,
    coverPrefix: String,
    titles: List<String>,
): List<DebugStory> = titles.mapIndexed { index, title ->
    DebugStory(
        sourceStoryId = if (index == 0) "$idPrefix-夜-01" else "$idPrefix-${index + 1}",
        title = title,
        contentType = mediaType,
        logicalAssetId = "$coverPrefix:cover-${if (index % 2 == 0) "a" else "b"}",
        rating = 7.1 + (index * 0.3),
        latestUpdateEpochMs = 1_780_000_000_000L + (index * 86_400_000L),
    )
}

private const val ASSET_VERSION = "1"
