package app.openstory.reader.content

import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.document.ReaderDocument

sealed interface ReaderDocumentReadResult {
    data class Hit(val document: ReaderDocument) : ReaderDocumentReadResult
    data object Missing : ReaderDocumentReadResult
    data object FingerprintOrDecodeMismatch : ReaderDocumentReadResult
}

interface ReaderDocumentDurableWriteIntent

interface ReaderDocumentStore {
    suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument?
    suspend fun readResult(
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ): ReaderDocumentReadResult = read(releaseId, fingerprint)
        ?.let(ReaderDocumentReadResult::Hit)
        ?: ReaderDocumentReadResult.Missing
    suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument?

    suspend fun captureAutomaticWriteIntent(): ReaderDocumentDurableWriteIntent? = null

    suspend fun writeWithIntent(
        releaseId: ChapterReleaseId,
        fingerprint: String,
        document: ReaderDocument,
        intent: ReaderDocumentDurableWriteIntent?,
    ) {
        write(releaseId, fingerprint, document)
    }

    suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument)
    suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String)
}
