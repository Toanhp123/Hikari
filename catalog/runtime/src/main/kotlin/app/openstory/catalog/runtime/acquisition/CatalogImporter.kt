package app.openstory.catalog.runtime.acquisition

import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.asset.CoverRevisionV1
import app.openstory.catalog.domain.asset.RemoteHttpsUriV1
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogValidationReason
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.source.CatalogSemanticProjection
import app.openstory.catalog.domain.source.DiscoverAcquisition
import app.openstory.catalog.domain.source.DiscoverAcquisitionItem
import app.openstory.catalog.domain.source.StoryDetailAcquisition
import app.openstory.catalog.domain.validation.CatalogAcquisitionValidator
import app.openstory.catalog.domain.write.CatalogMutationDiagnostics
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.domain.write.DiscoverPublicationCommand
import app.openstory.catalog.domain.write.StoryDetailPublicationCommand
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.retention.ActiveStoryPins
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import kotlinx.coroutines.withContext

class CatalogImporter(
    private val writePort: CatalogWritePort,
    private val activeStoryPins: ActiveStoryPins,
    private val dispatchers: CatalogExecutionDispatchers = CatalogExecutionDispatchers(),
) {
    suspend fun publishDiscover(
        binding: CatalogSourceBinding,
        mediaType: CatalogMediaType,
        acquisition: DiscoverAcquisition,
        acquiredAtEpochMs: Long,
    ): CatalogMutationDiagnostics {
        val command = withContext(dispatchers.cpu) {
            buildDiscoverCommand(binding, mediaType, acquisition.snapshot(), acquiredAtEpochMs)
        }
        return activeStoryPins.withMutationSnapshot { protectedStoryIds ->
            writePort.publishDiscover(command, protectedStoryIds)
        }
    }

    suspend fun upsertStoryDetail(
        binding: CatalogSourceBinding,
        ref: StorySourceRef,
        acquisition: StoryDetailAcquisition,
        acquiredAtEpochMs: Long,
    ) {
        val command = withContext(dispatchers.cpu) {
            buildStoryDetailCommand(binding, ref, acquisition.snapshot(), acquiredAtEpochMs)
        }
        writePort.publishStoryDetail(command)
    }

    private fun buildDiscoverCommand(
        binding: CatalogSourceBinding,
        mediaType: CatalogMediaType,
        acquisition: DiscoverAcquisition,
        acquiredAtEpochMs: Long,
    ): DiscoverPublicationCommand {
        requireTimestamp(acquiredAtEpochMs)
        CatalogAcquisitionValidator.requireValidDiscover(acquisition)
        val provenance = AcquisitionProvenance(
            binding.catalogSourceKey,
            binding.sourceVersion,
            acquiredAtEpochMs,
        )
        val cards = acquisition.sections
            .sortedBy { SECTION_ORDER.getValue(it.kind) }
            .flatMap { section ->
                CatalogSemanticProjection.project(binding.catalogSourceKey, section.kind, section.items)
                    .mapIndexed { position, item ->
                        requireContentType(item.contentType, mediaType)
                        item.toDiscoverCard(binding, provenance, section.kind, position)
                    }
            }
        requireConsistentRepeatedStories(cards)
        return DiscoverPublicationCommand(
            catalogSourceKey = binding.catalogSourceKey,
            mediaType = mediaType,
            provenance = provenance,
            cards = cards,
        )
    }

    private fun buildStoryDetailCommand(
        binding: CatalogSourceBinding,
        ref: StorySourceRef,
        acquisition: StoryDetailAcquisition,
        acquiredAtEpochMs: Long,
    ): StoryDetailPublicationCommand {
        requireAuthority(
            ref.catalogSourceKey == binding.catalogSourceKey,
            "ref.catalogSourceKey",
        )
        requireTimestamp(acquiredAtEpochMs)
        CatalogAcquisitionValidator.requireValidStoryDetail(ref, acquisition)
        val provenance = AcquisitionProvenance(
            binding.catalogSourceKey,
            binding.sourceVersion,
            acquiredAtEpochMs,
        )
        val cover = acquisition.cover.toPublishedCover(ref)
        return StoryDetailPublicationCommand(
            ref = ref,
            provenance = provenance,
            summary = StorySummaryProjection(
                ref = ref,
                title = acquisition.title,
                contentType = acquisition.contentType,
                sourceVersion = provenance.sourceVersion,
                coverLocator = cover?.first,
                coverAssetKey = cover?.second,
                rating = acquisition.rating,
                publicationStatusSummary = acquisition.publicationStatusSummary,
                latestUpdateEpochMs = acquisition.latestUpdateEpochMs,
            ),
            detail = StoryRichDetailProjection(
                description = acquisition.description,
                authors = acquisition.authors,
                artists = acquisition.artists,
                genres = acquisition.genres,
                publicationStatus = acquisition.publicationStatus,
                language = acquisition.language,
            ),
        )
    }
}

