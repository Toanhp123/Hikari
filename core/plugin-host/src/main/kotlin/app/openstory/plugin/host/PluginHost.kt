package app.openstory.plugin.host

import app.openstory.common.AppResult
import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.model.PluginId
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.content.ContentPlugin
import app.openstory.plugin.host.diagnostics.PluginDiagnosticsRepository
import java.util.concurrent.CancellationException

interface PluginHost {
    suspend fun catalog(id: PluginId): HostedPlugin<CatalogPlugin>

    suspend fun content(id: PluginId): HostedPlugin<ContentPlugin>

    suspend fun enabledCatalogs(): List<HostedPlugin<CatalogPlugin>>

    suspend fun enabledContentSources(): List<HostedPlugin<ContentPlugin>>
}

data class HostedPlugin<T>(
    val id: PluginId,
    val version: String,
    val instance: T,
)

interface PluginHostSource {
    suspend fun catalog(id: PluginId): AppResult<HostedPlugin<CatalogPlugin>>

    suspend fun content(id: PluginId): AppResult<HostedPlugin<ContentPlugin>>

    suspend fun enabledCatalogIds(): List<PluginId>

    suspend fun enabledContentIds(): List<PluginId>
}

class DefaultPluginHost(
    private val source: PluginHostSource,
    private val diagnostics: PluginDiagnosticsRepository,
    private val clock: Clock = SystemClock,
) : PluginHost {
    override suspend fun catalog(id: PluginId): HostedPlugin<CatalogPlugin> =
        load(id, OPERATION_LOAD_CATALOG) { source.catalog(id) }.valueOrThrow(id)

    override suspend fun content(id: PluginId): HostedPlugin<ContentPlugin> =
        load(id, OPERATION_LOAD_CONTENT) { source.content(id) }.valueOrThrow(id)

    override suspend fun enabledCatalogs(): List<HostedPlugin<CatalogPlugin>> =
        source.enabledCatalogIds().mapNotNull { id ->
            load(id, OPERATION_LOAD_CATALOG) { source.catalog(id) }.successValue()
        }

    override suspend fun enabledContentSources(): List<HostedPlugin<ContentPlugin>> =
        source.enabledContentIds().mapNotNull { id ->
            load(id, OPERATION_LOAD_CONTENT) { source.content(id) }.successValue()
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> load(
        id: PluginId,
        operation: String,
        load: suspend () -> AppResult<HostedPlugin<T>>,
    ): AppResult<HostedPlugin<T>> {
        val startedAt = clock.nowEpochMillis()
        val result = try {
            load()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            AppResult.Failure(
                app.openstory.common.AppError.Plugin(
                    code = LOAD_FAILED_CODE,
                    retryable = false,
                ),
            )
        }

        if (result is AppResult.Failure) {
            recordFailureSafely(
                id = id,
                operation = operation,
                code = result.error.code,
                startedAt = startedAt,
            )
        }

        return result
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun recordFailureSafely(
        id: PluginId,
        operation: String,
        code: String,
        startedAt: Long,
    ) {
        try {
            diagnostics.recordFailure(
                pluginId = id.value,
                version = UNKNOWN_VERSION,
                operation = operation,
                code = code,
                durationMillis = (clock.nowEpochMillis() - startedAt).coerceAtLeast(0L),
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            // Diagnostic persistence must not turn an isolated plugin failure into a batch failure.
        }
    }

    private fun <T> AppResult<HostedPlugin<T>>.successValue(): HostedPlugin<T>? =
        when (this) {
            is AppResult.Success -> value
            is AppResult.Failure -> null
        }

    private fun <T> AppResult<HostedPlugin<T>>.valueOrThrow(
        id: PluginId,
    ): HostedPlugin<T> = when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> throw PluginHostLoadException(id, error.code)
    }

    private companion object {
        const val OPERATION_LOAD_CATALOG = "load_catalog"
        const val OPERATION_LOAD_CONTENT = "load_content"
        const val LOAD_FAILED_CODE = "plugin.runtime_load_failed"
        const val UNKNOWN_VERSION = "unknown"
    }
}

class PluginHostLoadException(
    val pluginId: PluginId,
    val errorCode: String,
) : IllegalStateException("Plugin host operation failed.")
