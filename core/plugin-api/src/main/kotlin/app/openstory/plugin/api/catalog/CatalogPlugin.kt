package app.openstory.plugin.api.catalog

import app.openstory.common.AppResult
import app.openstory.plugin.api.Page

interface CatalogPlugin {
    suspend fun home(
        request: CatalogHomeRequest,
    ): AppResult<List<CatalogSection>>

    suspend fun search(
        request: CatalogSearchRequest,
    ): AppResult<Page<CatalogCard>>

    suspend fun details(
        sourceId: String,
    ): AppResult<CatalogDetails>

    suspend fun filters(): AppResult<List<CatalogFilterDefinition>>
}
