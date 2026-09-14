package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.model.CatalogMediaType

fun interface CatalogSearchCapability {
    suspend fun search(
        mediaType: CatalogMediaType,
        query: String,
        pageSize: Int,
        continuation: String?,
    ): CatalogPage<CatalogTransientStory>
}
