package app.openstory.catalog.ui.discover

import app.openstory.catalog.home.CatalogRefreshPrioritySelector
import app.openstory.catalog.home.CatalogRefreshResult
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.common.dispatchers.AppDispatchers
import javax.inject.Inject
import kotlinx.coroutines.withContext

class DiscoverRefreshPipeline @Inject constructor(
    private val refreshService: CatalogRefreshService,
    dispatchers: AppDispatchers,
) {
    private val dispatcher = dispatchers.default

    internal suspend fun refresh(): DiscoverRefreshExecution = withContext(dispatcher) {
        val results = refreshService.refresh(
            prioritySelector = CatalogRefreshPrioritySelector { emptySet() },
        )
        DiscoverRefreshExecution(
            report = results.toReport(),
            anyRetryableFailure = results.any { it.isRetryableFailure() },
        )
    }
}

internal data class DiscoverRefreshExecution(
    val report: DiscoverRefreshReport,
    val anyRetryableFailure: Boolean,
) {
    val noEnabledProviders: Boolean
        get() = report.succeeded.isEmpty() && report.failed.isEmpty()

    val allProvidersFailed: Boolean
        get() = report.succeeded.isEmpty() && report.failed.isNotEmpty()
}

private fun List<CatalogRefreshResult>.toReport(): DiscoverRefreshReport =
    fold(DiscoverRefreshReport()) { report, result ->
        when (result) {
            is CatalogRefreshResult.Success -> report.copy(
                succeeded = report.succeeded + result.pluginId,
                refreshedAtEpochMillis = report.refreshedAtEpochMillis +
                    (result.pluginId to result.refreshedAtEpochMillis),
            )
            is CatalogRefreshResult.SourceFailure -> report.copy(
                failed = report.failed + (result.pluginId to result.failure.code),
            )
            is CatalogRefreshResult.StoreFailure -> report.copy(
                failed = report.failed + (result.pluginId to result.failure.code),
            )
        }
    }

private fun CatalogRefreshResult.isRetryableFailure(): Boolean = when (this) {
    is CatalogRefreshResult.Success -> false
    is CatalogRefreshResult.SourceFailure -> failure.retryable
    is CatalogRefreshResult.StoreFailure -> failure.retryable
}
