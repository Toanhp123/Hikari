package app.openstory.settings

import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadState
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.settings.storage.SettingsStorageSummary
import app.openstory.settings.storage.StorageSummaryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class AppStorageSummaryAdapter(
    private val automaticCacheBudgetCoordinator: AutomaticCacheBudgetCoordinator,
    private val downloads: DownloadRepository,
    private val settings: AppSettingsRepository,
) : StorageSummaryPort {
    override fun observe(): Flow<SettingsStorageSummary> = combine(
        downloads.observeAll(),
        settings.settings,
    ) { records, currentSettings -> records to currentSettings.normalized() }
        .map { (records, currentSettings) ->
            val automaticBytes = automaticCacheBudgetCoordinator.snapshot().committedBytes
            val explicitDownloadBytes = records.asSequence()
                .filter { it.state == DownloadState.COMPLETED }
                .sumOf { it.sizeBytes }
            SettingsStorageSummary(
                totalBytes = explicitDownloadBytes + automaticBytes,
                automaticCacheBytes = automaticBytes,
                automaticCacheQuotaBytes = currentSettings.automaticCacheQuotaBytes,
            )
        }
}
