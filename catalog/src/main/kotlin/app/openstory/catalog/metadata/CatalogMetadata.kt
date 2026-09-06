package app.openstory.catalog.metadata

import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CatalogMetadataScope

interface CatalogMetadataAccess {
    suspend fun require(key: CatalogMetadataKey, level: CatalogMetadataLevel): CatalogMetadataResult
    suspend fun refresh(key: CatalogMetadataKey, level: CatalogMetadataLevel): CatalogMetadataResult
}

sealed interface CatalogMetadataResult {
    data class Ready(
        val storyId: StoryId,
        val entry: CatalogEntry,
    ) : CatalogMetadataResult

    data class Failure(
        val failure: CatalogMetadataFailure,
    ) : CatalogMetadataResult

    data object Missing : CatalogMetadataResult
}

sealed interface CatalogMetadataFailure {
    data class SourceUnavailable(
        val pluginId: PluginId,
    ) : CatalogMetadataFailure

    data class SourceFailure(
        val code: String,
        val retryable: Boolean,
    ) : CatalogMetadataFailure

    data class SourceIdMismatch(
        val requested: String,
        val returned: String,
    ) : CatalogMetadataFailure

    data class StoreFailure(
        val code: String,
        val retryable: Boolean,
    ) : CatalogMetadataFailure
}