private fun DiscoverAcquisitionItem.toDiscoverCard(
    binding: CatalogSourceBinding,
    provenance: AcquisitionProvenance,
    sectionKind: CatalogSectionKind,
    itemPosition: Int,
): DiscoverCard {
    val ref = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(binding.catalogSourceKey, sourceStoryId)),
        catalogSourceKey = binding.catalogSourceKey,
        sourceStoryId = sourceStoryId,
    )
    val publishedCover = cover.toPublishedCover(ref)
    return DiscoverCard(
        ref = ref,
        sectionKind = sectionKind,
        itemPosition = itemPosition,
        title = title,
        contentType = contentType,
        sourceVersion = provenance.sourceVersion,
        coverLocator = publishedCover?.first,
        coverAssetKey = publishedCover?.second,
        rating = rating,
        publicationStatusSummary = publicationStatusSummary,
        latestUpdateEpochMs = latestUpdateEpochMs,
    )
}

private fun AcquisitionCoverInput?.toPublishedCover(
    ref: StorySourceRef,
): Pair<CoverLocator, CoverAssetKey>? = when (this) {
    null -> null
    is AcquisitionCoverInput.TrustedLocal -> {
        val revision = CoverRevisionV1.local(logicalAssetId, assetVersion)
        CoverLocator.TrustedLocalResource(logicalAssetId, assetVersion) to CoverAssetKey(ref.storyId, revision)
    }
    is AcquisitionCoverInput.RemoteHttps -> {
        val normalized = RemoteHttpsUriV1.parseAndNormalize(rawUri)
        val revision = reviewedStableArtworkToken?.let(CoverRevisionV1::remoteStableToken)
            ?: CoverRevisionV1.remoteUri(normalized)
        CoverLocator.RemoteHttps(ref.catalogSourceKey, normalized, revision) to CoverAssetKey(ref.storyId, revision)
    }
}

private fun requireConsistentRepeatedStories(cards: List<DiscoverCard>) {
    cards.groupBy { it.ref.storyId }.values.forEach { memberships ->
        val first = memberships.first()
        requireAuthority(
            memberships.all { it.ref == first.ref && it.contentType == first.contentType },
            "cards.storyIdentity",
        )
    }
}

private fun requireContentType(actual: CatalogMediaType, expected: CatalogMediaType) =
    requireAuthority(actual == expected, "cards.contentType")

private fun requireTimestamp(value: Long) = requireValidation(
    value >= 0,
    "acquiredAtEpochMs",
    CatalogValidationReason.MALFORMED,
)

private fun requireAuthority(condition: Boolean, field: String) = requireValidation(
    condition,
    field,
    CatalogValidationReason.AUTHORITY_MISMATCH,
)

private fun requireValidation(
    condition: Boolean,
    field: String,
    reason: CatalogValidationReason,
) {
    if (!condition) throw CatalogFailureException(CatalogFailure.Validation(field, reason))
}

private fun DiscoverAcquisition.snapshot() = copy(
    sections = sections.map { section -> section.copy(items = section.items.toList()) },
)

private fun StoryDetailAcquisition.snapshot() = copy(
    authors = authors.toList(),
    artists = artists.toList(),
    genres = genres.toList(),
)

private val SECTION_ORDER = mapOf(
    CatalogSectionKind.POPULAR to 0,
    CatalogSectionKind.LATEST_UPDATES to 1,
    CatalogSectionKind.TOP_RATED to 2,
)
