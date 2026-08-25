package app.openstory.reader.content

import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.routing.ReaderRouteExecutor
import app.openstory.reader.selection.ReleaseCandidate
import app.openstory.reader.selection.ReleaseSelectionResult
import app.openstory.reader.selection.ReleaseSelector
import app.openstory.reader.selection.ReleaseSelectionPolicy

data class ReaderLoadRequest(
    val candidates: List<ReleaseCandidate>,
    val selectionPolicy: ReleaseSelectionPolicy = ReleaseSelectionPolicy(),
    val expectedFingerprints: Map<ChapterReleaseId, String> = emptyMap(),
)

sealed interface ReaderLoadResult {
    data class Success(
        val release: ReleaseCandidate,
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

class ReaderDocumentRepository(
    store: ReaderDocumentStore,
    sources: ReaderDocumentSourceRegistry,
    private val selector: ReleaseSelector,
) {
    private val executor = ReaderRouteExecutor(store, sources)

    suspend fun load(request: ReaderLoadRequest): ReaderLoadResult {
        val selection = selector.select(request.candidates, request.selectionPolicy)
        if (selection !is ReleaseSelectionResult.Selected) return ReaderLoadResult.Failure(emptyList())
        val ordered = listOf(selection.candidate) + selection.alternates
        return executor.executeCompatibility(ordered, request.expectedFingerprints)
    }
}
