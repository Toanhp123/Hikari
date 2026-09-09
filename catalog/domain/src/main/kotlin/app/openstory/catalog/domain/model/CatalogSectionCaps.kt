package app.openstory.catalog.domain.model

object CatalogSectionCaps {
    const val MAX_DISCOVER_MEMBERSHIPS = 19
    const val MAX_SECTIONS = 3

    fun cap(kind: CatalogSectionKind): Int = when (kind) {
        CatalogSectionKind.POPULAR -> POPULAR_CAP
        CatalogSectionKind.LATEST_UPDATES -> LATEST_UPDATES_CAP
        CatalogSectionKind.TOP_RATED -> TOP_RATED_CAP
    }

    private const val POPULAR_CAP = 5
    private const val LATEST_UPDATES_CAP = 9
    private const val TOP_RATED_CAP = 5
}
