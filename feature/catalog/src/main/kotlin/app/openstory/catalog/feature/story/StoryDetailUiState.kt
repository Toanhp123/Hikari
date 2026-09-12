package app.openstory.catalog.feature.story

import androidx.compose.runtime.Immutable
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.feature.state.CatalogIssueUi

internal data class StoryDetailUiState(
    val ref: StorySourceRef,
    val summary: StorySummaryUi?,
    val detail: StoryDetailUi?,
    val detailLoading: Boolean,
    val issue: CatalogIssueUi?,
    val destinationActive: Boolean,
    val artwork: StoryArtworkUi = StoryArtworkUi(assetKey = null, locator = null),
)

@Immutable
internal data class StoryArtworkUi(
    val assetKey: CoverAssetKey?,
    val locator: CoverLocator?,
)

@Immutable
internal data class StoryHeroUi(
    val title: String?,
    val contentType: CatalogMediaType?,
    val ratingLabel: String?,
    val publicationStatus: String?,
    val latestUpdateLabel: String?,
    val artwork: StoryArtworkUi,
)

internal fun StoryDetailUiState.toHeroUi(): StoryHeroUi = StoryHeroUi(
    title = summary?.title,
    contentType = summary?.contentType,
    ratingLabel = summary?.ratingLabel,
    publicationStatus = summary?.publicationStatus,
    latestUpdateLabel = summary?.latestUpdateLabel,
    artwork = artwork,
)

internal data class StorySummaryUi(
    val title: String,
    val contentType: CatalogMediaType,
    val ratingLabel: String?,
    val publicationStatus: String?,
    val latestUpdateLabel: String?,
)

internal data class StoryDetailUi(
    val description: String?,
    val authors: List<String>,
    val artists: List<String>,
    val genres: List<String>,
    val publicationStatus: String?,
    val language: String?,
)
