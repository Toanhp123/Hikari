package app.openstory.catalog.feature.discover

import app.openstory.catalog.domain.model.CatalogSectionKind

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
