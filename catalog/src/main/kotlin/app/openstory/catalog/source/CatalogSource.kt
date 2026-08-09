package app.openstory.catalog.source

import app.openstory.common.id.PluginId

interface CatalogSource {
    val pluginId: PluginId
    val version: String

    suspend fun home(request: SourceHomeRequest): CatalogSourceResult<List<SourceSection>>

    suspend fun search(request: SourceSearchRequest): CatalogSourceResult<SourceSearchPage>

    suspend fun details(sourceId: String): CatalogSourceResult<SourceDetails>

    suspend fun filters(): CatalogSourceResult<List<SourceFilter>>
}

interface CatalogSourceRegistry {
    suspend fun enabled(): List<CatalogSource>

    suspend fun source(pluginId: PluginId): CatalogSource?
}

sealed interface CatalogSourceResult<out T> {
    data class Success<T>(val value: T) : CatalogSourceResult<T>

    data class Failure(val failure: CatalogSourceFailure) : CatalogSourceResult<Nothing>
}

data class CatalogSourceFailure(
    val code: String,
    val retryable: Boolean,
)
