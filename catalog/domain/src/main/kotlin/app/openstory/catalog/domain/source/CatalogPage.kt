package app.openstory.catalog.domain.source

data class CatalogPage<T>(
    val items: List<T>,
    val nextContinuation: String?,
)
