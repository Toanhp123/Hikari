package app.openstory.catalog.feature.story

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.feature.state.CatalogIssueUi

internal data class StoryDetailUiState(
    val ref: StorySourceRef,
    val summary: StorySummaryUi?,
    val detail: StoryDetailUi?,
    val detailLoading: Boolean,
    val issue: CatalogIssueUi?,
    val destinationActive: Boolean,
    val coverLocator: CoverLocator? = summary?.coverLocator,
    val coverAssetKey: CoverAssetKey? = summary?.coverAssetKey,
)

internal data class StorySummaryUi(
    val title: String,
    val coverAssetKey: CoverAssetKey?,
    val ratingLabel: String?,
    val publicationStatus: String?,
    val coverLocator: CoverLocator? = null,
)

internal data class StoryDetailUi(
    val description: String?,
    val authors: List<String>,
    val artists: List<String>,
    val genres: List<String>,
    val publicationStatus: String?,
    val language: String?,
)
