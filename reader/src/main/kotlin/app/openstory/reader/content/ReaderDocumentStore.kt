package app.openstory.reader.content

import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.document.ReaderDocument

interface ReaderDocumentStore {
    suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument?
    suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument)
    suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String)
}

object NoOpReaderDocumentStore : ReaderDocumentStore {
    override suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? = null
    override suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument) = Unit
    override suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String) = Unit
}
