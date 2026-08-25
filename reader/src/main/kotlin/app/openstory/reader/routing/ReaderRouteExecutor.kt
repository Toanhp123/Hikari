package app.openstory.reader.routing

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderLoadFailure
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.isLocalPersistable
import app.openstory.reader.selection.ReleaseCandidate
import kotlinx.coroutines.CancellationException

/** M2 compatibility seam: exact extraction of the legacy local-first sequential attempt loop. */
internal class ReaderRouteExecutor(
    private val store: ReaderDocumentStore,
    private val sources: ReaderDocumentSourceRegistry,
) {
    suspend fun executeCompatibility(
        orderedCandidates: List<ReleaseCandidate>,
        expectedFingerprints: Map<ChapterReleaseId, String>,
        onAttempt: suspend (index: Int, candidate: ReleaseCandidate) -> Unit = { _, _ -> },
    ): ReaderLoadResult {
        val failures = mutableListOf<ReaderLoadFailure>()
        var sourceByPlugin: Map<PluginId, ReaderDocumentSource>? = null
        for ((index, candidate) in orderedCandidates.withIndex()) {
            onAttempt(index, candidate)
            val cached = loadCached(candidate, expectedFingerprints[candidate.release.id])
            if (cached != null) return cached

            val enabledSources = sourceByPlugin ?: loadFromSources().also { sourceByPlugin = it }
            when (val attempt = loadFromSource(candidate, enabledSources)) {
                is CandidateLoadResult.Success -> return attempt.value
                is CandidateLoadResult.Failure -> failures += attempt.value
            }
        }
        return ReaderLoadResult.Failure(failures)
    }

    private suspend fun loadFromSources(): Map<PluginId, ReaderDocumentSource> =
        sources.enabled().associateBy(ReaderDocumentSource::pluginId)

    private suspend fun loadFromSource(
        candidate: ReleaseCandidate,
        sourceByPlugin: Map<PluginId, ReaderDocumentSource>,
    ): CandidateLoadResult {
        val source = sourceByPlugin[candidate.release.pluginId]
        return if (source == null) {
            CandidateLoadResult.Failure(
                ReaderLoadFailure(candidate.release.id, "reader.source_unavailable", false),
            )
        } else {
            when (val fetched = fetch(source, candidate)) {
                is ReaderSourceResult.Success -> {
                    if (fetched.document.isLocalPersistable) {
                        store.write(candidate.release.id, fetched.document.fingerprint, fetched.document)
                    }
                    CandidateLoadResult.Success(
                        ReaderLoadResult.Success(candidate, fetched.document, fromStore = false),
                    )
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
            else -> ReaderLoadResult.Success(candidate, document, fromStore = true)
        }
    }

    private suspend fun readCached(
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ) = try {
        store.read(releaseId, fingerprint)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        store.quarantine(releaseId, fingerprint)
        null
    }

    private suspend fun readCurrent(releaseId: ChapterReleaseId) = try {
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

    private sealed interface CandidateLoadResult {
        data class Success(val value: ReaderLoadResult.Success) : CandidateLoadResult
        data class Failure(val value: ReaderLoadFailure) : CandidateLoadResult
    }
}
