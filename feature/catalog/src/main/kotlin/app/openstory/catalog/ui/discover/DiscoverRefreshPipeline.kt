package app.openstory.catalog.ui.discover

import app.openstory.catalog.home.CatalogRefreshPrioritySelector
import app.openstory.catalog.home.CatalogRefreshResult
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.dispatchers.AppDispatchers
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class DiscoverRefreshPipeline @Inject constructor(
    private val refreshService: CatalogRefreshService,
    private val repository: CatalogRepository,
    dispatchers: AppDispatchers,
) {
    private val dispatcher = dispatchers.default

    internal suspend fun refresh(): DiscoverRefreshExecution = withContext(dispatcher) {
        val results = refreshService.refresh(
            prioritySelector = CatalogRefreshPrioritySelector { committedHomes ->
                discoverCanonicalBootstrapStoryIds(committedHomes, ContentType.MANGA).toSet()
            },
        )
        val homes = repository.observeHomes().first()
        DiscoverRefreshExecution(
            report = results.toReport(homes),
            homes = homes,
            anyRetryableFailure = results.any { it.isRetryableFailure() },
        )
    }
}

internal data class DiscoverRefreshExecution(
    val report: DiscoverRefreshReport,
    val homes: List<CatalogHomeSnapshot>,
    val anyRetryableFailure: Boolean,
) {
    val noEnabledProviders: Boolean
        get() = report.succeeded.isEmpty() && report.failed.isEmpty()

    val allProvidersFailed: Boolean
        get() = report.succeeded.isEmpty() && report.failed.isNotEmpty()
}

private fun List<CatalogRefreshResult>.toReport(
    homes: List<CatalogHomeSnapshot>,
): DiscoverRefreshReport {
    val refreshedAt = homes.associate { it.pluginId to it.refreshedAtEpochMillis }
    return fold(DiscoverRefreshReport(refreshedAtEpochMillis = refreshedAt)) { report, result ->
        when (result) {
            is CatalogRefreshResult.Success -> report.copy(
                succeeded = report.succeeded + result.pluginId,
            )
            is CatalogRefreshResult.SourceFailure -> report.copy(
                failed = report.failed + (result.pluginId to result.failure.code),
            )
            is CatalogRefreshResult.StoreFailure -> report.copy(
                failed = report.failed + (result.pluginId to result.failure.code),
            )
        }
    }
}

private fun CatalogRefreshResult.isRetryableFailure(): Boolean = when (this) {
    is CatalogRefreshResult.Success -> false
    is CatalogRefreshResult.SourceFailure -> failure.retryable
    is CatalogRefreshResult.StoreFailure -> failure.retryable
}
