package app.openstory.reader.routing

import app.openstory.reader.content.ReaderDocumentSourceRegistry
import app.openstory.reader.content.ReaderDocumentStore
import app.openstory.reader.content.ReaderLoadFailure
import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.engine.ReaderRouteEngine
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository

/**
 * M2 real-target coordinator. Adaptive health/access/hysteresis/hedging remain deliberately disabled.
 */
class ReaderRouteCoordinator(
    store: ReaderDocumentStore,
    sources: ReaderDocumentSourceRegistry,
    progress: ReadingProgressRepository,
) {
    private val executor = ReaderRouteExecutor(store, sources)
    private val assembler = RouteSnapshotAssembler(progress)
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

        var latestAttempt: RouteAttempt? = null
        val loaded = executor.executeCompatibility(
            orderedCandidates = orderedCandidates,
            expectedFingerprints = assembled.expectedFingerprints,
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
        releaseId: app.openstory.common.id.ChapterReleaseId,
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
