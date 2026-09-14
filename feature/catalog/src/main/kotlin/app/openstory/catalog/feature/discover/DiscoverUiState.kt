package app.openstory.catalog.feature.discover

import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.asset.CoverLocator
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.source.CatalogSectionDescriptor
import app.openstory.catalog.domain.source.SectionExpansion
import app.openstory.catalog.feature.state.CatalogIssueUi

internal data class DiscoverUiState(
    val content: DiscoverContentState = DiscoverContentState.NoContentLoading,
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
    val descriptor: CatalogSectionDescriptor,
    val cards: List<DiscoverCardUi>,
) {
    val kind: CatalogSectionKind
        get() = descriptor.kind
}

internal fun CatalogSectionKind.previewDescriptor() = CatalogSectionDescriptor(
    key = when (this) {
        CatalogSectionKind.POPULAR -> "popular"
        CatalogSectionKind.LATEST_UPDATES -> "latest_updates"
        CatalogSectionKind.TOP_RATED -> "top_rated"
    },
    kind = this,
    displayLabel = null,
    expansion = SectionExpansion.NONE,
    globallyOrdered = false,
)

internal data class DiscoverCardUi(
    val ref: StorySourceRef,
    val title: String,
    val coverAssetKey: CoverAssetKey?,
    val ratingLabel: String?,
    val supportingLabel: String?,
    val coverLocator: CoverLocator? = null,
)
