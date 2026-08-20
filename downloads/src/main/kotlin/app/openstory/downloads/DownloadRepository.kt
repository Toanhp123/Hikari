package app.openstory.downloads

import app.openstory.common.id.ChapterReleaseId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface DownloadRepository {
    fun observeAll(): Flow<List<DownloadRecord>>
    fun observeCompletedCount(): Flow<Int> =
        observeAll().map { records -> records.count { it.state == DownloadState.COMPLETED } }
    suspend fun find(releaseId: ChapterReleaseId): DownloadRecord?
    fun observe(releaseId: ChapterReleaseId): Flow<DownloadRecord?>
    suspend fun save(record: DownloadRecord)
    suspend fun completeUnlessCancelled(record: DownloadRecord): Boolean {
        save(record)
        return true
    }
}
