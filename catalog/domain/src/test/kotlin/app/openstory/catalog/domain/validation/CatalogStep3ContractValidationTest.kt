package app.openstory.catalog.domain.validation

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.source.CatalogPage
import app.openstory.catalog.domain.source.CatalogSectionDescriptor
import app.openstory.catalog.domain.source.CatalogTransientStory
import app.openstory.catalog.domain.source.SectionExpansion
import app.openstory.catalog.domain.source.StoryDetailAcquisition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogStep3ContractValidationTest {
    @Test
    fun searchRequestRejectsBlankMalformedOverBoundAndInvalidPagingInputs() {
        listOf(
            Triple("query", "", null),
            Triple("query", "bad\uD800", null),
            Triple("query", "q".repeat(CatalogInputLimits.SEARCH_QUERY_UNICODE_SCALARS + 1), null),
            Triple("continuation", "query", ""),
            Triple("continuation", "query", "x".repeat(CatalogInputLimits.CONTINUATION_UTF8_BYTES + 1)),
        ).forEach { (field, query, continuation) ->
            assertValidation(field) {
                CatalogAcquisitionValidator.requireValidSearchRequest(
                    query = query,
                    pageSize = 1,
                    continuation = continuation,
                )
            }
        }

        listOf(0, CatalogInputLimits.SEARCH_PAGE_ITEMS + 1).forEach { pageSize ->
            assertValidation("pageSize") {
                CatalogAcquisitionValidator.requireValidSearchRequest("query", pageSize, null)
            }
        }
    }

    @Test
    fun sectionRequestRejectsMalformedDescriptorAndInvalidPagingInputs() {
        listOf(
            "section.key" to descriptor(key = ""),
            "section.key" to descriptor(key = "bad\uD800"),
            "section.key" to descriptor(key = "latest updates"),
            "section.key" to descriptor(key = "x".repeat(CatalogInputLimits.SECTION_KEY_UTF8_BYTES + 1)),
            "section.displayLabel" to
                descriptor(displayLabel = "x".repeat(CatalogInputLimits.SECTION_LABEL_UNICODE_SCALARS + 1)),
        ).forEach { (field, invalid) ->
            assertValidation(field) {
                CatalogAcquisitionValidator.requireValidSectionRequest(invalid, 1, null)
            }
        }

        assertValidation("continuation") {
            CatalogAcquisitionValidator.requireValidSectionRequest(descriptor(), 1, " ")
        }
        assertValidation("pageSize") {
            CatalogAcquisitionValidator.requireValidSectionRequest(
                descriptor(),
                CatalogInputLimits.SECTION_PAGE_ITEMS + 1,
                null,
            )
        }
    }

    @Test
    fun transientPagesRejectWrongAuthorityMediaDuplicatesAndMalformedMetadata() {
        val source = CatalogSourceKey("source")
        val valid = transient(source, "one")
        assertValidation("maximumItems") {
            CatalogAcquisitionValidator.requireValidTransientPage(
                source,
                CatalogMediaType.MANGA,
                CatalogInputLimits.SECTION_PAGE_ITEMS + 1,
                CatalogPage(listOf(valid), null),
            )
        }
        val cases = listOf(
            "items[0].ref.catalogSourceKey" to CatalogPage(listOf(transient(CatalogSourceKey("other"), "one")), null),
            "items[0].contentType" to CatalogPage(listOf(valid.copy(contentType = CatalogMediaType.LIGHT_NOVEL)), null),
            "items.sourceStoryId" to CatalogPage(listOf(valid, valid.copy(title = "Duplicate")), null),
            "items[0].title" to CatalogPage(listOf(valid.copy(title = "")), null),
            "items[0].title" to CatalogPage(
                listOf(valid.copy(title = "x".repeat(CatalogInputLimits.TITLE_UNICODE_SCALARS + 1))),
                null,
            ),
            "items[0].supportingText" to CatalogPage(
                listOf(
                    valid.copy(
                        supportingText = "x".repeat(CatalogInputLimits.SUPPORTING_TEXT_UNICODE_SCALARS + 1),
                    ),
                ),
                null,
            ),
            "continuation" to CatalogPage(listOf(valid), ""),
        )

        cases.forEach { (field, page) ->
            assertValidation(field) {
                CatalogAcquisitionValidator.requireValidTransientPage(
                    source,
                    CatalogMediaType.MANGA,
                    CatalogInputLimits.SECTION_PAGE_ITEMS,
                    page,
                )
            }
        }
    }

    @Test
    fun storyMetadataRejectsAliasAndLanguageTagOverflowDuplicatesAndMalformedValues() {
        val ref = ref(CatalogSourceKey("source"), "one")
        val cases = listOf(
            "alternateTitles" to detail(
                alternateTitles = List(CatalogInputLimits.ALTERNATE_TITLES + 1) { "Alias $it" },
            ),
            "alternateTitles[0]" to detail(
                alternateTitles = listOf("x".repeat(CatalogInputLimits.TITLE_UNICODE_SCALARS + 1)),
            ),
            "alternateTitles[0]" to detail(alternateTitles = listOf("")),
            "alternateTitles" to detail(alternateTitles = listOf("Same", "Same")),
            "catalogLanguageTags" to detail(
                catalogLanguageTags = List(CatalogInputLimits.CATALOG_LANGUAGE_TAGS + 1) { "x-$it" },
            ),
            "catalogLanguageTags[0]" to detail(catalogLanguageTags = listOf("EN")),
            "catalogLanguageTags[0]" to detail(catalogLanguageTags = listOf("en us")),
            "catalogLanguageTags" to detail(catalogLanguageTags = listOf("en", "en")),
        )

        cases.forEach { (field, invalid) ->
            assertValidation(field) {
                CatalogPublicationValidator.requireValidStoryDetail(summary(ref), invalid)
            }
        }
    }

    @Test
    fun acquisitionMetadataUsesTheSameFailClosedBounds() {
        val ref = ref(CatalogSourceKey("source"), "one")

        assertValidation("alternateTitles") {
            CatalogAcquisitionValidator.requireValidStoryDetail(
                ref,
                acquisition(alternateTitles = listOf("Same", "Same")),
            )
        }
        assertValidation("catalogLanguageTags[0]") {
            CatalogAcquisitionValidator.requireValidStoryDetail(
                ref,
                acquisition(catalogLanguageTags = listOf("EN")),
            )
        }
    }

    @Test
    fun descriptorsAndSimilarLimitsRejectAmbiguousOrUnboundedContracts() {
        assertValidation("sectionDescriptors[0].expansion") {
            CatalogAcquisitionValidator.requireValidSectionDescriptors(
                listOf(descriptor(expansion = SectionExpansion.NONE)),
            )
        }
        assertValidation("sectionDescriptors.key") {
            CatalogAcquisitionValidator.requireValidSectionDescriptors(
                listOf(
                    descriptor(key = "same", expansion = SectionExpansion.PAGED),
                    descriptor(key = "same", expansion = SectionExpansion.PAGED),
                ),
            )
        }
        assertValidation("sectionDescriptors.kind") {
            CatalogAcquisitionValidator.requireValidSectionDescriptors(
                listOf(
                    descriptor(key = "one", expansion = SectionExpansion.PAGED),
                    descriptor(key = "two", expansion = SectionExpansion.PAGED),
                ),
            )
        }
        assertValidation("maximumItems") {
            CatalogAcquisitionValidator.requireValidSimilarRequest(0)
        }
        assertValidation("maximumItems") {
            CatalogAcquisitionValidator.requireValidSimilarRequest(CatalogInputLimits.SIMILAR_ITEMS + 1)
        }
    }

    private fun descriptor(
        key: String = "latest",
        displayLabel: String? = null,
        expansion: SectionExpansion = SectionExpansion.PAGED,
    ) = CatalogSectionDescriptor(
        key = key,
        kind = CatalogSectionKind.LATEST_UPDATES,
        displayLabel = displayLabel,
        expansion = expansion,
        globallyOrdered = false,
    )

    private fun transient(
        source: CatalogSourceKey,
        sourceStoryId: String,
    ) = CatalogTransientStory(
        ref = ref(source, sourceStoryId),
        title = "Title",
        contentType = CatalogMediaType.MANGA,
        coverAssetKey = null,
        coverLocator = null,
        supportingText = null,
        rating = null,
    )

    private fun detail(
        alternateTitles: List<String> = emptyList(),
        catalogLanguageTags: List<String> = emptyList(),
    ) = StoryRichDetailProjection(
        description = null,
        authors = emptyList(),
        artists = emptyList(),
        genres = emptyList(),
        publicationStatus = null,
        language = null,
        alternateTitles = alternateTitles,
        catalogLanguageTags = catalogLanguageTags,
    )

    private fun acquisition(
        alternateTitles: List<String> = emptyList(),
        catalogLanguageTags: List<String> = emptyList(),
    ) = StoryDetailAcquisition(
        sourceStoryId = "one",
        title = "Title",
        contentType = CatalogMediaType.MANGA,
        cover = null,
        rating = null,
        publicationStatusSummary = null,
        latestUpdateEpochMs = null,
        description = null,
        authors = emptyList(),
        artists = emptyList(),
        genres = emptyList(),
        publicationStatus = null,
        language = null,
        alternateTitles = alternateTitles,
        catalogLanguageTags = catalogLanguageTags,
    )

    private fun summary(ref: StorySourceRef) = app.openstory.catalog.domain.read.StorySummaryProjection(
        ref = ref,
        title = "Title",
        contentType = CatalogMediaType.MANGA,
        sourceVersion = "1",
        coverLocator = null,
        coverAssetKey = null,
        rating = null,
        publicationStatusSummary = null,
        latestUpdateEpochMs = null,
    )

    private fun ref(source: CatalogSourceKey, sourceStoryId: String): StorySourceRef = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(source, sourceStoryId)),
        catalogSourceKey = source,
        sourceStoryId = sourceStoryId,
    )

    private fun assertValidation(field: String, block: () -> Unit) {
        val failure = assertFailsWith<CatalogFailureException>(block = block).failure
        val validation = failure as CatalogFailure.Validation
        assertEquals(field, validation.field)
        check(
            validation.reason == CatalogValidationReason.MALFORMED ||
                validation.reason == CatalogValidationReason.OVER_LIMIT ||
                validation.reason == CatalogValidationReason.AUTHORITY_MISMATCH ||
                validation.reason == CatalogValidationReason.INVARIANT_VIOLATION,
        )
    }
}
