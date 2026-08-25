package app.openstory.reader.routing

import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.content.ReaderLoadFailure
import app.openstory.reader.document.ReaderDocument
import app.openstory.reader.engine.ReaderChapterGraphRevision
import app.openstory.reader.engine.ReaderPlanRevision
import app.openstory.reader.preferences.ReaderPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

private const val READER_LOAD_FAILED = "reader.load_failed"

/** User intent only. Session-owned story/graph/preferences/continuity never ride on each request. */
data class ReaderForegroundIntent(
    val targetChapterId: CanonicalChapterId,
    val explicitReleaseId: ChapterReleaseId? = null,
)

data class ReaderExactRestoration(
    val blockId: String,
    val characterOffset: Int,
    val progressFraction: Float,
) {
    init {
        require(blockId.isNotBlank()) { "Restoration block ID must not be blank." }
        require(characterOffset >= 0) { "Restoration character offset must not be negative." }
        require(progressFraction.isFinite() && progressFraction in 0f..1f) {
            "Restoration progress fraction must be between zero and one."
        }
    }
}

data class ReaderForegroundIdentity(
    val sessionId: ReaderSessionId,
    val generationId: ReaderGenerationId,
    val targetChapterId: CanonicalChapterId,
)

sealed interface ReaderForegroundResult {
    val identity: ReaderForegroundIdentity

    data class Committed(
        override val identity: ReaderForegroundIdentity,
        val chapterGroup: CanonicalChapterGroup,
        val release: ChapterRelease,
        val document: ReaderDocument,
        val fromLocal: Boolean,
        val previousChapterId: CanonicalChapterId?,
        val nextChapterId: CanonicalChapterId?,
        val restoration: ReaderExactRestoration?,
    ) : ReaderForegroundResult

    data class Exhausted(
        override val identity: ReaderForegroundIdentity,
        val code: String,
        val retryable: Boolean,
        val attempts: List<ReaderLoadFailure>,
    ) : ReaderForegroundResult {
        init {
            require(code.isNotBlank()) { "Reader exhaustion code must not be blank." }
        }
    }

    data class Superseded(
        override val identity: ReaderForegroundIdentity,
    ) : ReaderForegroundResult
}

internal data class ReaderRouteExecutionContext(
    val storyId: StoryId,
    val identity: ReaderExecutionIdentity,
    val chapterGraphRevision: ReaderChapterGraphRevision,
    val chapterGroups: List<CanonicalChapterGroup>,
    val preferences: ReaderPreferences,
    val committedIdentity: ReaderCommittedIdentity?,
    val explicitReleaseId: ChapterReleaseId?,
) {
    val foregroundIdentity: ReaderForegroundIdentity
        get() = identity.toForegroundIdentity()
}

internal fun interface ReaderRouteExecutionDelegate {
    suspend fun execute(
        session: ReaderRouteSession,
        context: ReaderRouteExecutionContext,
    ): ReaderForegroundResult
}

