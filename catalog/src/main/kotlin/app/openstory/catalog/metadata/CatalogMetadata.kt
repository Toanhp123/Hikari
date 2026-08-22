package app.openstory.catalog.metadata

import app.openstory.catalog.model.CatalogEntry
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CatalogMetadataScope

data class CatalogMetadataKey(
    val pluginId: PluginId,
    val sourceId: String,
) {
    init {
        require(sourceId.isNotBlank())
    }
}

enum class CatalogMetadataLevel {
    Summary,
    Full,
}

data class CatalogMetadataStamp(
    val pluginVersion: String,
    val resolvedAtEpochMillis: Long,
) {
    init {
        require(pluginVersion.isNotBlank())
        require(resolvedAtEpochMillis >= 0)
    }
}

data class CatalogMetadataSnapshot(
    val entry: CatalogEntry,
    val summary: CatalogMetadataStamp,
    val full: CatalogMetadataStamp?,
)

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
