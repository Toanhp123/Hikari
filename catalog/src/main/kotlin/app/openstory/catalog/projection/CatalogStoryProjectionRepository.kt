package app.openstory.catalog.projection

import kotlinx.coroutines.flow.Flow

interface CatalogStoryProjectionRepository {
    fun observe(): Flow<List<CatalogStoryProjection>>
}
