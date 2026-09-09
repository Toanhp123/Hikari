package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.asset.AcquisitionCoverInput
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogRating

data class StoryDetailAcquisition(
    val sourceStoryId: String,
    val title: String,
    val contentType: CatalogMediaType,
    val cover: AcquisitionCoverInput?,
    val rating: CatalogRating?,
    val publicationStatusSummary: String?,
    val latestUpdateEpochMs: Long?,
    val description: String?,
    val authors: List<String>,
    val artists: List<String>,
    val genres: List<String>,
    val publicationStatus: String?,
    val language: String?,
)
