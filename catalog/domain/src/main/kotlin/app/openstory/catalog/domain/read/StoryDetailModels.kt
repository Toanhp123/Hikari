package app.openstory.catalog.domain.read

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating
import app.openstory.catalog.domain.source.AcquisitionProvenance

data class StorySummaryProjection(
    val ref: StorySourceRef,
    val title: String,
    val contentType: CatalogMediaType,
    val sourceVersion: String,
    val coverLocator: CoverLocator?,
    val coverAssetKey: CoverAssetKey?,
    val rating: CatalogRating?,
    val publicationStatusSummary: String?,
    val latestUpdateEpochMs: Long?,
)

data class StoryRichDetailProjection(
    val description: String?,
    val authors: List<String>,
    val artists: List<String>,
    val genres: List<String>,
    val publicationStatus: String?,
    val language: String?,
)

data class StoryDetailProjection(
    val ref: StorySourceRef,
    val summary: StorySummaryProjection,
    val detail: StoryRichDetailProjection?,
    val detailProvenance: AcquisitionProvenance?,
) {
    init {
        require(summary.ref == ref)
        require((detail == null) == (detailProvenance == null))
        detailProvenance?.let { require(it.catalogSourceKey == ref.catalogSourceKey) }
    }
}
