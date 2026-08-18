package app.openstory.reader.content

import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.document.ReaderDocument

interface ReaderDocumentStore {
    suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument?
    suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument?
    suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument)
    suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String)
}
