package app.openstory.catalog.domain.validation

import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.limits.CatalogInputLimits
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.source.DiscoverAcquisition
import app.openstory.catalog.domain.source.DiscoverAcquisitionItem
import app.openstory.catalog.domain.source.DiscoverAcquisitionSection
import app.openstory.catalog.domain.source.StoryDetailAcquisition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogAcquisitionValidatorTest {
    @Test
    fun exactInputCeilingsAreAccepted() {
        val item = item(
            sourceStoryId = "s".repeat(CatalogInputLimits.SOURCE_STORY_ID_UTF8_BYTES),
            title = "\uD83D\uDE00".repeat(CatalogInputLimits.TITLE_UNICODE_SCALARS),
            cover = AcquisitionCoverInput.TrustedLocal(
                logicalAssetId = "a".repeat(CatalogInputLimits.LOCAL_ASSET_ID_UTF8_BYTES),
                assetVersion = "v".repeat(CatalogInputLimits.LOCAL_ASSET_VERSION_UTF8_BYTES),
            ),
            status = "s".repeat(CatalogInputLimits.STATUS_UNICODE_SCALARS),
        )
        val sections = listOf(
            section(CatalogSectionKind.POPULAR, CatalogSectionCaps.cap(CatalogSectionKind.POPULAR), item),
            section(CatalogSectionKind.LATEST_UPDATES, CatalogSectionCaps.cap(CatalogSectionKind.LATEST_UPDATES), item),
            section(CatalogSectionKind.TOP_RATED, CatalogSectionCaps.cap(CatalogSectionKind.TOP_RATED), item),
        )

        CatalogAcquisitionValidator.requireValidDiscover(DiscoverAcquisition(sections))
    }

    @Test
    fun boundedEmptyMetadataValuesAreNotRejectedByAnUnapprovedMinimum() {
        validateSingle(item(title = ""))
        CatalogAcquisitionValidator.requireValidStoryDetail(
            storyRef("requested"),
            detail(
                authors = listOf(""),
                artists = listOf(""),
                genres = listOf(""),
                publicationStatus = "",
                language = "",
            ),
        )
    }

    @Test
    fun discoverRejectsSectionShapeOutsideFrozenBounds() {
        assertValidation("sections", CatalogValidationReason.OVER_LIMIT) {
            CatalogAcquisitionValidator.requireValidDiscover(
                DiscoverAcquisition(
                    List(CatalogInputLimits.DISCOVER_SECTIONS + 1) { index ->
                        DiscoverAcquisitionSection(CatalogSectionKind.entries[index % 3], emptyList())
                    },
                ),
            )
        }
        assertValidation("sections", CatalogValidationReason.INVARIANT_VIOLATION) {
            CatalogAcquisitionValidator.requireValidDiscover(
                DiscoverAcquisition(
                    listOf(
                        DiscoverAcquisitionSection(CatalogSectionKind.POPULAR, emptyList()),
                        DiscoverAcquisitionSection(CatalogSectionKind.POPULAR, emptyList()),
                    ),
                ),
            )
        }
        assertValidation("sections[POPULAR].items", CatalogValidationReason.OVER_LIMIT) {
            CatalogAcquisitionValidator.requireValidDiscover(
                DiscoverAcquisition(
                    listOf(
                        DiscoverAcquisitionSection(
                            CatalogSectionKind.POPULAR,
                            List(CatalogSectionCaps.cap(CatalogSectionKind.POPULAR) + 1) { item("story-$it") },
                        ),
                    ),
                ),
            )
        }
        assertValidation("sections[POPULAR].sourceStoryId", CatalogValidationReason.INVARIANT_VIOLATION) {
            CatalogAcquisitionValidator.requireValidDiscover(
                DiscoverAcquisition(
                    listOf(
                        DiscoverAcquisitionSection(
                            CatalogSectionKind.POPULAR,
                            listOf(item("same"), item("same")),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun validationReasonsDistinguishMalformedTextFromOverLimitText() {
        assertValidation("sourceStoryId", CatalogValidationReason.MALFORMED) {
            validateSingle(item(sourceStoryId = "bad\uD800"))
        }
        assertValidation("sourceStoryId", CatalogValidationReason.OVER_LIMIT) {
            validateSingle(item(sourceStoryId = "x".repeat(513)))
        }
        assertValidation("title", CatalogValidationReason.OVER_LIMIT) {
            validateSingle(item(title = "x".repeat(1_025)))
        }
        assertValidation("cover.rawUri", CatalogValidationReason.MALFORMED) {
            validateSingle(item(cover = AcquisitionCoverInput.RemoteHttps("https://example.com/%zz")))
        }
    }

    @Test
    fun discoverRejectsMalformedAndOverBoundCardFields() {
        val cases = listOf(
            "sourceStoryId" to item(sourceStoryId = "x".repeat(513)),
            "sourceStoryId" to item(sourceStoryId = "bad\uD800"),
            "title" to item(title = "x".repeat(1_025)),
            "publicationStatusSummary" to item(status = "x".repeat(513)),
            "latestUpdateEpochMs" to item(latestUpdateEpochMs = -1),
            "rating" to item(rating = CatalogRating(Double.POSITIVE_INFINITY, 10.0)),
            "rating" to item(rating = CatalogRating(11.0, 10.0)),
            "cover.logicalAssetId" to item(
                cover = AcquisitionCoverInput.TrustedLocal("x".repeat(513), "1"),
            ),
            "cover.assetVersion" to item(
                cover = AcquisitionCoverInput.TrustedLocal("asset", "x".repeat(129)),
            ),
            "cover.rawUri" to item(
                cover = AcquisitionCoverInput.RemoteHttps("x".repeat(4_097)),
            ),
            "cover.reviewedStableArtworkToken" to item(
                cover = AcquisitionCoverInput.RemoteHttps(
                    rawUri = "https://example.com/a",
                    reviewedStableArtworkToken = "x".repeat(513),
                ),
            ),
        )

        cases.forEach { (field, invalidItem) ->
            assertValidation(field) {
                CatalogAcquisitionValidator.requireValidDiscover(
                    DiscoverAcquisition(
                        listOf(DiscoverAcquisitionSection(CatalogSectionKind.POPULAR, listOf(invalidItem))),
                    ),
                )
            }
        }
    }

    @Test
    fun storyDetailRejectsRouteMismatchAndEveryDetailCeiling() {
        val ref = storyRef("requested")
        val cases = listOf(
            "sourceStoryId" to detail(sourceStoryId = "other"),
            "description" to detail(description = "x".repeat(CatalogInputLimits.DESCRIPTION_UTF8_BYTES + 1)),
            "authors" to detail(authors = List(CatalogInputLimits.AUTHORS + 1) { "author" }),
            "authors[0]" to detail(authors = listOf("x".repeat(CatalogInputLimits.PERSON_UNICODE_SCALARS + 1))),
            "artists" to detail(artists = List(CatalogInputLimits.ARTISTS + 1) { "artist" }),
            "artists[0]" to detail(artists = listOf("x".repeat(CatalogInputLimits.PERSON_UNICODE_SCALARS + 1))),
            "genres" to detail(genres = List(CatalogInputLimits.GENRES + 1) { "genre" }),
            "genres[0]" to detail(genres = listOf("x".repeat(CatalogInputLimits.GENRE_UNICODE_SCALARS + 1))),
            "publicationStatus" to detail(publicationStatus = "x".repeat(CatalogInputLimits.STATUS_UNICODE_SCALARS + 1)),
            "language" to detail(language = "x".repeat(CatalogInputLimits.LANGUAGE_UNICODE_SCALARS + 1)),
        )

        cases.forEach { (field, invalidDetail) ->
            assertValidation(field) {
                CatalogAcquisitionValidator.requireValidStoryDetail(ref, invalidDetail)
            }
        }
    }

    @Test
    fun descriptionLimitCountsUtf8BytesNotCharacters() {
        val ref = storyRef("requested")
        CatalogAcquisitionValidator.requireValidStoryDetail(
            ref,
            detail(description = "\u00E9".repeat(CatalogInputLimits.DESCRIPTION_UTF8_BYTES / 2)),
        )
        assertValidation("description", CatalogValidationReason.OVER_LIMIT) {
            CatalogAcquisitionValidator.requireValidStoryDetail(
                ref,
                detail(description = "\u00E9".repeat(CatalogInputLimits.DESCRIPTION_UTF8_BYTES / 2 + 1)),
            )
        }
    }

    @Test
    fun provenanceRejectsBlankOversizedVersionAndNegativeTimestamp() {
        val source = CatalogSourceKey("source")

        listOf("", " ", "x".repeat(CatalogInputLimits.SOURCE_VERSION_UTF8_BYTES + 1)).forEach { version ->
            assertFailsWith<IllegalArgumentException> {
                AcquisitionProvenance(source, version, 0)
            }
        }
        assertFailsWith<IllegalArgumentException> {
            AcquisitionProvenance(source, "1", -1)
        }
    }

    private fun section(
        kind: CatalogSectionKind,
        count: Int,
        template: DiscoverAcquisitionItem,
    ) = DiscoverAcquisitionSection(
        kind,
        List(count) { index -> template.copy(sourceStoryId = "${kind.name}-$index") },
    )

    private fun item(
        sourceStoryId: String = "story",
        title: String = "Title",
        cover: AcquisitionCoverInput? = null,
        status: String? = null,
        latestUpdateEpochMs: Long? = 1,
        rating: CatalogRating? = CatalogRating(8.0, 10.0),
    ) = DiscoverAcquisitionItem(
        sourceStoryId = sourceStoryId,
        title = title,
        contentType = CatalogMediaType.MANGA,
        cover = cover,
        rating = rating,
        publicationStatusSummary = status,
        latestUpdateEpochMs = latestUpdateEpochMs,
    )

    private fun detail(
        sourceStoryId: String = "requested",
        description: String? = null,
        authors: List<String> = emptyList(),
        artists: List<String> = emptyList(),
        genres: List<String> = emptyList(),
        publicationStatus: String? = null,
        language: String? = null,
    ) = StoryDetailAcquisition(
        sourceStoryId = sourceStoryId,
        title = "Title",
        contentType = CatalogMediaType.MANGA,
        cover = null,
        rating = null,
        publicationStatusSummary = null,
        latestUpdateEpochMs = null,
        description = description,
        authors = authors,
        artists = artists,
        genres = genres,
        publicationStatus = publicationStatus,
        language = language,
    )

    private fun storyRef(sourceStoryId: String): StorySourceRef {
        val source = CatalogSourceKey("source")
        return StorySourceRef(
            SourceStoryIdV1.derive(SourceStoryKey(source, sourceStoryId)),
            source,
            sourceStoryId,
        )
    }

    private fun validateSingle(item: DiscoverAcquisitionItem) {
        CatalogAcquisitionValidator.requireValidDiscover(
            DiscoverAcquisition(
                listOf(DiscoverAcquisitionSection(CatalogSectionKind.POPULAR, listOf(item))),
            ),
        )
    }

    private fun assertValidation(
        field: String,
        reason: CatalogValidationReason? = null,
        block: () -> Unit,
    ) {
        val failure = assertFailsWith<CatalogFailureException>(block = block).failure
        val validation = failure as CatalogFailure.Validation
        assertEquals(field, validation.field)
        reason?.let { assertEquals(it, validation.reason) }
    }
}
