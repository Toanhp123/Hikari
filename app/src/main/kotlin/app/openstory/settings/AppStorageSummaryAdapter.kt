package app.openstory.settings

import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.cache.CacheRepository
import app.openstory.settings.storage.SettingsStorageSummary
import app.openstory.settings.storage.StorageSummaryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest

class AppStorageSummaryAdapter(
    private val cache: CacheRepository,
    private val downloads: DownloadRepository,
    private val settings: AppSettingsRepository,
) : StorageSummaryPort {
    override fun observe(): Flow<SettingsStorageSummary> = combine(
        downloads.observeAll(),
        settings.settings,
    ) { _, currentSettings -> currentSettings.normalized() }
        .mapLatest { currentSettings ->
            val entries = cache.entries()
            SettingsStorageSummary(
                totalBytes = entries.sumOf { it.sizeBytes },
                automaticCacheBytes = entries.asSequence()
                    .filter { it.key.namespace == ChapterBlobNamespace.AUTOMATIC_CACHE }
                    .sumOf { it.sizeBytes },
                automaticCacheQuotaBytes = currentSettings.automaticCacheQuotaBytes,
            )
        }
}
