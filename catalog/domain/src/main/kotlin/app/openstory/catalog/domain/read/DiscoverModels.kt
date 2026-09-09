package app.openstory.catalog.domain.read

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.source.AcquisitionProvenance

data class DiscoverCard(
    val ref: StorySourceRef,
    val sectionKind: CatalogSectionKind,
    val itemPosition: Int,
    val title: String,
    val contentType: CatalogMediaType,
    val sourceVersion: String,
    val coverLocator: CoverLocator?,
    val coverAssetKey: CoverAssetKey?,
    val rating: CatalogRating?,
    val publicationStatusSummary: String?,
    val latestUpdateEpochMs: Long?,
)

sealed interface DiscoverPersistenceState {
    data object Absent : DiscoverPersistenceState

    data class Published(
        val generation: Long,
        val provenance: AcquisitionProvenance,
        val cards: List<DiscoverCard>,
    ) : DiscoverPersistenceState
}
