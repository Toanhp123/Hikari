package app.openstory.catalog.domain.validation

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CatalogPublicationValidatorTest {
    private val source = CatalogSourceKey("source")
    private val provenance = AcquisitionProvenance(source, "1", 1)

    @Test
    fun discoverPositionsMustBeContiguousAndUniquePerSection() {
        listOf(
            listOf(card("a", 1)),
            listOf(card("a", 0), card("b", 2)),
            listOf(card("a", 0), card("b", 0)),
        ).forEach { cards ->
            assertValidation { CatalogPublicationValidator.requireValidPublishedCards(cards) }
        }
    }

    @Test
    fun discoverRejectsDuplicateStoryWithinOneSectionButAllowsCrossSectionMembership() {
        val duplicate = listOf(card("same", 0), card("same", 1))
        assertValidation { CatalogPublicationValidator.requireValidPublishedCards(duplicate) }

        CatalogPublicationValidator.requireValidPublishedCards(
            listOf(
                card("same", 0, CatalogSectionKind.POPULAR),
                card("same", 0, CatalogSectionKind.TOP_RATED),
            ),
        )
    }

    @Test
    fun discoverCommandRejectsSourceMediaAndVersionAuthorityDrift() {
        assertFailsWith<IllegalArgumentException> {
            DiscoverPublicationCommand(
                catalogSourceKey = CatalogSourceKey("other"),
                mediaType = CatalogMediaType.MANGA,
                provenance = provenance,
                cards = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DiscoverPublicationCommand(source, CatalogMediaType.LIGHT_NOVEL, provenance, listOf(card("a", 0)))
        }
        assertFailsWith<IllegalArgumentException> {
            DiscoverPublicationCommand(source, CatalogMediaType.MANGA, provenance, listOf(card("a", 0, version = "2")))
        }
    }

    @Test
    fun publicationRevalidatesRogueScalarAndRatingInputs() {
        assertValidation {
            CatalogPublicationValidator.requireValidPublishedCards(listOf(card("a", 0, title = "x".repeat(1_025))))
        }
        assertValidation {
            CatalogPublicationValidator.requireValidPublishedCards(
                listOf(card("a", 0, rating = CatalogRating(Double.NaN, 10.0))),
            )
        }
        assertValidation {
            CatalogPublicationValidator.requireValidPublishedCards(listOf(card("a", 0, timestamp = -1)))
        }
    }

    @Test
    fun publicationDoesNotInventNonblankMetadataRequirements() {
        CatalogPublicationValidator.requireValidPublishedCards(listOf(card("a", 0, title = "")))
        CatalogPublicationValidator.requireValidStoryDetail(
            summary(ref("a")).copy(title = ""),
            StoryRichDetailProjection(
                description = "",
                authors = listOf(""),
                artists = listOf(""),
                genres = listOf(""),
                publicationStatus = "",
                language = "",
            ),
        )
    }

    @Test
    fun alignedCoverRejectsNullabilityStoryRevisionAndSourceDrift() {
        val ref = ref("a")
        val local = CoverLocator.TrustedLocalResource("asset", "1")
        val localKey = CoverAssetKey(ref.storyId, CoverRevisionV1.local("asset", "1"))
        val remoteUri = RemoteHttpsUriV1.parseAndNormalize("https://example.com/a")
        val remoteRevision = CoverRevisionV1.remoteUri(remoteUri)

        listOf(
            card("a", 0, locator = local, assetKey = null),
            card("a", 0, locator = null, assetKey = localKey),
            card("a", 0, locator = local, assetKey = CoverAssetKey(ref("b").storyId, localKey.coverRevision)),
            card("a", 0, locator = local, assetKey = CoverAssetKey(ref.storyId, CoverRevisionV1.local("asset", "2"))),
            card(
                "a",
                0,
                locator = CoverLocator.RemoteHttps(CatalogSourceKey("other"), remoteUri, remoteRevision),
                assetKey = CoverAssetKey(ref.storyId, remoteRevision),
            ),
            card(
                "a",
                0,
                locator = CoverLocator.RemoteHttps(source, remoteUri, remoteRevision),
                assetKey = CoverAssetKey(ref.storyId, CoverRevisionV1.remoteStableToken("other")),
            ),
        ).forEach { invalid ->
            assertFailsWith<CatalogFailureException> {
                DiscoverPublicationCommand(source, CatalogMediaType.MANGA, provenance, listOf(invalid))
            }
        }
    }

    @Test
    fun storyDetailCommandRejectsAuthorityAndRevalidatesCompleteProjection() {
        val ref = ref("a")
        val summary = summary(ref)

        assertFailsWith<IllegalArgumentException> {
            StoryDetailPublicationCommand(ref, AcquisitionProvenance(CatalogSourceKey("other"), "1", 1), summary, detail())
        }
        assertFailsWith<IllegalArgumentException> {
            StoryDetailPublicationCommand(ref, provenance, summary(ref("b")), detail())
        }
        assertFailsWith<IllegalArgumentException> {
            StoryDetailPublicationCommand(ref, provenance, summary.copy(sourceVersion = "2"), detail())
        }
        assertValidation {
            StoryDetailPublicationCommand(ref, provenance, summary, detail(description = "x".repeat(65_537)))
        }
    }

    private fun card(
        sourceStoryId: String,
        position: Int,
        kind: CatalogSectionKind = CatalogSectionKind.POPULAR,
        version: String = "1",
        title: String = "Title",
        rating: CatalogRating? = null,
        timestamp: Long? = null,
        locator: CoverLocator? = null,
        assetKey: CoverAssetKey? = null,
    ) = DiscoverCard(
        ref = ref(sourceStoryId),
        sectionKind = kind,
        itemPosition = position,
        title = title,
        contentType = CatalogMediaType.MANGA,
        sourceVersion = version,
        coverLocator = locator,
        coverAssetKey = assetKey,
        rating = rating,
        publicationStatusSummary = null,
        latestUpdateEpochMs = timestamp,
    )

    private fun summary(ref: StorySourceRef) = StorySummaryProjection(
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

    private fun detail(description: String? = null) = StoryRichDetailProjection(
        description = description,
        authors = emptyList(),
        artists = emptyList(),
        genres = emptyList(),
        publicationStatus = null,
        language = null,
    )

    private fun ref(sourceStoryId: String): StorySourceRef = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(source, sourceStoryId)),
        catalogSourceKey = source,
        sourceStoryId = sourceStoryId,
    )

    private fun assertValidation(block: () -> Unit) {
        val failure = assertFailsWith<CatalogFailureException>(block = block).failure
        assertIs<CatalogFailure.Validation>(failure)
        assertIs<CatalogValidationReason>(failure.reason)
    }
}