class ReaderRouteSession internal constructor(
    val storyId: StoryId,
    val sessionId: ReaderSessionId,
    private val delegate: ReaderRouteExecutionDelegate,
) {
    private data class ActiveExecution(
        val generationId: ReaderGenerationId,
        val targetChapterId: CanonicalChapterId,
        val explicitReleaseId: ChapterReleaseId?,
        val planRevision: ReaderPlanRevision,
    )

    private val stateLock = Any()
    private var nextGenerationValue = 1L
    private var activeExecution: ActiveExecution? = null
    private var latestChapterGroups: List<CanonicalChapterGroup>? = null
    private var latestPreferences: ReaderPreferences? = null
    private val firstChapterGraph = CompletableDeferred<Unit>()
    private val firstRoutingPreferences = CompletableDeferred<Unit>()
    private var chapterGraphRevision = ReaderChapterGraphRevision(0)
    private var committedIdentity: ReaderCommittedIdentity? = null
    private var mutableExecutionState: ReaderExecutionState = ReaderExecutionState.Idle

    internal val executionState: ReaderExecutionState
        get() = synchronized(stateLock) { mutableExecutionState }

    suspend fun updateChapterGraph(groups: List<CanonicalChapterGroup>) {
        val ownedGroups = groups.map { group ->
            require(group.chapter.storyId == storyId) {
                "Reader session chapter graph must contain only story ${storyId.value}."
            }
            require(group.releases.all { it.storyId == storyId }) {
                "Reader session releases must contain only story ${storyId.value}."
            }
            group.copy(
                chapter = group.chapter.copy(releaseIds = group.chapter.releaseIds.toSet()),
                releases = group.releases.toList(),
            )
        }
        synchronized(stateLock) {
            if (latestChapterGroups == ownedGroups) return
            latestChapterGroups = ownedGroups
            chapterGraphRevision = ReaderChapterGraphRevision(chapterGraphRevision.value + 1L)
            firstChapterGraph.complete(Unit)
            // M4 Tasks 23-24 own hard/soft invalidation classification. M2 records facts only.
        }
    }

    suspend fun updateRoutingPreferences(preferences: ReaderPreferences) {
        synchronized(stateLock) {
            latestPreferences = preferences.copy(
                languageOrder = preferences.languageOrder.toList(),
            )
            firstRoutingPreferences.complete(Unit)
            // M4 Task 24 decides whether a routing-preference change is a hard replan.
        }
    }

    suspend fun execute(intent: ReaderForegroundIntent): ReaderForegroundResult {
        val generationId = synchronized(stateLock) {
            val next = ReaderGenerationId(nextGenerationValue++)
            activeExecution = ActiveExecution(
                generationId = next,
                targetChapterId = intent.targetChapterId,
                explicitReleaseId = intent.explicitReleaseId,
                planRevision = ReaderPlanRevision(0),
            )
            mutableExecutionState = ReaderExecutionState.Planning(identityFor(activeExecution!!))
            next
        }

        try {
            firstChapterGraph.await()
            firstRoutingPreferences.await()
        } catch (cancelled: CancellationException) {
            markCancelled(generationId)
            throw cancelled
        }

        while (true) {
            val context = synchronized(stateLock) {
                val active = activeExecution
                if (active == null || active.generationId != generationId) return@synchronized null
                buildContext(active)
            }
            if (context == null) {
                val identity = ReaderForegroundIdentity(
                    sessionId = sessionId,
                    generationId = generationId,
                    targetChapterId = intent.targetChapterId,
                )
                return ReaderForegroundResult.Superseded(identity)
            }

            val result = try {
                delegate.execute(this, context)
            } catch (cancelled: CancellationException) {
                markCancelled(generationId)
                throw cancelled
            } catch (_: Exception) {
                ReaderForegroundResult.Exhausted(
                    identity = context.foregroundIdentity,
                    code = READER_LOAD_FAILED,
                    retryable = true,
                    attempts = emptyList(),
                )
            }

            when (val completion = completeExecution(context, result)) {
                CompletionDisposition.Replan -> continue
                is CompletionDisposition.Finished -> return completion.result
            }
        }
    }

    internal fun hardInvalidate(): ReaderExecutionIdentity? = synchronized(stateLock) {
        hardInvalidateLocked()
        activeExecution?.let(::identityFor)
    }

    internal fun markAttempt(
        context: ReaderRouteExecutionContext,
        attemptId: String,
        recovering: Boolean,
    ): Boolean = synchronized(stateLock) {
        if (!matchesActiveLocked(context.identity)) return@synchronized false
        val attempt = attemptIdentity(context.identity, attemptId)
        mutableExecutionState = if (recovering) {
            ReaderExecutionState.Recovering(attempt)
        } else {
            ReaderExecutionState.Executing(attempt)
        }
        true
    }

    internal fun markValidating(
        context: ReaderRouteExecutionContext,
        attemptId: String,
    ): Boolean = synchronized(stateLock) {
        if (!matchesActiveLocked(context.identity)) return@synchronized false
        mutableExecutionState = ReaderExecutionState.Validating(
            attemptIdentity(context.identity, attemptId),
        )
        true
    }

    private fun completeExecution(
        context: ReaderRouteExecutionContext,
        result: ReaderForegroundResult,
    ): CompletionDisposition = synchronized(stateLock) {
        val active = activeExecution
        when {
            active == null || active.generationId != context.identity.generationId ->
                CompletionDisposition.Finished(ReaderForegroundResult.Superseded(context.foregroundIdentity))
            active.planRevision != context.identity.planRevision -> {
                mutableExecutionState = ReaderExecutionState.Planning(identityFor(active))
                CompletionDisposition.Replan
            }
            else -> CompletionDisposition.Finished(finishLocked(context, result))
        }
    }

    private fun finishLocked(
        context: ReaderRouteExecutionContext,
        result: ReaderForegroundResult,
    ): ReaderForegroundResult {
        check(matchesActiveLocked(context.identity)) {
            "Reader completion gate must hold the active generation and plan revision."
        }
        when (result) {
            is ReaderForegroundResult.Committed -> {
                committedIdentity = ReaderCommittedIdentity(
                    chapterId = result.identity.targetChapterId,
                    releaseId = result.release.id,
                    sourceId = result.release.pluginId,
                    documentFingerprint = result.document.fingerprint,
                )
                mutableExecutionState = ReaderExecutionState.Committed(
                    identity = context.identity,
                    committed = committedIdentity!!,
                )
            }
            is ReaderForegroundResult.Exhausted -> {
                mutableExecutionState = ReaderExecutionState.Exhausted(
                    identity = context.identity,
                    code = result.code,
                    retryable = result.retryable,
                )
            }
            is ReaderForegroundResult.Superseded -> Unit
        }
        activeExecution = null
        return result
    }

    private fun markCancelled(generationId: ReaderGenerationId) {
        synchronized(stateLock) {
            val active = activeExecution ?: return
            if (active.generationId != generationId) return
            mutableExecutionState = ReaderExecutionState.Cancelled(identityFor(active))
            activeExecution = null
        }
    }

    private fun buildContext(active: ActiveExecution): ReaderRouteExecutionContext {
        val groups = checkNotNull(latestChapterGroups) {
            "Reader route execution requires an initialized chapter graph."
        }.map { it.copy(releases = it.releases.toList()) }
        val preferences = checkNotNull(latestPreferences) {
            "Reader route execution requires initialized routing preferences."
        }
        return ReaderRouteExecutionContext(
            storyId = storyId,
            identity = identityFor(active),
            chapterGraphRevision = chapterGraphRevision,
            chapterGroups = groups,
            preferences = preferences,
            committedIdentity = committedIdentity,
            explicitReleaseId = active.explicitReleaseId,
        )
    }

    private fun hardInvalidateLocked() {
        val active = activeExecution ?: return
        val revised = active.copy(
            planRevision = ReaderPlanRevision(active.planRevision.value + 1L),
        )
        activeExecution = revised
        mutableExecutionState = ReaderExecutionState.Planning(identityFor(revised))
    }

    private fun matchesActiveLocked(identity: ReaderExecutionIdentity): Boolean {
        val active = activeExecution ?: return false
        return active.generationId == identity.generationId &&
            active.planRevision == identity.planRevision &&
            active.targetChapterId == identity.targetChapterId
    }

    private fun identityFor(active: ActiveExecution) = ReaderExecutionIdentity(
        sessionId = sessionId,
        generationId = active.generationId,
        planRevision = active.planRevision,
        targetChapterId = active.targetChapterId,
    )

    private fun attemptIdentity(
        identity: ReaderExecutionIdentity,
        attemptId: String,
    ) = ReaderAttemptIdentity(
        sessionId = identity.sessionId,
        generationId = identity.generationId,
        planRevision = identity.planRevision,
        attemptId = attemptId,
        targetChapterId = identity.targetChapterId,
    )

    private sealed interface CompletionDisposition {
        data object Replan : CompletionDisposition
        data class Finished(val result: ReaderForegroundResult) : CompletionDisposition
    }
}

private fun ReaderExecutionIdentity.toForegroundIdentity() = ReaderForegroundIdentity(
    sessionId = sessionId,
    generationId = generationId,
    targetChapterId = targetChapterId,
)
