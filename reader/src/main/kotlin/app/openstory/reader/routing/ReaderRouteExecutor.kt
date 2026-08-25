package app.openstory.reader.routing

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderLoadFailure
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.content.ReaderSourceResult
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.document.isLocalPersistable
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.RecoveryScope
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.selection.ReleaseCandidate
import kotlinx.coroutines.CancellationException

/**
 * Compatibility executor with M3 semantic validation/observations. Ordering remains legacy/M1;
 * adaptive eligibility and scoring are still deliberately absent until M4.
 */
internal class ReaderRouteExecutor(
    private val store: ReaderDocumentStore,
    private val sources: ReaderDocumentSourceRegistry,
    private val executionLimiter: ReaderSourceExecutionLimiter = ReaderSourceExecutionLimiter(),
    private val validator: ReaderDocumentValidatorAdapter = ReaderDocumentValidatorAdapter(),
    private val monotonicNanos: () -> Long = System::nanoTime,
) {
    suspend fun executeCompatibility(
        orderedCandidates: List<ReleaseCandidate>,
        expectedFingerprints: Map<ChapterReleaseId, String>,
        remoteAttemptKinds: Map<ChapterReleaseId, RemoteAttemptKind> = emptyMap(),
        onSourceObservation: suspend (sourceId: PluginId, observation: SourceObservation) -> Unit = { _, _ -> },
        onLocalInvalidated: suspend (releaseId: ChapterReleaseId, fingerprint: String) -> Unit = { _, _ -> },
        onAttempt: suspend (index: Int, candidate: ReleaseCandidate) -> Unit = { _, _ -> },
    ): ReaderLoadResult {
        val failures = mutableListOf<ReaderLoadFailure>()
        var sourceByPlugin: Map<PluginId, ReaderDocumentSource>? = null
        for ((index, candidate) in orderedCandidates.withIndex()) {
            onAttempt(index, candidate)
            val cached = loadCached(
                candidate = candidate,
                fingerprint = expectedFingerprints[candidate.release.id],
                onSourceObservation = onSourceObservation,
                onLocalInvalidated = onLocalInvalidated,
            )
            if (cached != null) return cached

            val enabledSources = sourceByPlugin ?: loadFromSources().also { sourceByPlugin = it }
            val attemptKind = remoteAttemptKinds[candidate.release.id]
                ?: RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT
            when (
                val attempt = loadFromSource(
                    candidate = candidate,
                    sourceByPlugin = enabledSources,
                    attemptKind = attemptKind,
                    onSourceObservation = onSourceObservation,
                )
            ) {
                is CandidateLoadResult.Success -> return attempt.value
                is CandidateLoadResult.Failure -> failures += attempt.value.toLegacy()
            }
        }
        return ReaderLoadResult.Failure(failures)
    }

    private suspend fun loadFromSources(): Map<PluginId, ReaderDocumentSource> =
        sources.enabled().associateBy(ReaderDocumentSource::pluginId)

    private suspend fun loadFromSource(
        candidate: ReleaseCandidate,
        sourceByPlugin: Map<PluginId, ReaderDocumentSource>,
        attemptKind: RemoteAttemptKind,
        onSourceObservation: suspend (PluginId, SourceObservation) -> Unit,
    ): CandidateLoadResult {
        val release = candidate.release
        val sourceId = release.pluginId
        val source = sourceByPlugin[sourceId]
        if (source == null) {
            val failure = ReaderSourceFailureClassifier.classifyRemote(
                releaseId = release.id,
                sourceId = sourceId,
                code = "reader.source_unavailable",
                retryable = false,
                attemptKind = attemptKind,
                sourceOriginProven = false,
            )
            onSourceObservation(sourceId, failure.observation)
            return CandidateLoadResult.Failure(failure)
        }

        val startedNanos = monotonicNanos()
        val fetched = fetch(source, candidate)
        val latencyMillis = elapsedMillis(startedNanos, monotonicNanos())
        return when (fetched) {
            is ReaderSourceResult.Success -> when (
                val validation = validator.validateRemote(fetched.document, attemptKind)
            ) {
                is ReaderDocumentValidation.Valid -> {
                    onSourceObservation(
                        sourceId,
                        SourceObservation.Success.Remote(attemptKind, latencyMillis),
                    )
                    persistBestEffort(release.id, validation.document)
                    CandidateLoadResult.Success(
                        ReaderLoadResult.Success(candidate, validation.document, fromStore = false),
                    )
                }
                is ReaderDocumentValidation.Invalid -> {
                    val failure = ReaderAttemptFailure(
                        releaseId = release.id,
                        sourceId = sourceId,
                        accessMode = AccessMode.REMOTE,
                        observation = validation.observation,
                        recoveryScope = validation.recoveryScope,
                        legacyCode = validation.legacyCode,
                        retryable = false,
                        remoteAttemptKind = attemptKind,
                    )
                    onSourceObservation(sourceId, failure.observation)
                    CandidateLoadResult.Failure(failure)
                }
            }
            is ReaderSourceResult.Failure -> {
                val failure = ReaderSourceFailureClassifier.classifyRemote(
                    releaseId = release.id,
                    sourceId = sourceId,
                    code = fetched.code,
                    retryable = fetched.retryable,
                    attemptKind = attemptKind,
                    sourceOriginProven = true,
                )
                onSourceObservation(sourceId, failure.observation)
                CandidateLoadResult.Failure(failure)
            }
        }
    }

    private suspend fun loadCached(
        candidate: ReleaseCandidate,
        fingerprint: String?,
        onSourceObservation: suspend (PluginId, SourceObservation) -> Unit,
        onLocalInvalidated: suspend (ChapterReleaseId, String) -> Unit,
    ): ReaderLoadResult.Success? {
        val release = candidate.release
        val read = if (fingerprint == null) {
            readCurrent(candidate)
        } else {
            readExact(candidate, fingerprint)
        }
        val document = when (read) {
            is LocalReadResult.Hit -> read.document
            is LocalReadResult.Failure -> {
                onSourceObservation(release.pluginId, read.value.observation)
                return null
            }
        }

        return when (val validation = validator.validateLocal(document, fingerprint)) {
            is ReaderDocumentValidation.Valid -> {
                onSourceObservation(release.pluginId, SourceObservation.Success.Local)
                ReaderLoadResult.Success(candidate, validation.document, fromStore = true)
            }
            is ReaderDocumentValidation.Invalid -> {
                val failure = ReaderAttemptFailure(
                    releaseId = release.id,
                    sourceId = release.pluginId,
                    accessMode = AccessMode.LOCAL,
                    observation = validation.observation,
                    recoveryScope = validation.recoveryScope,
                    legacyCode = validation.legacyCode,
                    retryable = false,
                )
                onSourceObservation(release.pluginId, failure.observation)
                // Quarantine is valid only when the exact requested locator was materialized and
                // then proven corrupt/mismatched. A current/unversioned read has no requested
                // locator whose corruption can be asserted.
                if (fingerprint != null) {
                    quarantineBestEffort(release.id, fingerprint)
                    onLocalInvalidated(release.id, fingerprint)
                }
                null
            }
        }
    }

    private suspend fun readExact(
        candidate: ReleaseCandidate,
        fingerprint: String,
    ): LocalReadResult = readLocal(candidate, fingerprint) {
        store.read(candidate.release.id, fingerprint)
    }

    private suspend fun readCurrent(candidate: ReleaseCandidate): LocalReadResult =
        readLocal(candidate, requestedFingerprint = null) {
            store.readCurrent(candidate.release.id)
        }

    private suspend fun readLocal(
        candidate: ReleaseCandidate,
        requestedFingerprint: String?,
        read: suspend () -> ReaderDocument?,
    ): LocalReadResult = try {
        val document = read()
        if (document == null) {
            LocalReadResult.Failure(
                localFailure(
                    candidate = candidate,
                    observation = SourceObservation.LocalFailure.MissingBlob,
                    recoveryScope = RecoveryScope.LOCAL_SCOPED,
                    legacyCode = if (requestedFingerprint == null) {
                        "reader.local_document_missing"
                    } else {
                        "reader.local_blob_missing"
                    },
                    retryable = false,
                ),
            )
        } else {
            LocalReadResult.Hit(document)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Storage availability/I/O is not proof of corruption. Keep the failure typed and
        // client-scoped so remote recovery can continue without quarantining any locator.
        LocalReadResult.Failure(
            localFailure(
                candidate = candidate,
                observation = SourceObservation.RuntimeFailure.Unexpected,
                recoveryScope = RecoveryScope.CLIENT_SCOPED,
                legacyCode = "reader.local_read_failed",
                retryable = true,
            ),
        )
    }

    private fun localFailure(
        candidate: ReleaseCandidate,
        observation: SourceObservation,
        recoveryScope: RecoveryScope,
        legacyCode: String,
        retryable: Boolean,
    ) = ReaderAttemptFailure(
        releaseId = candidate.release.id,
        sourceId = candidate.release.pluginId,
        accessMode = AccessMode.LOCAL,
        observation = observation,
        recoveryScope = recoveryScope,
        legacyCode = legacyCode,
        retryable = retryable,
    )

    private suspend fun fetch(
        source: ReaderDocumentSource,
        candidate: ReleaseCandidate,
    ): ReaderSourceResult = try {
        executionLimiter.withRemotePermit(source.pluginId, ReaderRemoteWorkPriority.FOREGROUND) {
            source.fetch(candidate.release)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // This catch is exactly at the proven source invocation boundary, so classifier may use the
        // compatibility transport fallback for the synthesized retryable code.
        ReaderSourceResult.Failure("reader.source_failed", true)
    }

    private suspend fun persistBestEffort(
        releaseId: ChapterReleaseId,
        document: ReaderDocument,
    ) {
        if (!document.isLocalPersistable) return
        try {
            store.write(releaseId, document.fingerprint, document)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Automatic cache persistence is not part of semantic Reader success.
        }
    }

    private suspend fun quarantineBestEffort(
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ) {
        try {
            store.quarantine(releaseId, fingerprint)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Diagnostic-only in M3. The active generation still treats this exact locator invalid.
        }
    }

    private fun elapsedMillis(startNanos: Long, endNanos: Long): Long {
        val delta = if (endNanos >= startNanos) endNanos - startNanos else 0L
        return delta / NANOS_PER_MILLI
    }

    private sealed interface LocalReadResult {
        data class Hit(val document: ReaderDocument) : LocalReadResult
        data class Failure(val value: ReaderAttemptFailure) : LocalReadResult
    }

    private sealed interface CandidateLoadResult {
        data class Success(val value: ReaderLoadResult.Success) : CandidateLoadResult
        data class Failure(val value: ReaderAttemptFailure) : CandidateLoadResult
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
