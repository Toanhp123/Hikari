package app.openstory.reader.routing

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderLoadFailure
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.ReaderRouteEngine
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.CancellationException

/**
 * M4 adaptive foreground coordinator. The pure engine owns eligibility/ranking/hysteresis/route
 * construction; this class materializes process facts, owns probe leases, and executes exactly the
 * bounded route returned by the engine. Hedging/prefetch remain disabled until later phases.
 */
class ReaderRouteCoordinator(
    store: ReaderDocumentStore,
    sources: ReaderDocumentSourceRegistry,
    progress: ReadingProgressRepository,
    private val sourceAvailability: ReaderSourceAvailability = ReaderSourceAvailability {
        sources.enabled().mapTo(linkedSetOf()) { it.pluginId }
    },
    healthRegistry: ReaderSourceHealthRegistry = ReaderSourceHealthRegistry(),
    executionLimiter: ReaderSourceExecutionLimiter = ReaderSourceExecutionLimiter(),
    cacheFacts: ReaderCacheFactsPort = ReaderCacheFactsPort { releaseIds, _ ->
        releaseIds.associateWith { ReaderLocalCacheFact.Unknown }
    },
    private val networkFacts: ReaderNetworkFactsPort = ReaderNetworkFactsPort { ReaderNetworkState.UNKNOWN },
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val healthRegistry = healthRegistry
    private val executor = ReaderRouteExecutor(store, sources, executionLimiter)
    private val assembler = RouteSnapshotAssembler(
        progress = progress,
        sourceAvailability = sourceAvailability,
        healthRegistry = healthRegistry,
        executionLimiter = executionLimiter,
        cacheFacts = cacheFacts,
        networkFacts = networkFacts,
        nowEpochMillis = nowEpochMillis,
    )
    private val engine = ReaderRouteEngine.v1()

    internal suspend fun execute(
        session: ReaderRouteSession,
        context: ReaderRouteExecutionContext,
    ): ReaderForegroundResult {
        val assembled = assembler.assemble(context) ?: return ReaderForegroundResult.Exhausted(
            identity = context.foregroundIdentity,
            code = READER_CHAPTER_NOT_FOUND,
            retryable = false,
            attempts = emptyList(),
        )
        val heldProbeLeases = assembled.probeLeases.toMutableList()
        try {
            val decision = engine.plan(assembled.snapshot, assembled.policy)
            val plannedAttempts = buildList {
                decision.competitiveSet.primary?.let(::add)
                addAll(decision.recoveryChain)
            }
            val candidateByRelease = assembled.candidates.associateBy { it.release.id }
            plannedAttempts.forEach { attempt ->
                checkNotNull(candidateByRelease[attempt.releaseId]) {
                    "Engine planned release ${attempt.releaseId.value} outside the assembled candidate set."
                }
            }

            releaseUnusedProbeLeases(heldProbeLeases, plannedAttempts)

            if (plannedAttempts.isEmpty()) {
                return ReaderForegroundResult.Exhausted(
                    identity = context.foregroundIdentity,
                    code = READER_EMPTY,
                    retryable = false,
                    attempts = emptyList(),
                )
            }
            val winnerReleaseId = checkNotNull(decision.trace.finalWinnerReleaseId) {
                "A non-empty adaptive route requires a final winner release."
            }
            if (!session.recordPlannedRoute(context, winnerReleaseId, plannedAttempts)) {
                return ReaderForegroundResult.Superseded(context.foregroundIdentity)
            }

            val probeSourceIds = heldProbeLeases.mapTo(linkedSetOf()) { it.key.sourceId }
            val probeAttemptKinds = firstPlannedProbeBySource(plannedAttempts, probeSourceIds)
            var latestAttempt: RouteAttempt? = null
            var currentAttemptIndex = -1
            val loaded = executor.executeAdaptive(
                attempts = plannedAttempts,
                candidatesByRelease = candidateByRelease,
                remoteAttemptKinds = probeAttemptKinds,
                onSourceObservation = { sourceId, observation ->
                    recordHealth(sourceId, observation)
                    if (observation is SourceObservation.TransportFailure.Connection) {
                        hardInvalidateIfDefinitelyOffline(
                            session = session,
                            context = context,
                            attempts = plannedAttempts,
                            completedAttemptIndex = currentAttemptIndex,
                        )
                    }
                },
                onLocalInvalidated = { releaseId, fingerprint ->
                    // The current deterministic recovery chain gets first chance to recover. The
                    // exact locator is remembered for any later hard replan/new generation.
                    session.markKnownInvalidLocal(context, releaseId, fingerprint)
                },
                onAttempt = { index, attempt ->
                    if (
                        attempt.accessMode == AccessMode.REMOTE &&
                        remoteAttemptHardInvalidated(attempt, probeSourceIds)
                    ) {
                        session.hardInvalidateIfCurrent(context)
                        throw ReaderRoutePlanInvalidatedException()
                    }
                    currentAttemptIndex = index
                    latestAttempt = attempt
                    check(
                        session.markAttempt(
                            context = context,
                            attemptId = attempt.attemptId,
                            recovering = index > 0,
                        ),
                    ) { "Reader adaptive attempt belongs to a superseded plan." }
                },
            )
            return when (loaded) {
                is ReaderLoadResult.Success -> {
                    val attempt = latestAttempt ?: plannedAttempts.first()
                    session.markValidating(context, attempt.attemptId)
                    committed(context, assembled, loaded)
                }
                is ReaderLoadResult.Failure -> exhausted(context, loaded.attempts)
            }
        } finally {
            heldProbeLeases.forEach(ReaderHalfOpenProbeLease::release)
        }
    }

    private suspend fun remoteAttemptHardInvalidated(
        attempt: RouteAttempt,
        heldProbeSourceIds: Set<PluginId>,
    ): Boolean {
        val enabledSourceIds = try {
            sourceAvailability.enabledPluginIds()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Lack of a fresh availability observation is not proof that a previously planned
            // remote path became ineligible. Keep executing the immutable plan conservatively.
            null
        }
        if (enabledSourceIds != null && attempt.sourceId !in enabledSourceIds) return true

        val now = nowEpochMillis()
        require(now >= 0L) { "Reader route health preflight clock must be non-negative." }
        val health = healthRegistry.snapshot(SourceOperationKey(attempt.sourceId), now)
        return when (health.state.circuitState) {
            app.openstory.reader.engine.CircuitState.CLOSED -> false
            app.openstory.reader.engine.CircuitState.OPEN -> true
            app.openstory.reader.engine.CircuitState.HALF_OPEN -> attempt.sourceId !in heldProbeSourceIds
        }
    }

    private suspend fun recordHealth(sourceId: PluginId, observation: SourceObservation) {
        healthRegistry.record(
            key = SourceOperationKey(sourceId),
            observation = observation,
            nowEpochMillis = nowEpochMillis(),
        )
    }

    private suspend fun hardInvalidateIfDefinitelyOffline(
        session: ReaderRouteSession,
        context: ReaderRouteExecutionContext,
        attempts: List<RouteAttempt>,
        completedAttemptIndex: Int,
    ) {
        if (completedAttemptIndex < 0) return
        val hasFutureLocalPath = attempts
            .drop(completedAttemptIndex + 1)
            .any { it.accessMode == AccessMode.LOCAL }
        if (hasFutureLocalPath) return
        val state = try {
            networkFacts.current()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ReaderNetworkState.UNKNOWN
        }
        if (state == ReaderNetworkState.OFFLINE) {
            session.hardInvalidateIfCurrent(context)
        }
    }

    private fun releaseUnusedProbeLeases(
        heldLeases: MutableList<ReaderHalfOpenProbeLease>,
        attempts: List<RouteAttempt>,
    ) {
        val plannedRemoteSources = attempts
            .asSequence()
            .filter { it.accessMode == AccessMode.REMOTE }
            .mapTo(linkedSetOf()) { it.sourceId }
        val unused = heldLeases.filter { it.key.sourceId !in plannedRemoteSources }
        unused.forEach { lease ->
            lease.release()
            heldLeases.remove(lease)
        }
    }

    private fun firstPlannedProbeBySource(
        attempts: List<RouteAttempt>,
        probeSourceIds: Set<PluginId>,
    ): Map<ChapterReleaseId, RemoteAttemptKind> {
        val assigned = mutableSetOf<PluginId>()
        return buildMap {
            attempts.forEach { attempt ->
                if (
                    attempt.accessMode == AccessMode.REMOTE &&
                    attempt.sourceId in probeSourceIds &&
                    assigned.add(attempt.sourceId)
                ) {
                    put(attempt.releaseId, RemoteAttemptKind.HALF_OPEN_PROBE)
                }
            }
        }
    }

    private fun committed(
        context: ReaderRouteExecutionContext,
        assembled: AssembledRouteSnapshot,
        loaded: ReaderLoadResult.Success,
    ): ReaderForegroundResult.Committed {
        val release = loaded.release.release
        val restoration = exactRestoration(
            progress = assembled.restoredProgress,
            releaseId = release.id,
            documentFingerprint = loaded.document.fingerprint,
        )
        return ReaderForegroundResult.Committed(
            identity = context.foregroundIdentity,
            chapterGroup = assembled.targetGroup,
            release = release,
            document = loaded.document,
            fromLocal = loaded.fromStore,
            previousChapterId = context.chapterGroups.getOrNull(assembled.targetIndex - 1)?.chapter?.id,
            nextChapterId = context.chapterGroups.getOrNull(assembled.targetIndex + 1)?.chapter?.id,
            restoration = restoration,
        )
    }

    private fun exactRestoration(
        progress: ReadingProgress?,
        releaseId: ChapterReleaseId,
        documentFingerprint: String,
    ): ReaderExactRestoration? {
        if (progress?.releaseId != releaseId || progress.contentFingerprint != documentFingerprint) {
            return null
        }
        return ReaderExactRestoration(
            blockId = progress.position.blockId,
            characterOffset = progress.position.characterOffset,
            progressFraction = progress.position.fraction,
        )
    }

    private fun exhausted(
        context: ReaderRouteExecutionContext,
        attempts: List<ReaderLoadFailure>,
    ): ReaderForegroundResult.Exhausted {
        val failure = attempts.firstOrNull { it.retryable } ?: attempts.firstOrNull()
        return ReaderForegroundResult.Exhausted(
            identity = context.foregroundIdentity,
            code = failure?.code ?: READER_EMPTY,
            retryable = failure?.retryable ?: false,
            attempts = attempts,
        )
    }

    private companion object {
        const val READER_CHAPTER_NOT_FOUND = "reader.chapter_not_found"
        const val READER_EMPTY = "reader.no_release_available"
    }
}

private class ReaderRoutePlanInvalidatedException : IllegalStateException(
    "Reader route plan was hard-invalidated before a remote effect started.",
)
