package app.openstory.catalog.domain.validation

import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.requireAlignedCover
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection

object CatalogPublicationValidator {
    fun requireValidPublishedCards(cards: List<DiscoverCard>) {
        requireValidation(
            cards.size <= CatalogSectionCaps.MAX_DISCOVER_MEMBERSHIPS,
            "cards",
            CatalogValidationReason.OVER_LIMIT,
        )
        cards.forEach(::validateSummary)
        cards.groupBy { it.sectionKind }.forEach { (kind, sectionCards) ->
            requireValidation(
                sectionCards.size <= CatalogSectionCaps.cap(kind),
                "cards[$kind]",
                CatalogValidationReason.OVER_LIMIT,
            )
            val positions = sectionCards.map { it.itemPosition }.sorted()
            requireValidation(
                positions == positions.indices.toList(),
                "cards[$kind].itemPosition",
                CatalogValidationReason.INVARIANT_VIOLATION,
            )
            requireValidation(
                sectionCards.map { it.ref.storyId }.distinct().size == sectionCards.size,
                "cards[$kind].storyId",
                CatalogValidationReason.INVARIANT_VIOLATION,
            )
        }
    }

    fun requireValidStoryDetail(
        summary: StorySummaryProjection,
        detail: StoryRichDetailProjection,
    ) {
        validateSummary(summary)
        detail.description?.let {
            validateUtf8("description", it, CatalogInputLimits.DESCRIPTION_UTF8_BYTES)
        }
        validateChildren(
            "authors",
            detail.authors,
            CatalogInputLimits.AUTHORS,
            CatalogInputLimits.PERSON_UNICODE_SCALARS,
        )
        validateChildren(
            "artists",
            detail.artists,
            CatalogInputLimits.ARTISTS,
            CatalogInputLimits.PERSON_UNICODE_SCALARS,
        )
        validateChildren(
            "genres",
            detail.genres,
            CatalogInputLimits.GENRES,
            CatalogInputLimits.GENRE_UNICODE_SCALARS,
        )
        detail.publicationStatus?.let {
            validateScalars("publicationStatus", it, CatalogInputLimits.STATUS_UNICODE_SCALARS)
        }
        detail.language?.let {
            validateScalars("language", it, CatalogInputLimits.LANGUAGE_UNICODE_SCALARS)
        }
    }

    private fun validateSummary(card: DiscoverCard) {
        validateSummary(
            StorySummaryProjection(
                ref = card.ref,
                title = card.title,
                contentType = card.contentType,
                sourceVersion = card.sourceVersion,
                coverLocator = card.coverLocator,
                coverAssetKey = card.coverAssetKey,
                rating = card.rating,
                publicationStatusSummary = card.publicationStatusSummary,
                latestUpdateEpochMs = card.latestUpdateEpochMs,
            ),
        )
    }

    private fun validateSummary(summary: StorySummaryProjection) {
        validateScalars("title", summary.title, CatalogInputLimits.TITLE_UNICODE_SCALARS)
        validateUtf8(
            "sourceVersion",
            summary.sourceVersion,
            CatalogInputLimits.SOURCE_VERSION_UTF8_BYTES,
            true,
        )
        summary.publicationStatusSummary?.let {
            validateScalars("publicationStatusSummary", it, CatalogInputLimits.STATUS_UNICODE_SCALARS)
        }
        summary.latestUpdateEpochMs?.let {
            requireValidation(it >= 0, "latestUpdateEpochMs", CatalogValidationReason.MALFORMED)
        }
        summary.rating?.let(::validateRating)
        validateLocator(summary.coverLocator)
        validate("coverAssetKey", CatalogValidationReason.INVARIANT_VIOLATION) {
            requireAlignedCover(summary.ref, summary.coverLocator, summary.coverAssetKey)
        }
    }

    private fun validateRating(rating: CatalogRating) {
        requireValidation(
            rating.value.isFinite() && rating.scale.isFinite() &&
                rating.scale > 0.0 && rating.value >= 0.0 && rating.value <= rating.scale,
            "rating",
            CatalogValidationReason.MALFORMED,
        )
    }

    private fun validateLocator(locator: CoverLocator?) {
        when (locator) {
            null -> Unit
            is CoverLocator.TrustedLocalResource -> {
                validateUtf8(
                    "cover.logicalAssetId",
                    locator.logicalAssetId,
                    CatalogInputLimits.LOCAL_ASSET_ID_UTF8_BYTES,
                    true,
                )
                validateUtf8(
                    "cover.assetVersion",
                    locator.assetVersion,
                    CatalogInputLimits.LOCAL_ASSET_VERSION_UTF8_BYTES,
                    true,
                )
                validate("cover.revision", CatalogValidationReason.INVARIANT_VIOLATION) {
                    CoverRevisionV1.local(locator.logicalAssetId, locator.assetVersion)
                }
            }
            is CoverLocator.RemoteHttps -> Unit
        }
    }

    private fun validateChildren(
        field: String,
        values: List<String>,
        maximumItems: Int,
        maximumScalars: Int,
    ) {
        requireValidation(values.size <= maximumItems, field, CatalogValidationReason.OVER_LIMIT)
        values.forEachIndexed { index, value ->
            validateScalars("$field[$index]", value, maximumScalars)
        }
    }
}
