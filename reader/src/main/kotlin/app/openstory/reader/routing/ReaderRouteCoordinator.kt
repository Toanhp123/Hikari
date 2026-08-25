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
import app.openstory.reader.engine.RoutingIntent
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlinx.coroutines.CancellationException

/**
 * Adaptive Reader route coordinator. The pure engine owns eligibility/ranking/hysteresis/route
 * construction; this class materializes process facts, owns probe leases, and executes exactly the
 * bounded route returned by the engine. M5 prefetch reuses this path with [RoutingIntent.PREFETCH]
 * and never enters the visible foreground commit gate; M6 permits one foreground hedge.
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
    private val executionScheduler: ReaderExecutionScheduler = DefaultReaderExecutionScheduler(),
) {
    private val healthRegistry = healthRegistry
    private val executor = ReaderRouteExecutor(
        store = store,
        sources = sources,
        executionLimiter = executionLimiter,
        monotonicNanos = executionScheduler::monotonicNanos,
    )
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
                decision.competitiveSet.hedge?.let(::add)
                addAll(decision.recoveryChain)
            }
            val candidateByRelease = assembled.candidates.associateBy { it.id }
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
            val attemptIndex = plannedAttempts.mapIndexed { index, attempt ->
                attempt.attemptId to index
            }.toMap()
            val sourceByPlugin = if (plannedAttempts.any { it.accessMode == AccessMode.REMOTE }) {
                executor.enabledSources()
            } else {
                emptyMap()
            }
            val primary = checkNotNull(decision.competitiveSet.primary)
            val execution = ReaderCompetitiveExecution(
                scheduler = executionScheduler,
                executeAttempt = { attempt, ownership, onValidCompletion ->
                    val candidate = checkNotNull(candidateByRelease[attempt.releaseId])
                    executor.executeAttempt(
                        attempt = attempt,
                        candidate = candidate,
                        sourceByPlugin = sourceByPlugin,
                        attemptKind = probeAttemptKinds[attempt.releaseId]
                            ?: RemoteAttemptKind.NORMAL_REMOTE_ATTEMPT,
                        ownership = ownership,
                        onValidCompletion = onValidCompletion,
                        onSourceObservation = { sourceId, observation ->
                            recordHealth(sourceId, observation)
                            if (observation is SourceObservation.TransportFailure.Connection) {
                                hardInvalidateIfDefinitelyOffline(
                                    session = session,
                                    context = context,
                                    attempts = plannedAttempts,
                                    completedAttemptIndex = attemptIndex.getValue(attempt.attemptId),
                                )
                            }
                        },
                        onLocalInvalidated = { releaseId, fingerprint ->
                            // The current deterministic recovery chain gets first chance to recover.
                            session.markKnownInvalidLocal(context, releaseId, fingerprint)
                        },
                    )
                },
                onAttemptStarted = { attempt, recovering ->
                    if (
                        attempt.accessMode == AccessMode.REMOTE &&
                        remoteAttemptHardInvalidated(attempt, probeSourceIds)
                    ) {
                        session.hardInvalidateIfCurrent(context)
                        throw ReaderRoutePlanInvalidatedException()
                    }
                    val marked = if (
                        attempt.role == app.openstory.reader.engine.AttemptRole.HEDGE && !recovering
                    ) {
                        session.markCompeting(
                            context = context,
                            primaryAttemptId = primary.attemptId,
                            hedgeAttemptId = attempt.attemptId,
                        )
                    } else {
                        session.markAttempt(
                            context = context,
                            attemptId = attempt.attemptId,
                            recovering = recovering,
                        )
                    }
                    check(marked) { "Reader adaptive attempt belongs to a superseded plan." }
                },
                onCompetitionLoser = { attempt ->
                    recordHealth(attempt.sourceId, SourceObservation.Cancellation.HedgeLoser)
                },
            ).execute(
                primary = primary,
                hedgeDirective = decision.hedgeDirective,
                recoveryChain = decision.recoveryChain,
            )
            return execution.completion?.let { completion ->
                session.markValidating(context, completion.attempt.attemptId)
                committed(context, assembled, completion.loaded)
            } ?: exhausted(
                context,
                execution.failures.map(ReaderAttemptFailure::toLoadFailure),
            )
        } finally {
            heldProbeLeases.forEach(ReaderHalfOpenProbeLease::release)
        }
    }


    internal suspend fun executePrefetch(
        session: ReaderRouteSession,
        context: ReaderRoutePlanningContext,
    ) {
        val assembled = assembler.assemble(context, RoutingIntent.PREFETCH) ?: return
        val heldProbeLeases = assembled.probeLeases.toMutableList()
        try {
            val decision = engine.plan(assembled.snapshot, assembled.policy)
            val plannedAttempts = buildList {
                decision.competitiveSet.primary?.let(::add)
                addAll(decision.recoveryChain)
            }
            if (plannedAttempts.isEmpty()) return

            val candidateByRelease = assembled.candidates.associateBy { it.id }
            plannedAttempts.forEach { attempt ->
                checkNotNull(candidateByRelease[attempt.releaseId]) {
                    "Engine planned prefetch release ${attempt.releaseId.value} outside the assembled candidate set."
                }
            }
            releaseUnusedProbeLeases(heldProbeLeases, plannedAttempts)
            val probeSourceIds = heldProbeLeases.mapTo(linkedSetOf()) { it.key.sourceId }
            val probeAttemptKinds = firstPlannedProbeBySource(plannedAttempts, probeSourceIds)

            try {
                executor.executeAdaptive(
                    attempts = plannedAttempts,
                    candidatesByRelease = candidateByRelease,
                    remoteAttemptKinds = probeAttemptKinds,
                    onSourceObservation = { sourceId, observation ->
                        recordHealth(sourceId, observation)
                    },
                    onLocalInvalidated = { releaseId, fingerprint ->
                        session.markKnownInvalidLocal(releaseId, fingerprint)
                    },
                    onAttempt = { _, attempt ->
                        if (
                            attempt.accessMode == AccessMode.REMOTE &&
                            (
                                !prefetchRemoteStillPermitted() ||
                                    remoteAttemptHardInvalidated(attempt, probeSourceIds)
                            )
                        ) {
                            throw ReaderRoutePlanInvalidatedException()
                        }
                    },
                    remotePriority = ReaderRemoteWorkPriority.PREFETCH,
                )
            } catch (_: ReaderRoutePlanInvalidatedException) {
                // Prefetch never owns a visible commit. A stale plan is simply abandoned.
            }
        } finally {
            heldProbeLeases.forEach(ReaderHalfOpenProbeLease::release)
        }
    }

    private suspend fun prefetchRemoteStillPermitted(): Boolean = try {
        networkFacts.current() == ReaderNetworkState.UNMETERED
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
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
        val release = loaded.release
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
