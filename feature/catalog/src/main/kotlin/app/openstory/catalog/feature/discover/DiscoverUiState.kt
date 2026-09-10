package app.openstory.catalog.feature.discover

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.feature.state.CatalogIssueUi

internal data class DiscoverUiState(
    val selectedMediaType: CatalogMediaType = CatalogMediaType.MANGA,
    val mediaOptions: List<DiscoverMediaOption> = CatalogMediaType.entries.map { mediaType ->
        DiscoverMediaOption(mediaType, enabled = true)
    },
    val content: DiscoverContentState = DiscoverContentState.NoContentLoading,
)

internal data class DiscoverMediaOption(
    val mediaType: CatalogMediaType,
    val enabled: Boolean,
)

internal sealed interface DiscoverContentState {
    data object NoContentLoading : DiscoverContentState

    data class NoContentFailure(
        val issue: CatalogIssueUi,
    ) : DiscoverContentState

    data class Empty(
        val refreshing: Boolean = false,
        val issue: CatalogIssueUi? = null,
    ) : DiscoverContentState

    data class Content(
        val sections: List<DiscoverSectionUi>,
        val refreshing: Boolean,
        val issue: CatalogIssueUi?,
    ) : DiscoverContentState
}

internal data class DiscoverSectionUi(
    val kind: CatalogSectionKind,
    val cards: List<DiscoverCardUi>,
)

internal data class DiscoverCardUi(
    val ref: StorySourceRef,
    val title: String,
    val coverAssetKey: CoverAssetKey?,
    val ratingLabel: String?,
    val supportingLabel: String?,
    val coverLocator: CoverLocator? = null,
)
