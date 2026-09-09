package app.openstory.catalog.domain.read

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import kotlinx.coroutines.flow.Flow

interface DiscoverReadPort {
    fun observe(
        catalogSourceKey: CatalogSourceKey,
        mediaType: CatalogMediaType,
    ): Flow<DiscoverPersistenceState>
}
