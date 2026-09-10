package app.openstory.catalog.feature.discover

import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogSectionKind

internal object DiscoverTestTags {
    const val ROOT = "catalog-discover"
    const val EMPTY = "discover-empty"
    const val POPULAR_SKELETON = "discover-skeleton-popular"
    const val LATEST_SKELETON = "discover-skeleton-latest"
    const val TOP_RATED_SKELETON = "discover-skeleton-top-rated"

    fun section(kind: CatalogSectionKind): String = "discover-section-${kind.name.lowercase()}"

    fun card(kind: CatalogSectionKind, ref: StorySourceRef): String =
        "discover-card-${kind.name.lowercase()}-${ref.storyId.value}"
}
