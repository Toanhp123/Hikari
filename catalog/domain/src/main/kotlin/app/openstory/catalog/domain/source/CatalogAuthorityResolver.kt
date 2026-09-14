package app.openstory.catalog.domain.source

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType

fun interface CatalogAuthorityResolver {
    fun authorityFor(mediaType: CatalogMediaType): CatalogSourceKey?
}
