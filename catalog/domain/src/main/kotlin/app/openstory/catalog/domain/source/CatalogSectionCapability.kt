package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.model.CatalogMediaType

fun interface CatalogSectionCapability {
    suspend fun acquireSection(
        mediaType: CatalogMediaType,
        section: CatalogSectionDescriptor,
        pageSize: Int,
        continuation: String?,
    ): CatalogPage<CatalogTransientStory>
}
