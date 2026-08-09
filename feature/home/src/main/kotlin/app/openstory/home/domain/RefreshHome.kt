package app.openstory.home.domain

import app.openstory.catalog.home.CatalogRefreshResult
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.AppError
import app.openstory.home.model.HomeRefreshReport
import app.openstory.home.model.HomeCatalogFreshness
import kotlinx.coroutines.flow.first

class RefreshHome(
    private val refreshService: CatalogRefreshService,
    private val repository: CatalogRepository,
) {
    suspend operator fun invoke(): HomeRefreshReport {
        val results = refreshService.refresh()
        val homes = repository.observeHomes().first().associateBy { it.pluginId }
        return results.fold(HomeRefreshReport()) { report, result ->
            val refreshedAt = homes[result.pluginId]?.refreshedAtEpochMillis
            when (result) {
                is CatalogRefreshResult.Success -> report.copy(
                    succeeded = report.succeeded + result.pluginId,
                    freshness = report.freshness + (result.pluginId to HomeCatalogFreshness(refreshedAt, false)),
                )
                is CatalogRefreshResult.SourceFailure -> report.copy(
                    failed = report.failed + (result.pluginId to result.failure.toAppError()),
                    freshness = report.freshness + (result.pluginId to HomeCatalogFreshness(refreshedAt, true)),
                )
                is CatalogRefreshResult.StoreFailure -> report.copy(
                    failed = report.failed + (result.pluginId to result.failure.toAppError()),
                    freshness = report.freshness + (result.pluginId to HomeCatalogFreshness(refreshedAt, true)),
                )
            }
        }
    }
}

private fun app.openstory.catalog.source.CatalogSourceFailure.toAppError(): AppError = AppError.Plugin(
    code = code,
    retryable = retryable,
)

private fun app.openstory.catalog.CatalogStoreFailure.toAppError(): AppError = AppError.Storage(
    code = code,
    retryable = retryable,
)
