package app.openstory.home.domain

import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.database.repository.CatalogRepository
import app.openstory.home.model.HomeRefreshReport
import app.openstory.plugin.api.catalog.CatalogHomeRequest
import app.openstory.plugin.api.catalog.CatalogPlugin
import app.openstory.plugin.api.catalog.CatalogSection
import app.openstory.plugin.host.HostedPlugin
import app.openstory.plugin.host.PluginHost
import java.util.concurrent.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class RefreshHome(
    private val host: PluginHost,
    private val mapper: CatalogSnapshotMapper,
    private val repository: CatalogRepository,
    private val dispatchers: AppDispatchers,
    private val maxConcurrentCatalogs: Int = DEFAULT_MAX_CONCURRENT_CATALOGS,
) {
    init {
        require(maxConcurrentCatalogs > 0) {
            "Home refresh concurrency must be positive"
        }
    }

    suspend operator fun invoke(): HomeRefreshReport = supervisorScope {
        val semaphore = Semaphore(maxConcurrentCatalogs)
        host.enabledCatalogs()
            .map { hosted ->
                async(dispatchers.io) {
                    semaphore.withPermit {
                        refreshAndPersist(hosted)
                    }
                }
            }
            .awaitAll()
            .fold(HomeRefreshReport()) { report, outcome ->
                when (val result = outcome.result) {
                    is AppResult.Success -> report.recordSuccess(
                        pluginId = outcome.hosted.id,
                        refreshedAtEpochMillis = outcome.refreshedAtEpochMillis,
                    )

                    is AppResult.Failure -> report.recordFailure(
                        pluginId = outcome.hosted.id,
                        error = result.error,
                        refreshedAtEpochMillis = outcome.refreshedAtEpochMillis,
                    )
                }
            }
    }

    private suspend fun refreshAndPersist(
        hosted: HostedPlugin<CatalogPlugin>,
    ): CatalogRefreshOutcome {
        val result = when (val refreshed = refresh(hosted)) {
            is AppResult.Success -> persist(hosted, refreshed.value)
            is AppResult.Failure -> AppResult.Failure(refreshed.error)
        }
        val refreshedAt = repository.observeCatalogHome(hosted.id)
            .first()
            ?.refreshedAtEpochMillis

        return CatalogRefreshOutcome(
            hosted = hosted,
            result = result,
            refreshedAtEpochMillis = refreshedAt,
        )
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun refresh(
        hosted: HostedPlugin<CatalogPlugin>,
    ): AppResult<List<CatalogSection>> = try {
        hosted.instance.home(CatalogHomeRequest())
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        AppResult.Failure(
            AppError.Plugin(
                code = REFRESH_FAILED_CODE,
                retryable = false,
            ),
        )
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun persist(
        hosted: HostedPlugin<CatalogPlugin>,
        sections: List<CatalogSection>,
    ): AppResult<Unit> = try {
        repository.ingest(mapper.map(hosted, sections))
    } catch (failure: CancellationException) {
        throw failure
    } catch (_: Exception) {
        AppResult.Failure(
            AppError.Storage(
                code = PERSIST_FAILED_CODE,
                retryable = false,
            ),
        )
    }

    private data class CatalogRefreshOutcome(
        val hosted: HostedPlugin<CatalogPlugin>,
        val result: AppResult<Unit>,
        val refreshedAtEpochMillis: Long?,
    )

    private companion object {
        const val DEFAULT_MAX_CONCURRENT_CATALOGS = 3
        const val REFRESH_FAILED_CODE = "catalog.refresh_failed"
        const val PERSIST_FAILED_CODE = "catalog.persist_failed"
    }
}
