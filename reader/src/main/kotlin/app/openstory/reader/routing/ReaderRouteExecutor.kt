package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterRelease
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderDocumentSource
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentReadResult
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
    private val executionLimiter: ReaderSourceExecutionLimiter,
    private val validator: ReaderDocumentValidatorAdapter = ReaderDocumentValidatorAdapter(),
    private val monotonicNanos: () -> Long = System::nanoTime,
) {

    internal suspend fun enabledSources(): Map<PluginId, ReaderDocumentSource> = loadFromSources()

    internal suspend fun executeAttempt(
        identity: ReaderAttemptIdentity,
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
        require(identity.attemptId == attempt.attemptId) {
            "Reader attempt identity must match its route attempt."
        }
        var publishedCompletion: ReaderValidCompletion? = null
        val effect = executeAttemptEffect(
            attempt = attempt,
            candidate = candidate,
            sourceByPlugin = sourceByPlugin,
            attemptKind = attemptKind,
            ownership = ownership,
            onSourceObservation = onSourceObservation,
            onLocalInvalidated = onLocalInvalidated,
            remotePriority = remotePriority,
            onValidEffect = { success ->
                val completion = ReaderValidCompletion(
                    identity = identity,
                    attempt = attempt,
                    loaded = success.loaded,
                    completedAtNanos = success.completedAtNanos,
                )
                onValidCompletion(completion)
                publishedCompletion = completion
            },
        )
        return when (effect) {
            is ReaderAttemptEffectOutcome.Success -> ReaderAttemptOutcome.Success(
                checkNotNull(publishedCompletion) {
                    "Foreground Reader success must publish its valid completion."
                },
            )
            is ReaderAttemptEffectOutcome.Failure -> ReaderAttemptOutcome.Failure(
                identity = identity,
                failure = effect.failure,
            )
        }
    }

    private suspend fun executeAttemptEffect(
        attempt: app.openstory.reader.engine.RouteAttempt,
        candidate: ChapterRelease,
        sourceByPlugin: Map<PluginId, ReaderDocumentSource>,
        attemptKind: RemoteAttemptKind,
        ownership: ReaderAttemptOwnership,
        onSourceObservation: suspend (sourceId: PluginId, observation: SourceObservation) -> Unit,
        onLocalInvalidated: suspend (releaseId: ChapterReleaseId, fingerprint: String) -> Unit,
        remotePriority: ReaderRemoteWorkPriority,
        onValidEffect: (ReaderAttemptEffectOutcome.Success) -> Unit = {},
    ): ReaderAttemptEffectOutcome {
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
                onSourceObservation = onSourceObservation,
                onLocalInvalidated = onLocalInvalidated,
                onValidEffect = onValidEffect,
            )
            AccessMode.REMOTE -> executeRemoteAttempt(
                attempt = attempt,
                candidate = candidate,
                sourceByPlugin = sourceByPlugin,
                attemptKind = attemptKind,
                ownership = ownership,
                onSourceObservation = onSourceObservation,
                remotePriority = remotePriority,
                onValidEffect = onValidEffect,
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
        ReaderRouteRuntimeGuard.validateSequential(attempts)

        val failures = mutableListOf<ReaderLoadFailure>()
        val suppressedRemoteSources = mutableSetOf<PluginId>()
        var sourceByPlugin: Map<PluginId, ReaderDocumentSource>? = null
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
            val availableSources = if (attempt.accessMode == AccessMode.REMOTE) {
                sourceByPlugin ?: loadFromSources().also { sourceByPlugin = it }
            } else {
                emptyMap()
            }
            onAttempt(index, attempt)
            val result = executeAttemptEffect(
                attempt = attempt,
                candidate = candidate,
                sourceByPlugin = availableSources,
                attemptKind = remoteAttemptKinds[attempt.releaseId]
                    ?: RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
                ownership = ReaderAttemptOwnership(),
                onSourceObservation = onSourceObservation,
                onLocalInvalidated = onLocalInvalidated,
                remotePriority = remotePriority,
            )
            when (result) {
                is ReaderAttemptEffectOutcome.Success -> return result.loaded
                is ReaderAttemptEffectOutcome.Failure -> {
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
        onSourceObservation: suspend (PluginId, SourceObservation) -> Unit,
        onLocalInvalidated: suspend (ChapterReleaseId, String) -> Unit,
        onValidEffect: (ReaderAttemptEffectOutcome.Success) -> Unit,
    ): ReaderAttemptEffectOutcome {
        val fingerprint = checkNotNull(attempt.localFingerprint)
        val release = candidate
        return when (val read = readExact(candidate, fingerprint)) {
            is LocalReadResult.Failure -> {
                ensureOwned(ownership)
                onSourceObservation(release.pluginId, read.value.observation)
                ReaderAttemptEffectOutcome.Failure(read.value)
            }
            is LocalReadResult.ConfirmedCorruption -> {
                ensureOwned(ownership)
                onSourceObservation(release.pluginId, read.value.observation)
                quarantineBestEffort(release.id, fingerprint)
                ensureOwned(ownership)
                onLocalInvalidated(release.id, fingerprint)
                ReaderAttemptEffectOutcome.Failure(read.value)
            }
            is LocalReadResult.Hit -> when (val validation = validator.validateLocal(read.document, fingerprint)) {
                is ReaderDocumentValidation.Valid -> {
                    ensureOwned(ownership)
                    val completedAtNanos = monotonicNanos()
                    val loaded = ReaderLoadResult.Success(
                        release = candidate,
                        document = validation.document,
                        fromStore = true,
                    )
                    val success = ReaderAttemptEffectOutcome.Success(
                        loaded = loaded,
                        completedAtNanos = completedAtNanos,
                    )
                    onValidEffect(success)
                    ensureOwned(ownership)
                    onSourceObservation(release.pluginId, SourceObservation.Success.Local)
                    success
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
                    ReaderAttemptEffectOutcome.Failure(failure)
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
        onSourceObservation: suspend (PluginId, SourceObservation) -> Unit,
        remotePriority: ReaderRemoteWorkPriority,
        onValidEffect: (ReaderAttemptEffectOutcome.Success) -> Unit,
    ): ReaderAttemptEffectOutcome {
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
            return ReaderAttemptEffectOutcome.Failure(failure)
        }

        val startedNanos = monotonicNanos()
        val fetched = fetch(source, candidate, remotePriority)
        val fetchCompletedNanos = monotonicNanos()
        val latencyMillis = elapsedMillis(startedNanos, fetchCompletedNanos)
        return when (fetched) {
            is ReaderSourceResult.Success -> when (
                val validation = validator.validateRemote(fetched.document, attemptKind)
            ) {
                is ReaderDocumentValidation.Valid -> {
                    ensureOwned(ownership)
                    val validCompletedAtNanos = monotonicNanos()
                    val success = ReaderAttemptEffectOutcome.Success(
                        loaded = ReaderLoadResult.Success(candidate, validation.document, fromStore = false),
                        completedAtNanos = validCompletedAtNanos,
                    )
                    onValidEffect(success)
                    ensureOwned(ownership)
                    onSourceObservation(
                        sourceId,
                        SourceObservation.Success.Remote(attemptKind, latencyMillis),
                    )
                    ensureOwned(ownership)
                    persistBestEffort(release.id, validation.document)
                    success
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
                    ReaderAttemptEffectOutcome.Failure(failure)
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
                ReaderAttemptEffectOutcome.Failure(failure)
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
    ): LocalReadResult = try {
        when (val result = store.readResult(candidate.id, fingerprint)) {
            is ReaderDocumentReadResult.Hit -> LocalReadResult.Hit(result.document)
            ReaderDocumentReadResult.Missing -> LocalReadResult.Failure(
                localFailure(
                    candidate = candidate,
                    observation = SourceObservation.LocalFailure.MissingBlob,
                    recoveryScope = RecoveryScope.LOCAL_SCOPED,
                    code = "reader.local_blob_missing",
                    retryable = false,
                ),
            )
            ReaderDocumentReadResult.FingerprintOrDecodeMismatch -> LocalReadResult.ConfirmedCorruption(
                localFailure(
                    candidate = candidate,
                    observation = SourceObservation.LocalFailure.FingerprintOrDecodeMismatch,
                    recoveryScope = RecoveryScope.LOCAL_SCOPED,
                    code = "reader.local_fingerprint_or_decode_mismatch",
                    retryable = false,
                ),
            )
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
        data class ConfirmedCorruption(val value: ReaderAttemptFailure) : LocalReadResult
    }

    private sealed interface ReaderAttemptEffectOutcome {
        data class Success(
            val loaded: ReaderLoadResult.Success,
            val completedAtNanos: Long,
        ) : ReaderAttemptEffectOutcome

        data class Failure(
            val failure: ReaderAttemptFailure,
        ) : ReaderAttemptEffectOutcome
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
