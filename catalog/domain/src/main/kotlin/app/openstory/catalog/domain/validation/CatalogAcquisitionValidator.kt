package app.openstory.catalog.domain.validation

import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.asset.requireAlignedCover
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.source.DiscoverAcquisition
import app.openstory.catalog.domain.source.DiscoverAcquisitionItem
import app.openstory.catalog.domain.source.CatalogPage
import app.openstory.catalog.domain.source.CatalogSectionDescriptor
import app.openstory.catalog.domain.source.CatalogTransientStory
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

    fun requireValidSearchRequest(
        query: String,
        pageSize: Int,
        continuation: String?,
    ) {
        validateScalars("query", query, CatalogInputLimits.SEARCH_QUERY_UNICODE_SCALARS, true)
        requirePageSize(pageSize, CatalogInputLimits.SEARCH_PAGE_ITEMS)
        validateContinuation(continuation)
    }

    fun requireValidSectionRequest(
        section: CatalogSectionDescriptor,
        pageSize: Int,
        continuation: String?,
    ) {
        validateSectionDescriptor(section)
        requirePageSize(pageSize, CatalogInputLimits.SECTION_PAGE_ITEMS)
        validateContinuation(continuation)
    }

    fun requireValidSimilarRequest(maximumItems: Int) {
        requirePageSize(maximumItems, CatalogInputLimits.SIMILAR_ITEMS, "maximumItems")
    }

    fun requireValidSectionDescriptors(descriptors: List<CatalogSectionDescriptor>) {
        descriptors.forEachIndexed { index, descriptor ->
            validateSectionDescriptor(descriptor)
            requireValidation(
                descriptor.expansion == app.openstory.catalog.domain.source.SectionExpansion.PAGED,
                "sectionDescriptors[$index].expansion",
                CatalogValidationReason.INVARIANT_VIOLATION,
            )
        }
        requireValidation(
            descriptors.map(CatalogSectionDescriptor::key).distinct().size == descriptors.size,
            "sectionDescriptors.key",
            CatalogValidationReason.INVARIANT_VIOLATION,
        )
        requireValidation(
            descriptors.map(CatalogSectionDescriptor::kind).distinct().size == descriptors.size,
            "sectionDescriptors.kind",
            CatalogValidationReason.INVARIANT_VIOLATION,
        )
    }

    fun requireValidTransientPage(
        sourceKey: CatalogSourceKey,
        mediaType: app.openstory.catalog.domain.model.CatalogMediaType,
        maximumItems: Int,
        page: CatalogPage<CatalogTransientStory>,
    ) {
        requirePageSize(maximumItems, CatalogInputLimits.SECTION_PAGE_ITEMS, "maximumItems")
        requireValidation(page.items.size <= maximumItems, "items", CatalogValidationReason.OVER_LIMIT)
        page.items.forEachIndexed { index, item -> validateTransientStory(index, sourceKey, mediaType, item) }
        requireValidation(
            page.items.map { it.ref.sourceStoryId }.distinct().size == page.items.size,
            "items.sourceStoryId",
            CatalogValidationReason.INVARIANT_VIOLATION,
        )
        validateContinuation(page.nextContinuation)
    }

    private fun validateSectionDescriptor(section: CatalogSectionDescriptor) {
        validateUtf8("section.key", section.key, CatalogInputLimits.SECTION_KEY_UTF8_BYTES, true)
        requireValidation(
            SECTION_KEY.matches(section.key),
            "section.key",
            CatalogValidationReason.MALFORMED,
        )
        section.displayLabel?.let {
            validateScalars("section.displayLabel", it, CatalogInputLimits.SECTION_LABEL_UNICODE_SCALARS, true)
        }
    }

    private fun validateTransientStory(
        index: Int,
        sourceKey: CatalogSourceKey,
        mediaType: app.openstory.catalog.domain.model.CatalogMediaType,
        item: CatalogTransientStory,
    ) {
        requireValidation(
            item.ref.catalogSourceKey == sourceKey,
            "items[$index].ref.catalogSourceKey",
            CatalogValidationReason.AUTHORITY_MISMATCH,
        )
        requireValidation(
            item.contentType == mediaType,
            "items[$index].contentType",
            CatalogValidationReason.AUTHORITY_MISMATCH,
        )
        validateUtf8(
            "items[$index].ref.sourceStoryId",
            item.ref.sourceStoryId,
            CatalogInputLimits.SOURCE_STORY_ID_UTF8_BYTES,
            true,
        )
        validateScalars("items[$index].title", item.title, CatalogInputLimits.TITLE_UNICODE_SCALARS)
        item.supportingText?.let {
            validateScalars(
                "items[$index].supportingText",
                it,
                CatalogInputLimits.SUPPORTING_TEXT_UNICODE_SCALARS,
            )
        }
        item.rating?.let(::validateRating)
        validate("items[$index].cover", CatalogValidationReason.INVARIANT_VIOLATION) {
            requireAlignedCover(item.ref, item.coverLocator, item.coverAssetKey)
        }
    }

    private fun validateContinuation(continuation: String?) {
        continuation?.let {
            validateUtf8("continuation", it, CatalogInputLimits.CONTINUATION_UTF8_BYTES, true)
        }
    }

    private fun requirePageSize(maximumItems: Int, limit: Int, field: String = "pageSize") {
        requireValidation(maximumItems > 0, field, CatalogValidationReason.MALFORMED)
        requireValidation(maximumItems <= limit, field, CatalogValidationReason.OVER_LIMIT)
    }

    private val SECTION_KEY = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")

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
        item.rating?.let(::validateRating)
        item.cover?.let(::validateCover)
    }

    private fun validateRating(rating: CatalogRating) {
        requireValidation(
            rating.value.isFinite() && rating.scale.isFinite() &&
                rating.scale > 0.0 && rating.value >= 0.0 && rating.value <= rating.scale,
            "rating",
            CatalogValidationReason.MALFORMED,
        )
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
