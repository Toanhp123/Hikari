package app.openstory.downloads

import app.openstory.common.id.ChapterReleaseId
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    suspend fun find(releaseId: ChapterReleaseId): DownloadRecord?
    fun observe(releaseId: ChapterReleaseId): Flow<DownloadRecord?>
    suspend fun save(record: DownloadRecord)
}
