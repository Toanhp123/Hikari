package app.openstory.reader.routing

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderLoadFailure
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.engine.ReaderRouteEngine
import app.openstory.reader.engine.RemoteAttemptKind
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.engine.SourceObservation
import app.openstory.reader.engine.SourceOperationKey
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository

/**
 * M3 real-target coordinator. Health/availability are assembled and observed, but M4 adaptive
 * eligibility/scoring/hysteresis/hedging remain deliberately disabled.
 */
class ReaderRouteCoordinator(
    store: ReaderDocumentStore,
    sources: ReaderDocumentSourceRegistry,
    progress: ReadingProgressRepository,
    sourceAvailability: ReaderSourceAvailability = ReaderSourceAvailability {
        sources.enabled().mapTo(linkedSetOf()) { it.pluginId }
    },
    healthRegistry: ReaderSourceHealthRegistry = ReaderSourceHealthRegistry(),
    executionLimiter: ReaderSourceExecutionLimiter = ReaderSourceExecutionLimiter(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val healthRegistry = healthRegistry
    private val executor = ReaderRouteExecutor(store, sources, executionLimiter)
    private val assembler = RouteSnapshotAssembler(
        progress = progress,
        sourceAvailability = sourceAvailability,
        healthRegistry = healthRegistry,
        executionLimiter = executionLimiter,
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
        try {
            val decision = engine.plan(assembled.snapshot, assembled.policy)
            val plannedAttempts = buildList {
                decision.competitiveSet.primary?.let(::add)
                addAll(decision.recoveryChain)
            }
            val candidateByRelease = assembled.candidates.associateBy { it.release.id }
            val orderedCandidates = plannedAttempts.map { attempt ->
                checkNotNull(candidateByRelease[attempt.releaseId]) {
                    "Engine planned release ${attempt.releaseId.value} outside the assembled candidate set."
                }
            }
            if (orderedCandidates.isEmpty()) {
                return ReaderForegroundResult.Exhausted(
                    identity = context.foregroundIdentity,
                    code = READER_EMPTY,
                    retryable = false,
                    attempts = emptyList(),
                )
            }

            val probeSourceIds = assembled.probeLeases.mapTo(linkedSetOf()) { it.key.sourceId }
            val probeAttemptKinds = firstPlannedProbeBySource(plannedAttempts, probeSourceIds)
            var latestAttempt: RouteAttempt? = null
            val loaded = executor.executeCompatibility(
                orderedCandidates = orderedCandidates,
                expectedFingerprints = assembled.expectedFingerprints,
                remoteAttemptKinds = probeAttemptKinds,
                onSourceObservation = { sourceId, observation ->
                    recordHealth(sourceId, observation)
                },
                onLocalInvalidated = { releaseId, fingerprint ->
                    session.markKnownInvalidLocal(context, releaseId, fingerprint)
                },
            ) { index, _ ->
                val attempt = plannedAttempts[index]
                latestAttempt = attempt
                session.markAttempt(
                    context = context,
                    attemptId = attempt.attemptId,
                    recovering = index > 0,
                )
            }
            return when (loaded) {
                is ReaderLoadResult.Success -> {
                    val attempt = latestAttempt ?: plannedAttempts.first()
                    session.markValidating(context, attempt.attemptId)
                    committed(context, assembled, loaded)
                }
                is ReaderLoadResult.Failure -> exhausted(context, loaded.attempts)
            }
        } finally {
            assembled.probeLeases.forEach(ReaderHalfOpenProbeLease::release)
        }
    }

    private suspend fun recordHealth(sourceId: PluginId, observation: SourceObservation) {
        healthRegistry.record(
            key = SourceOperationKey(sourceId),
            observation = observation,
            nowEpochMillis = nowEpochMillis(),
        )
    }

    private fun firstPlannedProbeBySource(
        attempts: List<RouteAttempt>,
        probeSourceIds: Set<PluginId>,
    ): Map<ChapterReleaseId, RemoteAttemptKind> {
        val assigned = mutableSetOf<PluginId>()
        return buildMap {
            attempts.forEach { attempt ->
                if (attempt.sourceId in probeSourceIds && assigned.add(attempt.sourceId)) {
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
