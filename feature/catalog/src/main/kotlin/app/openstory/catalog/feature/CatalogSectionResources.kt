package app.openstory.catalog.feature

import app.openstory.catalog.domain.model.CatalogSectionKind

internal val CatalogSectionKind.titleResource: Int
    get() = when (this) {
        CatalogSectionKind.POPULAR -> R.string.discover_section_trending_now
        CatalogSectionKind.LATEST_UPDATES -> R.string.discover_section_latest_updates
        CatalogSectionKind.TOP_RATED -> R.string.discover_section_top_rated
    }
