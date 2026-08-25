package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterRelease
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
import kotlinx.coroutines.CancellationException

/** Executes exactly the bounded LOCAL/REMOTE attempts emitted by the HES-v1 routing engine. */
internal class ReaderRouteExecutor(
    private val store: ReaderDocumentStore,
    private val sources: ReaderDocumentSourceRegistry,
    private val executionLimiter: ReaderSourceExecutionLimiter = ReaderSourceExecutionLimiter(),
    private val validator: ReaderDocumentValidatorAdapter = ReaderDocumentValidatorAdapter(),
    private val monotonicNanos: () -> Long = System::nanoTime,
) {

    internal suspend fun enabledSources(): Map<PluginId, ReaderDocumentSource> = loadFromSources()

    internal suspend fun executeAttempt(
        attempt: app.openstory.reader.engine.RouteAttempt,
        candidate: ChapterRelease,
        sourceByPlugin: Map<PluginId, ReaderDocumentSource>,
        attemptKind: RemoteAttemptKind = RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
        ownership: ReaderAttemptOwnership,
        onValidCompletion: (ReaderValidCompletion) -> Unit,
        onSourceObservation: suspend (sourceId: PluginId, observation: SourceObservation) -> Unit,
        onLocalInvalidated: suspend (releaseId: ChapterReleaseId, fingerprint: String) -> Unit,
        remotePriority: ReaderRemoteWorkPriority = ReaderRemoteWorkPriority.FOREGROUND,
    ): ReaderAttemptOutcome {
        require(candidate.id == attempt.releaseId) {
            "Reader attempt release must match its candidate."
        }
        require(candidate.pluginId == attempt.sourceId) {
            "Reader attempt source must match its candidate."
        }
        return when (attempt.accessMode) {
            AccessMode.LOCAL -> executeLocalAttempt(
                attempt = attempt,
                candidate = candidate,
                ownership = ownership,
                onValidCompletion = onValidCompletion,
                onSourceObservation = onSourceObservation,
                onLocalInvalidated = onLocalInvalidated,
            )
            AccessMode.REMOTE -> executeRemoteAttempt(
                attempt = attempt,
                candidate = candidate,
                sourceByPlugin = sourceByPlugin,
                attemptKind = attemptKind,
                ownership = ownership,
                onValidCompletion = onValidCompletion,
                onSourceObservation = onSourceObservation,
                remotePriority = remotePriority,
            )
        }
    }

    suspend fun executeAdaptive(
        attempts: List<app.openstory.reader.engine.RouteAttempt>,
        candidatesByRelease: Map<ChapterReleaseId, ChapterRelease>,
        remoteAttemptKinds: Map<ChapterReleaseId, RemoteAttemptKind> = emptyMap(),
        onSourceObservation: suspend (sourceId: PluginId, observation: SourceObservation) -> Unit = { _, _ -> },
        onLocalInvalidated: suspend (releaseId: ChapterReleaseId, fingerprint: String) -> Unit = { _, _ -> },
        onAttempt: suspend (index: Int, attempt: app.openstory.reader.engine.RouteAttempt) -> Unit = { _, _ -> },
        remotePriority: ReaderRemoteWorkPriority = ReaderRemoteWorkPriority.FOREGROUND,
    ): ReaderLoadResult {
        require(attempts.size <= MAX_TOTAL_FOREGROUND_ATTEMPTS) {
            "Reader adaptive route exceeds HES-v1 total attempt ceiling: ${attempts.size}"
        }
        require(attempts.count { it.accessMode == AccessMode.REMOTE } <= MAX_FOREGROUND_REMOTE_ATTEMPTS) {
            "Reader adaptive route exceeds HES-v1 REMOTE attempt ceiling."
        }
        require(attempts.map { it.attemptId }.distinct().size == attempts.size) {
            "Reader adaptive route attempt IDs must be unique."
        }
        require(
            attempts.map { Triple(it.releaseId, it.accessMode, it.localFingerprint) }.distinct().size == attempts.size,
        ) { "Reader adaptive route cannot execute the same release/access/locator twice." }
        if (attempts.isNotEmpty()) {
            require(attempts.first().role == app.openstory.reader.engine.AttemptRole.PRIMARY) {
                "Reader adaptive route first attempt must be PRIMARY."
            }
            require(attempts.drop(1).all { it.role == app.openstory.reader.engine.AttemptRole.FALLBACK }) {
                "Sequential adaptive execution accepts PRIMARY followed only by FALLBACK attempts."
            }
        }

        val failures = mutableListOf<ReaderLoadFailure>()
        val suppressedRemoteSources = mutableSetOf<PluginId>()
        val sourceByPlugin = if (attempts.any { it.accessMode == AccessMode.REMOTE }) {
            loadFromSources()
        } else {
            emptyMap()
        }
        attempts.forEachIndexed { index, attempt ->
            val candidate = checkNotNull(candidatesByRelease[attempt.releaseId]) {
                "Reader adaptive route references release outside candidate set: ${attempt.releaseId.value}"
            }
            require(candidate.pluginId == attempt.sourceId) {
                "Reader adaptive route source mismatch for ${attempt.releaseId.value}."
            }
            if (attempt.accessMode == AccessMode.REMOTE && attempt.sourceId in suppressedRemoteSources) {
                return@forEachIndexed
            }
            onAttempt(index, attempt)
            val result = executeAttempt(
                attempt = attempt,
                candidate = candidate,
                sourceByPlugin = sourceByPlugin,
                attemptKind = remoteAttemptKinds[attempt.releaseId]
                    ?: RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
                ownership = ReaderAttemptOwnership(),
                onValidCompletion = {},
                onSourceObservation = onSourceObservation,
                onLocalInvalidated = onLocalInvalidated,
                remotePriority = remotePriority,
            )
            when (result) {
                is ReaderAttemptOutcome.Success -> return result.completion.loaded
                is ReaderAttemptOutcome.Failure -> {
                    failures += result.failure.toLoadFailure()
                    if (
                        attempt.accessMode == AccessMode.REMOTE &&
                        result.failure.recoveryScope == RecoveryScope.SOURCE_SCOPED
                    ) {
                        suppressedRemoteSources += attempt.sourceId
                    }
                }
            }
        }
        return ReaderLoadResult.Failure(failures)
    }

    private suspend fun loadFromSources(): Map<PluginId, ReaderDocumentSource> =
        sources.enabled().associateBy(ReaderDocumentSource::pluginId)

    private suspend fun executeLocalAttempt(
        attempt: app.openstory.reader.engine.RouteAttempt,
        candidate: ChapterRelease,
        ownership: ReaderAttemptOwnership,
        onValidCompletion: (ReaderValidCompletion) -> Unit,
        onSourceObservation: suspend (PluginId, SourceObservation) -> Unit,
        onLocalInvalidated: suspend (ChapterReleaseId, String) -> Unit,
    ): ReaderAttemptOutcome {
        val fingerprint = checkNotNull(attempt.localFingerprint)
        val release = candidate
        return when (val read = readExact(candidate, fingerprint)) {
            is LocalReadResult.Failure -> {
                ensureOwned(ownership)
                onSourceObservation(release.pluginId, read.value.observation)
                ReaderAttemptOutcome.Failure(read.value)
            }
            is LocalReadResult.Hit -> when (val validation = validator.validateLocal(read.document, fingerprint)) {
                is ReaderDocumentValidation.Valid -> {
                    ensureOwned(ownership)
                    val completion = ReaderValidCompletion(
                        attempt = attempt,
                        loaded = ReaderLoadResult.Success(candidate, validation.document, fromStore = true),
                        completedAtNanos = monotonicNanos(),
                    )
                    onValidCompletion(completion)
                    ensureOwned(ownership)
                    onSourceObservation(release.pluginId, SourceObservation.Success.Local)
                    ReaderAttemptOutcome.Success(completion)
                }
                is ReaderDocumentValidation.Invalid -> {
                    val failure = ReaderAttemptFailure(
                        releaseId = release.id,
                        sourceId = release.pluginId,
                        accessMode = AccessMode.LOCAL,
                        observation = validation.observation,
                        recoveryScope = validation.recoveryScope,
                        code = validation.code,
                        retryable = false,
                    )
                    ensureOwned(ownership)
                    onSourceObservation(release.pluginId, failure.observation)
                    quarantineBestEffort(release.id, fingerprint)
                    ensureOwned(ownership)
                    onLocalInvalidated(release.id, fingerprint)
                    ReaderAttemptOutcome.Failure(failure)
                }
            }
        }
    }

    private suspend fun executeRemoteAttempt(
        attempt: app.openstory.reader.engine.RouteAttempt,
        candidate: ChapterRelease,
        sourceByPlugin: Map<PluginId, ReaderDocumentSource>,
        attemptKind: RemoteAttemptKind,
        ownership: ReaderAttemptOwnership,
        onValidCompletion: (ReaderValidCompletion) -> Unit,
        onSourceObservation: suspend (PluginId, SourceObservation) -> Unit,
        remotePriority: ReaderRemoteWorkPriority,
    ): ReaderAttemptOutcome {
        val release = candidate
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
            ensureOwned(ownership)
            onSourceObservation(sourceId, failure.observation)
            return ReaderAttemptOutcome.Failure(failure)
        }

        val startedNanos = monotonicNanos()
        val fetched = fetch(source, candidate, remotePriority)
        val completedNanos = monotonicNanos()
        val latencyMillis = elapsedMillis(startedNanos, completedNanos)
        return when (fetched) {
            is ReaderSourceResult.Success -> when (
                val validation = validator.validateRemote(fetched.document, attemptKind)
            ) {
                is ReaderDocumentValidation.Valid -> {
                    ensureOwned(ownership)
                    val completion = ReaderValidCompletion(
                        attempt = attempt,
                        loaded = ReaderLoadResult.Success(candidate, validation.document, fromStore = false),
                        completedAtNanos = completedNanos,
                    )
                    onValidCompletion(completion)
                    ensureOwned(ownership)
                    onSourceObservation(
                        sourceId,
                        SourceObservation.Success.Remote(attemptKind, latencyMillis),
                    )
                    ensureOwned(ownership)
                    persistBestEffort(release.id, validation.document)
                    ReaderAttemptOutcome.Success(completion)
                }
                is ReaderDocumentValidation.Invalid -> {
                    val failure = ReaderAttemptFailure(
                        releaseId = release.id,
                        sourceId = sourceId,
                        accessMode = AccessMode.REMOTE,
                        observation = validation.observation,
                        recoveryScope = validation.recoveryScope,
                        code = validation.code,
                        retryable = false,
                        remoteAttemptKind = attemptKind,
                    )
                    ensureOwned(ownership)
                    onSourceObservation(sourceId, failure.observation)
                    ReaderAttemptOutcome.Failure(failure)
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
                ensureOwned(ownership)
                onSourceObservation(sourceId, failure.observation)
                ReaderAttemptOutcome.Failure(failure)
            }
        }
    }

    private fun ensureOwned(ownership: ReaderAttemptOwnership) {
        if (!ownership.isOwned()) {
            throw CancellationException("Reader attempt observation ownership was cancelled.")
        }
    }

    private suspend fun readExact(
        candidate: ChapterRelease,
        fingerprint: String,
    ): LocalReadResult = readLocal(candidate) {
        store.read(candidate.id, fingerprint)
    }

    private suspend fun readLocal(
        candidate: ChapterRelease,
        read: suspend () -> ReaderDocument?,
    ): LocalReadResult = try {
        val document = read()
        if (document == null) {
            LocalReadResult.Failure(
                localFailure(
                    candidate = candidate,
                    observation = SourceObservation.LocalFailure.MissingBlob,
                    recoveryScope = RecoveryScope.LOCAL_SCOPED,
                    code = "reader.local_blob_missing",
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
                code = "reader.local_read_failed",
                retryable = true,
            ),
        )
    }

    private fun localFailure(
        candidate: ChapterRelease,
        observation: SourceObservation,
        recoveryScope: RecoveryScope,
        code: String,
        retryable: Boolean,
    ) = ReaderAttemptFailure(
        releaseId = candidate.id,
        sourceId = candidate.pluginId,
        accessMode = AccessMode.LOCAL,
        observation = observation,
        recoveryScope = recoveryScope,
        code = code,
        retryable = retryable,
    )

    private suspend fun fetch(
        source: ReaderDocumentSource,
        candidate: ChapterRelease,
        remotePriority: ReaderRemoteWorkPriority,
    ): ReaderSourceResult = try {
        executionLimiter.withRemotePermit(source.pluginId, remotePriority) {
            source.fetch(candidate)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // This catch is exactly at the proven source invocation boundary, so classifier may use the
        // transport fallback for the synthesized retryable code.
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


    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val MAX_FOREGROUND_REMOTE_ATTEMPTS = 4
        const val MAX_TOTAL_FOREGROUND_ATTEMPTS = 7
    }
}
