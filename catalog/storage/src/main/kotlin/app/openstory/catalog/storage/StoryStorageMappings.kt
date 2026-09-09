package app.openstory.catalog.storage

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevision
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.storage.story.StoryArtistEntity
import app.openstory.catalog.storage.story.StoryAuthorEntity
import app.openstory.catalog.storage.story.StoryDetailRecord
import app.openstory.catalog.storage.story.StoryDetailObservationRow
import app.openstory.catalog.storage.story.StoryGenreEntity
import app.openstory.catalog.storage.story.StorySourceSummaryEntity
import app.openstory.common.id.StoryId

internal fun StorySummaryProjection.toSummaryEntity(lastSeenEpochMs: Long): StorySourceSummaryEntity {
    val cover = coverColumns()
    return StorySourceSummaryEntity(
        storyId = ref.storyId.value,
        sourceKey = ref.catalogSourceKey.value,
        sourceVersion = sourceVersion,
        title = title,
        contentType = contentType.name,
        coverLocatorType = cover.type,
        coverLocatorValue = cover.value,
        coverLocatorAux = cover.aux,
        coverRevision = cover.revision,
        ratingValue = rating?.value,
        ratingScale = rating?.scale,
        publicationStatusSummary = publicationStatusSummary,
        latestUpdateEpochMs = latestUpdateEpochMs,
        lastSeenEpochMs = lastSeenEpochMs,
    )
}

internal fun StoryDetailRecord.toDomainProjection(): StoryDetailProjection {
    val ref = StorySourceRef(
        storyId = StoryId(row.storyId),
        catalogSourceKey = CatalogSourceKey(row.sourceKey),
        sourceStoryId = row.sourceStoryId,
    )
    val cover = coverFromColumns(
        ref = ref,
        type = row.summaryCoverLocatorType,
        value = row.summaryCoverLocatorValue,
        aux = row.summaryCoverLocatorAux,
        revisionValue = row.summaryCoverRevision,
    )
    val summary = StorySummaryProjection(
        ref = ref,
        title = row.summaryTitle,
        contentType = CatalogMediaType.valueOf(row.summaryContentType),
        sourceVersion = row.summarySourceVersion,
        coverLocator = cover?.first,
        coverAssetKey = cover?.second,
        rating = row.toRating(),
        publicationStatusSummary = row.summaryPublicationStatusSummary,
        latestUpdateEpochMs = row.summaryLatestUpdateEpochMs,
    )
    val detail = row.detailSourceVersion?.let {
        StoryRichDetailProjection(
            description = row.detailDescription,
            authors = authors.sortedBy(StoryAuthorEntity::position).map(StoryAuthorEntity::value),
            artists = artists.sortedBy(StoryArtistEntity::position).map(StoryArtistEntity::value),
            genres = genres.sortedBy(StoryGenreEntity::position).map(StoryGenreEntity::value),
            publicationStatus = row.detailPublicationStatus,
            language = row.detailLanguage,
        )
    }
    return StoryDetailProjection(
        ref = ref,
        summary = summary,
        detail = detail,
        detailProvenance = row.detailSourceVersion?.let { sourceVersion ->
            AcquisitionProvenance(
                catalogSourceKey = ref.catalogSourceKey,
                sourceVersion = sourceVersion,
                acquiredAtEpochMs = requireNotNull(row.detailFetchedAtEpochMs),
            )
        },
    )
}

private fun StorySummaryProjection.coverColumns(): StoryCoverColumns = when (val locator = coverLocator) {
    null -> StoryCoverColumns(null, null, null, null)
    is CoverLocator.TrustedLocalResource -> StoryCoverColumns(
        type = COVER_LOCAL,
        value = locator.logicalAssetId,
        aux = locator.assetVersion,
        revision = requireNotNull(coverAssetKey).coverRevision.value,
    )
    is CoverLocator.RemoteHttps -> StoryCoverColumns(
        type = COVER_REMOTE,
        value = locator.normalizedUri.value,
        aux = null,
        revision = requireNotNull(coverAssetKey).coverRevision.value,
    )
}

private fun StoryDetailObservationRow.toRating(): CatalogRating? = when {
    summaryRatingValue == null && summaryRatingScale == null -> null
    else -> CatalogRating(
        requireNotNull(summaryRatingValue),
        requireNotNull(summaryRatingScale),
    )
}

private fun coverFromColumns(
    ref: StorySourceRef,
    type: String?,
    value: String?,
    aux: String?,
    revisionValue: String?,
): Pair<CoverLocator, CoverAssetKey>? = when (type) {
    null -> {
        require(value == null && aux == null && revisionValue == null)
        null
    }
    COVER_LOCAL -> {
        val locator = CoverLocator.TrustedLocalResource(requireNotNull(value), requireNotNull(aux))
        locator to CoverAssetKey(ref.storyId, CoverRevision(requireNotNull(revisionValue)))
    }
    COVER_REMOTE -> {
        require(aux == null)
        val revision = CoverRevision(requireNotNull(revisionValue))
        val locator = CoverLocator.RemoteHttps(
            catalogSourceKey = ref.catalogSourceKey,
            normalizedUri = RemoteHttpsUriV1.parseAndNormalize(requireNotNull(value)),
            revision = revision,
        )
        locator to CoverAssetKey(ref.storyId, revision)
    }
    else -> error("Unknown cover locator type")
}

private data class StoryCoverColumns(
    val type: String?,
    val value: String?,
    val aux: String?,
    val revision: String?,
)

private const val COVER_LOCAL = "LOCAL"
private const val COVER_REMOTE = "REMOTE"
