package app.openstory.catalog.domain.validation

import app.openstory.catalog.domain.asset.requireAlignedCover
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.source.CatalogPage
import app.openstory.catalog.domain.source.CatalogSectionDescriptor
import app.openstory.catalog.domain.source.CatalogTransientStory
import app.openstory.catalog.domain.source.SectionExpansion

internal object CatalogTransientContractValidator {
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
                descriptor.expansion == SectionExpansion.PAGED,
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
        mediaType: CatalogMediaType,
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
        requireValidation(SECTION_KEY.matches(section.key), "section.key", CatalogValidationReason.MALFORMED)
        section.displayLabel?.let {
            validateScalars("section.displayLabel", it, CatalogInputLimits.SECTION_LABEL_UNICODE_SCALARS, true)
        }
    }

    private fun validateTransientStory(
        index: Int,
        sourceKey: CatalogSourceKey,
        mediaType: CatalogMediaType,
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
        validateScalars("items[$index].title", item.title, CatalogInputLimits.TITLE_UNICODE_SCALARS, true)
        item.supportingText?.let {
            validateScalars(
                "items[$index].supportingText",
                it,
                CatalogInputLimits.SUPPORTING_TEXT_UNICODE_SCALARS,
            )
        }
        item.rating?.let(::validateCatalogRating)
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
}
