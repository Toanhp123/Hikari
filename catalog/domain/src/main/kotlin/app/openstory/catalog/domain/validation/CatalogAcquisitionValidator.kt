package app.openstory.catalog.domain.validation

import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.source.CatalogPage
import app.openstory.catalog.domain.source.CatalogSectionDescriptor
import app.openstory.catalog.domain.source.CatalogTransientStory
import app.openstory.catalog.domain.source.DiscoverAcquisition
import app.openstory.catalog.domain.source.DiscoverAcquisitionItem
import app.openstory.catalog.domain.source.StoryDetailAcquisition

object CatalogAcquisitionValidator {
    fun requireValidDiscover(acquisition: DiscoverAcquisition) {
        requireValidation(
            acquisition.sections.size <= CatalogSectionCaps.MAX_SECTIONS,
            "sections",
            CatalogValidationReason.OVER_LIMIT,
        )
        requireValidation(
            acquisition.sections.map { it.kind }.distinct().size == acquisition.sections.size,
            "sections",
            CatalogValidationReason.INVARIANT_VIOLATION,
        )
        acquisition.sections.forEach { section ->
            requireValidation(
                section.items.size <= CatalogSectionCaps.cap(section.kind),
                "sections[${section.kind}].items",
                CatalogValidationReason.OVER_LIMIT,
            )
            requireValidation(
                section.items.map { it.sourceStoryId }.distinct().size == section.items.size,
                "sections[${section.kind}].sourceStoryId",
                CatalogValidationReason.INVARIANT_VIOLATION,
            )
            section.items.forEach(::validateItem)
        }
        requireValidation(
            acquisition.sections.sumOf { it.items.size } <= CatalogSectionCaps.MAX_DISCOVER_MEMBERSHIPS,
            "sections",
            CatalogValidationReason.OVER_LIMIT,
        )
    }

    fun requireValidStoryDetail(
        ref: StorySourceRef,
        acquisition: StoryDetailAcquisition,
    ) {
        requireValidation(
            acquisition.sourceStoryId == ref.sourceStoryId,
            "sourceStoryId",
            CatalogValidationReason.AUTHORITY_MISMATCH,
        )
        validateItem(acquisition.asDiscoverItem())
        acquisition.description?.let {
            validateUtf8("description", it, CatalogInputLimits.DESCRIPTION_UTF8_BYTES)
        }
        validateChildren(
            "authors",
            acquisition.authors,
            CatalogInputLimits.AUTHORS,
            CatalogInputLimits.PERSON_UNICODE_SCALARS,
        )
        validateChildren(
            "artists",
            acquisition.artists,
            CatalogInputLimits.ARTISTS,
            CatalogInputLimits.PERSON_UNICODE_SCALARS,
        )
        validateChildren(
            "genres",
            acquisition.genres,
            CatalogInputLimits.GENRES,
            CatalogInputLimits.GENRE_UNICODE_SCALARS,
        )
        acquisition.publicationStatus?.let {
            validateScalars("publicationStatus", it, CatalogInputLimits.STATUS_UNICODE_SCALARS)
        }
        acquisition.language?.let {
            validateScalars("language", it, CatalogInputLimits.LANGUAGE_UNICODE_SCALARS)
        }
        validateStoryMetadata(acquisition.alternateTitles, acquisition.catalogLanguageTags)
    }

    fun requireValidSearchRequest(query: String, pageSize: Int, continuation: String?) =
        CatalogTransientContractValidator.requireValidSearchRequest(query, pageSize, continuation)

    fun requireValidSectionRequest(
        section: CatalogSectionDescriptor,
        pageSize: Int,
        continuation: String?,
    ) = CatalogTransientContractValidator.requireValidSectionRequest(section, pageSize, continuation)

    fun requireValidSimilarRequest(maximumItems: Int) =
        CatalogTransientContractValidator.requireValidSimilarRequest(maximumItems)

    fun requireValidSectionDescriptors(
        descriptors: List<CatalogSectionDescriptor>,
    ) = CatalogTransientContractValidator.requireValidSectionDescriptors(descriptors)

    fun requireValidTransientPage(
        sourceKey: CatalogSourceKey,
        mediaType: CatalogMediaType,
        maximumItems: Int,
        page: CatalogPage<CatalogTransientStory>,
    ) = CatalogTransientContractValidator.requireValidTransientPage(sourceKey, mediaType, maximumItems, page)

    private fun validateItem(item: DiscoverAcquisitionItem) {
        validateUtf8(
            "sourceStoryId",
            item.sourceStoryId,
            CatalogInputLimits.SOURCE_STORY_ID_UTF8_BYTES,
            true,
        )
        validateScalars("title", item.title, CatalogInputLimits.TITLE_UNICODE_SCALARS)
        item.publicationStatusSummary?.let {
            validateScalars("publicationStatusSummary", it, CatalogInputLimits.STATUS_UNICODE_SCALARS)
        }
        item.latestUpdateEpochMs?.let { timestamp ->
            requireValidation(timestamp >= 0, "latestUpdateEpochMs", CatalogValidationReason.MALFORMED)
        }
        item.rating?.let(::validateCatalogRating)
        item.cover?.let(::validateCover)
    }

    private fun validateCover(cover: AcquisitionCoverInput) {
        when (cover) {
            is AcquisitionCoverInput.TrustedLocal -> {
                validateUtf8(
                    "cover.logicalAssetId",
                    cover.logicalAssetId,
                    CatalogInputLimits.LOCAL_ASSET_ID_UTF8_BYTES,
                    true,
                )
                validateUtf8(
                    "cover.assetVersion",
                    cover.assetVersion,
                    CatalogInputLimits.LOCAL_ASSET_VERSION_UTF8_BYTES,
                    true,
                )
            }
            is AcquisitionCoverInput.RemoteHttps -> {
                if (cover.rawUri.length > CatalogInputLimits.COVER_LOCATOR_CHARS) {
                    validationFailure("cover.rawUri", CatalogValidationReason.OVER_LIMIT)
                }
                validate("cover.rawUri") { RemoteHttpsUriV1.parseAndNormalize(cover.rawUri) }
                cover.reviewedStableArtworkToken?.let {
                    validateUtf8(
                        "cover.reviewedStableArtworkToken",
                        it,
                        CatalogInputLimits.STABLE_ARTWORK_TOKEN_UTF8_BYTES,
                        true,
                    )
                }
            }
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

    private fun StoryDetailAcquisition.asDiscoverItem() = DiscoverAcquisitionItem(
        sourceStoryId = sourceStoryId,
        title = title,
        contentType = contentType,
        cover = cover,
        rating = rating,
        publicationStatusSummary = publicationStatusSummary,
        latestUpdateEpochMs = latestUpdateEpochMs,
    )

}
