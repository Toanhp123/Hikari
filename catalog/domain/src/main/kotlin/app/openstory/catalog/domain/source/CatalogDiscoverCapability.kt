package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.model.CatalogMediaType

fun interface CatalogDiscoverCapability {
    suspend fun acquireDiscover(mediaType: CatalogMediaType): DiscoverAcquisition
}
