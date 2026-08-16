package app.openstory.reader.content

import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.selection.ReleaseCandidate
import app.openstory.reader.selection.ReleaseSelectionResult
import app.openstory.reader.selection.ReleaseSelector
import app.openstory.reader.selection.ReleaseSelectionPolicy
import kotlinx.coroutines.CancellationException

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
    private val store: ReaderDocumentStore,
    private val sources: ReaderDocumentSourceRegistry,
    private val selector: ReleaseSelector,
) {
    suspend fun load(request: ReaderLoadRequest): ReaderLoadResult {
        val selection = selector.select(request.candidates, request.selectionPolicy)
        if (selection !is ReleaseSelectionResult.Selected) return ReaderLoadResult.Failure(emptyList())
        return loadSelected(selection, request.expectedFingerprints)
    }

    private suspend fun loadSelected(
        selection: ReleaseSelectionResult.Selected,
        expectedFingerprints: Map<ChapterReleaseId, String>,
    ): ReaderLoadResult {
        val ordered = listOf(selection.candidate) + selection.alternates
        val failures = mutableListOf<ReaderLoadFailure>()
        var sourceByPlugin: Map<app.openstory.common.id.PluginId, ReaderDocumentSource>? = null
        var success: ReaderLoadResult.Success? = null
        val candidates = ordered.iterator()
        while (success == null && candidates.hasNext()) {
            val candidate = candidates.next()
            val cached = loadCached(candidate, expectedFingerprints[candidate.release.id])
            if (cached != null) {
                success = cached
            } else {
                val enabledSources = sourceByPlugin ?: loadFromSources().also { sourceByPlugin = it }
                when (val attempt = loadFromSource(candidate, enabledSources)) {
                    is CandidateLoadResult.Success -> success = attempt.value
                    is CandidateLoadResult.Failure -> failures += attempt.value
                }
            }
        }
        return success ?: ReaderLoadResult.Failure(failures)
    }

    private suspend fun loadFromSources(): Map<app.openstory.common.id.PluginId, ReaderDocumentSource> =
        sources.enabled().associateBy(ReaderDocumentSource::pluginId)

    private suspend fun loadFromSource(
        candidate: ReleaseCandidate,
        sourceByPlugin: Map<app.openstory.common.id.PluginId, ReaderDocumentSource>,
    ): CandidateLoadResult {
        val source = sourceByPlugin[candidate.release.pluginId]
        return if (source == null) {
            CandidateLoadResult.Failure(
                ReaderLoadFailure(candidate.release.id, "reader.source_unavailable", false),
            )
        } else {
            when (val fetched = fetch(source, candidate)) {
                is ReaderSourceResult.Success -> {
                    store.write(candidate.release.id, fetched.document.fingerprint, fetched.document)
                    CandidateLoadResult.Success(ReaderLoadResult.Success(candidate, fetched.document, false))
                }
                is ReaderSourceResult.Failure -> CandidateLoadResult.Failure(
                    ReaderLoadFailure(candidate.release.id, fetched.code, fetched.retryable),
                )
            }
        }
    }

    private suspend fun loadCached(
        candidate: ReleaseCandidate,
        fingerprint: String?,
    ): ReaderLoadResult.Success? {
        val document = if (fingerprint == null) {
            readCurrent(candidate.release.id)
        } else {
            readCached(candidate.release.id, fingerprint)
        }
        return when {
            document == null -> null
            fingerprint != null && document.fingerprint != fingerprint -> {
                store.quarantine(candidate.release.id, fingerprint)
                null
            }
            else -> ReaderLoadResult.Success(candidate, document, true)
        }
    }

    private suspend fun readCached(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument? = try {
        store.read(releaseId, fingerprint)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        store.quarantine(releaseId, fingerprint)
        null
    }

    private suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? = try {
        store.readCurrent(releaseId)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    private suspend fun fetch(
        source: ReaderDocumentSource,
        candidate: ReleaseCandidate,
    ): ReaderSourceResult = try {
        source.fetch(candidate.release)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ReaderSourceResult.Failure("reader.source_failed", true)
    }
}

private sealed interface CandidateLoadResult {
    data class Success(val value: ReaderLoadResult.Success) : CandidateLoadResult
    data class Failure(val value: ReaderLoadFailure) : CandidateLoadResult
}
