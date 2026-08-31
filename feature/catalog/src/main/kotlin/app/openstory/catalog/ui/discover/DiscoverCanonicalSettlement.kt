package app.openstory.catalog.ui.discover

import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.common.id.StoryId

internal enum class DiscoverExclusionReason {
    CONTENT_TYPE_MISMATCH,
}

internal sealed interface DiscoverCanonicalSettlement {
    val storyId: StoryId

    data class Projected(
        override val storyId: StoryId,
        val projection: CatalogStoryProjection,
    ) : DiscoverCanonicalSettlement

    data class ResolvedExcluded(
        override val storyId: StoryId,
        val reason: DiscoverExclusionReason,
    ) : DiscoverCanonicalSettlement

    data class Failed(
        override val storyId: StoryId,
        val failure: CatalogUiFailure,
    ) : DiscoverCanonicalSettlement
}
