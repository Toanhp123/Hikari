package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.model.CatalogSectionKind

data class DiscoverAcquisitionItem(
    val sourceStoryId: String,
    val title: String,
    val contentType: CatalogMediaType,
    val cover: AcquisitionCoverInput?,
    val rating: CatalogRating?,
    val publicationStatusSummary: String?,
    val latestUpdateEpochMs: Long?,
)

data class DiscoverAcquisitionSection(
    val kind: CatalogSectionKind,
    val items: List<DiscoverAcquisitionItem>,
)

data class DiscoverAcquisition(
    val sections: List<DiscoverAcquisitionSection>,
)
