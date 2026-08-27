package app.openstory.reader.content

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.document.ReaderDocument

internal sealed interface ReaderLoadResult {
    data class Success(
        val release: ChapterRelease,
        val document: ReaderDocument,
        val fromStore: Boolean,
    ) : ReaderLoadResult

    data class Failure(val attempts: List<ReaderLoadFailure>) : ReaderLoadResult
}

data class ReaderLoadFailure(
    val releaseId: ChapterReleaseId,
    val code: String,
    val retryable: Boolean,
)
