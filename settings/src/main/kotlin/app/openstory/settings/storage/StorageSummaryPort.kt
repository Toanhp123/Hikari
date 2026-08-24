package app.openstory.settings.storage

import kotlinx.coroutines.flow.Flow

fun interface StorageSummaryPort {
    fun observe(): Flow<SettingsStorageSummary>
}

data class SettingsStorageSummary(
    val totalBytes: Long,
    val automaticCacheBytes: Long,
    val automaticCacheQuotaBytes: Long,
)
