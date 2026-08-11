package app.openstory.downloads

import app.openstory.downloads.blob.ChapterBlobKey

interface DownloadRepository {
    suspend fun find(key: ChapterBlobKey): DownloadRecord?
    suspend fun save(record: DownloadRecord)
}
