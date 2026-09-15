package app.openstory.catalog.feature.discover

import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.feature.R

internal data class DiscoverSectionLabels(
    val popular: String,
    val latestUpdates: String,
    val topRated: String,
) {
    fun title(kind: CatalogSectionKind): String = when (kind) {
        CatalogSectionKind.POPULAR -> popular
        CatalogSectionKind.LATEST_UPDATES -> latestUpdates
        CatalogSectionKind.TOP_RATED -> topRated
    }
}

internal val CatalogSectionKind.titleResource: Int
    get() = when (this) {
        CatalogSectionKind.POPULAR -> R.string.discover_section_trending_now
        CatalogSectionKind.LATEST_UPDATES -> R.string.discover_section_latest_updates
        CatalogSectionKind.TOP_RATED -> R.string.discover_section_top_rated
    }
