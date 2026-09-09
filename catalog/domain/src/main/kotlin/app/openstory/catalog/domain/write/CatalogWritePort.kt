package app.openstory.catalog.domain.write

import app.openstory.catalog.domain.asset.requireAlignedCover
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.StoryRichDetailProjection
import app.openstory.catalog.domain.read.StorySummaryProjection
import app.openstory.catalog.domain.source.AcquisitionProvenance
import app.openstory.catalog.domain.validation.CatalogPublicationValidator
import app.openstory.common.id.StoryId

data class DiscoverPublicationCommand(
    val catalogSourceKey: CatalogSourceKey,
    val mediaType: CatalogMediaType,
    val provenance: AcquisitionProvenance,
    val cards: List<DiscoverCard>,
) {
    init {
        require(provenance.catalogSourceKey == catalogSourceKey)
        CatalogPublicationValidator.requireValidPublishedCards(cards)
        require(cards.all { it.ref.catalogSourceKey == catalogSourceKey })
        require(cards.all { it.contentType == mediaType })
        require(cards.all { it.sourceVersion == provenance.sourceVersion })
        cards.forEach { requireAlignedCover(it.ref, it.coverLocator, it.coverAssetKey) }
    }
}

data class StoryDetailPublicationCommand(
    val ref: StorySourceRef,
    val provenance: AcquisitionProvenance,
    val summary: StorySummaryProjection,
    val detail: StoryRichDetailProjection,
) {
    init {
        require(provenance.catalogSourceKey == ref.catalogSourceKey)
        require(summary.ref == ref)
        require(summary.sourceVersion == provenance.sourceVersion)
        CatalogPublicationValidator.requireValidStoryDetail(summary, detail)
        requireAlignedCover(summary.ref, summary.coverLocator, summary.coverAssetKey)
    }
}

object CatalogMutationBounds {
    const val MAX_DISCOVER_TOUCHED_STORY_IDS = 57
    const val MAX_RELEASE_TOUCHED_STORY_IDS = 2
    const val MAX_RETENTION_PROTECTED_STORY_IDS = 2
}

data class CatalogMutationDiagnostics(
    val touchedStoryIds: Set<StoryId>,
)

interface CatalogWritePort {
    suspend fun publishDiscover(
        command: DiscoverPublicationCommand,
        retentionProtectedStoryIds: Set<StoryId>,
    ): CatalogMutationDiagnostics

    suspend fun publishStoryDetail(command: StoryDetailPublicationCommand)

    suspend fun touchStoryAccess(
        ref: StorySourceRef,
        accessedAtEpochMs: Long,
    )

    suspend fun releaseStoryDemand(
        ref: StorySourceRef,
        retentionProtectedStoryIds: Set<StoryId>,
        releasedAtEpochMs: Long,
    ): CatalogMutationDiagnostics
}
