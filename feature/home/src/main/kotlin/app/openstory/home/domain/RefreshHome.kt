package app.openstory.home.domain

import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceHomeRequest
import app.openstory.catalog.source.SourceSection
import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.common.dispatchers.AppDispatchers
import app.openstory.database.repository.CatalogRepository
import app.openstory.home.model.HomeRefreshReport
import java.util.concurrent.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class RefreshHome(
    private val sources: CatalogSourceRegistry,
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
        sources.enabled()
            .map { source ->
                async(dispatchers.io) {
                    semaphore.withPermit {
                        refreshAndPersist(source)
                    }
                }
            }
            .awaitAll()
            .fold(HomeRefreshReport()) { report, outcome ->
                when (val result = outcome.result) {
                    is AppResult.Success -> report.recordSuccess(
                        pluginId = outcome.source.pluginId,
                        refreshedAtEpochMillis = outcome.refreshedAtEpochMillis,
                    )

                    is AppResult.Failure -> report.recordFailure(
                        pluginId = outcome.source.pluginId,
                        error = result.error,
                        refreshedAtEpochMillis = outcome.refreshedAtEpochMillis,
                    )
                }
            }
    }

    private suspend fun refreshAndPersist(
        source: CatalogSource,
    ): CatalogRefreshOutcome {
        val result = when (val refreshed = refresh(source)) {
            is AppResult.Success -> persist(source, refreshed.value)
            is AppResult.Failure -> AppResult.Failure(refreshed.error)
        }
        val refreshedAt = repository.observeCatalogHome(source.pluginId)
            .first()
            ?.refreshedAtEpochMillis

        return CatalogRefreshOutcome(
            source = source,
            result = result,
            refreshedAtEpochMillis = refreshedAt,
        )
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private suspend fun refresh(
        source: CatalogSource,
    ): AppResult<List<SourceSection>> = try {
        source.home(SourceHomeRequest()).toAppResult()
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
        source: CatalogSource,
        sections: List<SourceSection>,
    ): AppResult<Unit> = try {
        repository.ingest(mapper.map(source, sections))
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
        val source: CatalogSource,
        val result: AppResult<Unit>,
        val refreshedAtEpochMillis: Long?,
    )

    private companion object {
        const val DEFAULT_MAX_CONCURRENT_CATALOGS = 3
        const val REFRESH_FAILED_CODE = "catalog.refresh_failed"
        const val PERSIST_FAILED_CODE = "catalog.persist_failed"
    }
}

private fun <T> CatalogSourceResult<T>.toAppResult(): AppResult<T> = when (this) {
    is CatalogSourceResult.Success -> AppResult.Success(value)
    is CatalogSourceResult.Failure -> AppResult.Failure(
        AppError.Plugin(code = failure.code, retryable = failure.retryable),
    )
}
