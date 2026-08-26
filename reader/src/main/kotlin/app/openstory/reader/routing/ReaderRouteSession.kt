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
import app.openstory.reader.engine.RouteAttempt
import app.openstory.reader.preferences.ReaderPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
    val chapterGraph: ReaderSessionChapterGraph,
    val preferences: ReaderPreferences,
    val committedIdentity: ReaderCommittedIdentity?,
    val explicitReleaseId: ChapterReleaseId?,
    val knownInvalidLocalFingerprints: Map<ChapterReleaseId, Set<String>> = emptyMap(),
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
    private val prefetchDelegate: ReaderPrefetchExecutionDelegate? = null,
    private val prefetchScope: CoroutineScope? = null,
) {
    private data class ActiveExecution(
        val generationId: ReaderGenerationId,
        val targetChapterId: CanonicalChapterId,
        val explicitReleaseId: ChapterReleaseId?,
        val planRevision: ReaderPlanRevision,
    )

    private data class ActivePlan(
        val identity: ReaderExecutionIdentity,
        val winnerReleaseId: ChapterReleaseId,
        val plannedReleaseIds: Set<ChapterReleaseId>,
    )

    private val stateLock = Any()
    private var nextGenerationValue = 1L
    private var activeExecution: ActiveExecution? = null
    private var activePlan: ActivePlan? = null
    private var latestChapterGraph: ReaderSessionChapterGraph? = null
    private var latestPreferences: ReaderPreferences? = null
    private val firstChapterGraph = CompletableDeferred<Unit>()
    private val firstRoutingPreferences = CompletableDeferred<Unit>()
    private var chapterGraphRevision = ReaderChapterGraphRevision(0)
    private var committedIdentity: ReaderCommittedIdentity? = null
    private val knownInvalidLocalFingerprints = mutableMapOf<ChapterReleaseId, MutableSet<String>>()
    private var mutableExecutionState: ReaderExecutionState = ReaderExecutionState.Idle
    private var prefetchJob: Job? = null
    private var prefetchTargetChapterId: CanonicalChapterId? = null
    private var prefetchTargetGroup: CanonicalChapterGroup? = null
    private var prefetchToken = 0L

    internal val executionState: ReaderExecutionState
        get() = synchronized(stateLock) { mutableExecutionState }

    suspend fun updateChapterGraph(groups: List<CanonicalChapterGroup>) {
        val candidate = ReaderSessionChapterGraph.create(storyId, groups)
        val changed = synchronized(stateLock) {
            val current = latestChapterGraph
            if (current?.groups == candidate.groups) return@synchronized false
            // The first graph snapshot establishes readiness; there is no prior route fact to revoke yet.
            val isFirstEmission = current == null
            val active = activeExecution
            val hardInvalidation = !isFirstEmission &&
                active != null &&
                graphHardInvalidatesLocked(active, candidate)
            latestChapterGraph = candidate
            chapterGraphRevision = ReaderChapterGraphRevision(chapterGraphRevision.value + 1L)
            knownInvalidLocalFingerprints.keys.retainAll(candidate.releaseIds)
            firstChapterGraph.complete(Unit)
            if (hardInvalidation) hardInvalidateLocked()
            true
        }
        if (changed) refreshPrefetch()
    }

    suspend fun updateRoutingPreferences(preferences: ReaderPreferences) {
        val owned = preferences.copy(languageOrder = preferences.languageOrder.toList())
        val routingChanged = synchronized(stateLock) {
            val previous = latestPreferences
            if (previous == owned) {
                firstRoutingPreferences.complete(Unit)
                return@synchronized false
            }
            latestPreferences = owned
            firstRoutingPreferences.complete(Unit)
            val languageChanged = previous != null && previous.languageOrder != owned.languageOrder
            if (activeExecution != null && languageChanged) {
                hardInvalidateLocked()
            }
            languageChanged
        }
        if (routingChanged) refreshPrefetch(force = true)
    }

    suspend fun execute(intent: ReaderForegroundIntent): ReaderForegroundResult {
        cancelPrefetch()
        val generationId = synchronized(stateLock) {
            val next = ReaderGenerationId(nextGenerationValue++)
            activeExecution = ActiveExecution(
                generationId = next,
                targetChapterId = intent.targetChapterId,
                explicitReleaseId = intent.explicitReleaseId,
                planRevision = ReaderPlanRevision(0),
            )
            activePlan = null
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
                is CompletionDisposition.Finished -> {
                    if (completion.result is ReaderForegroundResult.Committed) refreshPrefetch()
                    return completion.result
                }
            }
        }
    }

    internal fun recordPlannedRoute(
        context: ReaderRouteExecutionContext,
        winnerReleaseId: ChapterReleaseId,
        attempts: List<RouteAttempt>,
    ): Boolean = synchronized(stateLock) {
        if (!matchesActiveLocked(context.identity)) return@synchronized false
        require(attempts.isNotEmpty()) { "An active Reader plan must contain at least one attempt." }
        require(attempts.any { it.releaseId == winnerReleaseId }) {
            "Reader winner must be represented in the executable route."
        }
        val plan = ActivePlan(
            identity = context.identity,
            winnerReleaseId = winnerReleaseId,
            plannedReleaseIds = attempts.mapTo(linkedSetOf()) { it.releaseId },
        )
        if (
            context.chapterGraphRevision != chapterGraphRevision &&
            graphInvalidatesPlanLocked(
                targetChapterId = context.identity.targetChapterId,
                plan = plan,
                nextGraph = checkNotNull(latestChapterGraph),
            )
        ) {
            hardInvalidateLocked()
            return@synchronized false
        }
        activePlan = plan
        true
    }

    internal fun hardInvalidate(): ReaderExecutionIdentity? = synchronized(stateLock) {
        hardInvalidateLocked()
        activeExecution?.let(::identityFor)
    }

    internal fun hardInvalidateIfCurrent(context: ReaderRouteExecutionContext): Boolean = synchronized(stateLock) {
        if (!matchesActiveLocked(context.identity)) return@synchronized false
        hardInvalidateLocked()
        true
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

    internal fun markCompeting(
        context: ReaderRouteExecutionContext,
        primaryAttemptId: String,
        hedgeAttemptId: String,
    ): Boolean = synchronized(stateLock) {
        if (!matchesActiveLocked(context.identity)) return@synchronized false
        mutableExecutionState = ReaderExecutionState.Competing(
            primary = attemptIdentity(context.identity, primaryAttemptId),
            hedge = attemptIdentity(context.identity, hedgeAttemptId),
        )
        true
    }

    internal fun markKnownInvalidLocal(
        context: ReaderRouteExecutionContext,
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ): Boolean = synchronized(stateLock) {
        if (!matchesActiveLocked(context.identity)) return@synchronized false
        markKnownInvalidLocalLocked(releaseId, fingerprint)
        true
    }

    internal fun markKnownInvalidLocal(
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ) {
        synchronized(stateLock) {
            if (latestChapterGraph?.release(releaseId) == null) return@synchronized
            markKnownInvalidLocalLocked(releaseId, fingerprint)
        }
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
        activePlan = null
        return result
    }

    private fun markCancelled(generationId: ReaderGenerationId) {
        synchronized(stateLock) {
            val active = activeExecution ?: return
            if (active.generationId != generationId) return
            mutableExecutionState = ReaderExecutionState.Cancelled(identityFor(active))
            activeExecution = null
            activePlan = null
        }
    }

    private fun buildContext(active: ActiveExecution): ReaderRouteExecutionContext {
        val graph = checkNotNull(latestChapterGraph) {
            "Reader route execution requires an initialized chapter graph."
        }
        val preferences = checkNotNull(latestPreferences) {
            "Reader route execution requires initialized routing preferences."
        }
        return ReaderRouteExecutionContext(
            storyId = storyId,
            identity = identityFor(active),
            chapterGraphRevision = chapterGraphRevision,
            chapterGraph = graph,
            preferences = preferences,
            committedIdentity = committedIdentity,
            explicitReleaseId = active.explicitReleaseId,
            knownInvalidLocalFingerprints = knownInvalidLocalFingerprints
                .mapValues { (_, fingerprints) -> fingerprints.toSet() },
        )
    }

    private fun refreshPrefetch(force: Boolean = false) {
        val action = synchronized(stateLock) {
            val scope = prefetchScope
            val prefetch = prefetchDelegate
            val committed = committedIdentity
            val graph = latestChapterGraph
            val preferences = latestPreferences
            if (scope == null || prefetch == null || committed == null) {
                return@synchronized PrefetchAction.None
            }
            if (graph == null || preferences == null || activeExecution != null) {
                return@synchronized PrefetchAction.None
            }

            val nextGroup = graph.nextAfter(committed.chapterId)
            if (nextGroup == null) {
                val old = prefetchJob
                prefetchToken += 1L
                prefetchJob = null
                prefetchTargetChapterId = null
                prefetchTargetGroup = null
                return@synchronized PrefetchAction.Cancel(old)
            }
            val nextChapterId = nextGroup.chapter.id
            val samePrefetchTarget = prefetchTargetChapterId == nextChapterId &&
                prefetchTargetGroup == nextGroup
            if (!force && samePrefetchTarget && prefetchJob != null) {
                return@synchronized PrefetchAction.None
            }

            val old = prefetchJob
            val token = prefetchToken + 1L
            prefetchToken = token
            prefetchJob = null
            prefetchTargetChapterId = nextChapterId
            prefetchTargetGroup = nextGroup
            PrefetchAction.Launch(
                oldJob = old,
                scope = scope,
                delegate = prefetch,
                token = token,
                targetChapterId = nextChapterId,
                context = ReaderRoutePlanningContext(
                    storyId = storyId,
                    targetChapterId = nextChapterId,
                    chapterGraphRevision = chapterGraphRevision,
                    planRevision = ReaderPlanRevision(0),
                    chapterGraph = graph,
                    preferences = preferences,
                    committedIdentity = committed,
                    explicitReleaseId = null,
                    knownInvalidLocalFingerprints = knownInvalidLocalFingerprints
                        .mapValues { (_, fingerprints) -> fingerprints.toSet() },
                ),
            )
        }
        when (action) {
            PrefetchAction.None -> Unit
            is PrefetchAction.Cancel -> action.job?.cancel()
            is PrefetchAction.Launch -> {
                action.oldJob?.cancel()
                val job = action.scope.launch {
                    try {
                        action.delegate.execute(this@ReaderRouteSession, action.context)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Opportunistic work must never fail the owning Reader scope.
                    }
                }
                val keep = synchronized(stateLock) {
                    if (
                        prefetchToken == action.token &&
                        prefetchTargetChapterId == action.targetChapterId &&
                        activeExecution == null
                    ) {
                        prefetchJob = job
                        true
                    } else {
                        false
                    }
                }
                if (!keep) job.cancel()
            }
        }
    }

    private fun cancelPrefetch() {
        val job = synchronized(stateLock) {
            prefetchToken += 1L
            prefetchTargetChapterId = null
            prefetchTargetGroup = null
            prefetchJob.also { prefetchJob = null }
        }
        job?.cancel()
    }

    private fun markKnownInvalidLocalLocked(
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ) {
        require(fingerprint.isNotBlank()) { "Known-invalid local fingerprint must not be blank." }
        knownInvalidLocalFingerprints.getOrPut(releaseId, ::mutableSetOf).add(fingerprint)
    }

    private sealed interface PrefetchAction {
        data object None : PrefetchAction
        data class Cancel(val job: Job?) : PrefetchAction
        data class Launch(
            val oldJob: Job?,
            val scope: CoroutineScope,
            val delegate: ReaderPrefetchExecutionDelegate,
            val token: Long,
            val targetChapterId: CanonicalChapterId,
            val context: ReaderRoutePlanningContext,
        ) : PrefetchAction
    }

    private fun graphHardInvalidatesLocked(
        active: ActiveExecution,
        nextGraph: ReaderSessionChapterGraph,
    ): Boolean {
        val target = nextGraph.group(active.targetChapterId)
        if (
            target == null ||
            target.chapter.tombstoned ||
            target.releases.isEmpty() ||
            target.releases.none { it.canonicalChapterId == target.chapter.id }
        ) {
            return true
        }
        val plan = activePlan
        return plan != null &&
            plan.identity == identityFor(active) &&
            graphInvalidatesPlanLocked(active.targetChapterId, plan, nextGraph)
    }

    private fun graphInvalidatesPlanLocked(
        targetChapterId: CanonicalChapterId,
        plan: ActivePlan,
        nextGraph: ReaderSessionChapterGraph,
    ): Boolean {
        val target = nextGraph.group(targetChapterId) ?: return true
        if (target.chapter.tombstoned || target.releases.isEmpty()) return true
        val targetReleaseIds = target.releases
            .asSequence()
            .filter { it.canonicalChapterId == target.chapter.id }
            .mapTo(hashSetOf()) { it.id }
        if (targetReleaseIds.isEmpty()) return true
        return plan.winnerReleaseId !in targetReleaseIds ||
            plan.plannedReleaseIds.any { it !in targetReleaseIds }
    }

    private fun hardInvalidateLocked() {
        val active = activeExecution ?: return
        val revised = active.copy(
            planRevision = ReaderPlanRevision(active.planRevision.value + 1L),
        )
        activeExecution = revised
        activePlan = null
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
