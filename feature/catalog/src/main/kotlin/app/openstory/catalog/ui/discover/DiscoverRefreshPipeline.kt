package app.openstory.catalog.ui.discover

import app.openstory.catalog.home.CatalogRefreshResult
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.common.dispatchers.AppDispatchers
import javax.inject.Inject
import kotlinx.coroutines.withContext

class DiscoverRefreshPipeline @Inject constructor(
    private val refreshService: CatalogRefreshService,
    dispatchers: AppDispatchers,
) {
    private val dispatcher = dispatchers.default

    internal suspend fun refresh(
        cachedHomes: List<CatalogHomeSnapshot>,
    ): DiscoverRefreshReport = withContext(dispatcher) {
        refreshService.refresh().toReport(cachedHomes)
    }
}

private fun List<CatalogRefreshResult>.toReport(
    homes: List<CatalogHomeSnapshot>,
): DiscoverRefreshReport {
    val refreshedAt = homes.associate { it.pluginId to it.refreshedAtEpochMillis }
    return fold(DiscoverRefreshReport(refreshedAtEpochMillis = refreshedAt)) { report, result ->
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
}
