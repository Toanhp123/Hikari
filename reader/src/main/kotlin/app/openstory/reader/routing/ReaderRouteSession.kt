package app.openstory.reader.routing

import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.assets.ReaderAssetGraphRevision
import app.openstory.reader.assets.ReaderAssetSessionPort
import app.openstory.reader.assets.ReaderAssetSessionState
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

class ReaderRouteSession internal constructor(
    val storyId: StoryId,
    val sessionId: ReaderSessionId,
    private val delegate: ReaderRouteExecutionDelegate,
    private val prefetchDelegate: ReaderPrefetchExecutionDelegate? = null,
    private val prefetchScope: CoroutineScope? = null,
    private val assetSessionPort: ReaderAssetSessionPort = ReaderAssetSessionPort.NO_OP,
) {
    private val stateLock = Any()
    private var nextGenerationValue = 1L
    private var activeExecution: ReaderSessionActiveExecution? = null
    private var activePlan: ReaderSessionActivePlan? = null
    private var latestChapterGraph: ReaderSessionChapterGraph? = null
    private var latestPreferences: ReaderRoutingPreferences? = null
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
    private var mutableAssetSessionState = ReaderAssetSessionState()
    private var closed = false

    internal val executionState: ReaderExecutionState
        get() = synchronized(stateLock) { mutableExecutionState }

    internal val assetSessionState: ReaderAssetSessionState
        get() = synchronized(stateLock) { mutableAssetSessionState }

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
                readerGraphHardInvalidates(
                    active = active,
                    activeIdentity = identityFor(active),
                    plan = activePlan,
                    nextGraph = candidate,
                )
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
        val owned = ReaderRoutingPreferences.create(preferences.languageOrder)
        val routingChanged = synchronized(stateLock) {
            val previous = latestPreferences
            if (previous == owned) {
                firstRoutingPreferences.complete(Unit)
                return@synchronized false
            }
            latestPreferences = owned
            firstRoutingPreferences.complete(Unit)
            if (activeExecution != null && previous != null) {
                hardInvalidateLocked()
            }
            previous != null
        }
        if (routingChanged) refreshPrefetch(force = true)
    }

    suspend fun execute(intent: ReaderForegroundIntent): ReaderForegroundResult {
        cancelPrefetch()
        val generationId = synchronized(stateLock) {
            val next = ReaderGenerationId(nextGenerationValue++)
            activeExecution = ReaderSessionActiveExecution(
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
        val plan = ReaderSessionActivePlan(
            identity = context.identity,
            winnerReleaseId = winnerReleaseId,
            plannedReleaseIds = attempts.mapTo(linkedSetOf()) { it.releaseId },
        )
        if (
            context.chapterGraphRevision != chapterGraphRevision &&
            readerGraphInvalidatesPlan(
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
        attempt: ReaderAttemptIdentity,
        recovering: Boolean,
    ): Boolean = synchronized(stateLock) {
        if (!matchesActiveLocked(attempt)) return@synchronized false
        mutableExecutionState = if (recovering) {
            ReaderExecutionState.Recovering(attempt)
        } else {
            ReaderExecutionState.Executing(attempt)
        }
        true
    }

    internal fun markValidating(attempt: ReaderAttemptIdentity): Boolean = synchronized(stateLock) {
        if (!matchesActiveLocked(attempt)) return@synchronized false
        mutableExecutionState = ReaderExecutionState.Validating(attempt)
        true
    }

    internal fun markCompeting(
        primary: ReaderAttemptIdentity,
        hedge: ReaderAttemptIdentity,
    ): Boolean = synchronized(stateLock) {
        if (!matchesActiveLocked(primary) || !matchesActiveLocked(hedge)) return@synchronized false
        mutableExecutionState = ReaderExecutionState.Competing(
            primary = primary,
            hedge = hedge,
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
        check(result.identity == context.foregroundIdentity) {
            "Reader completion result identity must match the active execution context."
        }
        val acceptedResult = when (result) {
            is ReaderForegroundResult.Committed -> {
                val targetGroup = checkNotNull(
                    context.chapterGraph.group(context.identity.targetChapterId),
                ) {
                    "Reader committed target chapter must exist in the execution graph."
                }
                check(result.chapterGroup == targetGroup) {
                    "Reader committed chapter group must match the execution target chapter."
                }
                check(targetGroup.releases.any { it == result.release }) {
                    "Reader committed release must belong to the execution target chapter."
                }
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
                acceptAssetManifestLocked(result)
            }
            is ReaderForegroundResult.Exhausted -> {
                mutableExecutionState = ReaderExecutionState.Exhausted(
                    identity = context.identity,
                    code = result.code,
                    retryable = result.retryable,
                )
                result
            }
            is ReaderForegroundResult.Superseded -> result
        }
        activeExecution = null
        activePlan = null
        return acceptedResult
    }

    private fun acceptAssetManifestLocked(
        result: ReaderForegroundResult.Committed,
    ): ReaderForegroundResult.Committed {
        val manifest = result.assetManifest ?: return result
        check(manifest.sessionId == sessionId) { "Reader asset manifest must belong to the active session." }
        check(manifest.canonicalChapterId == result.identity.targetChapterId) {
            "Reader asset manifest must belong to the committed chapter."
        }
        check(manifest.selectedReleaseId == result.release.id) {
            "Reader asset manifest must belong to the committed release."
        }
        val proposedRevision = mutableAssetSessionState.manifestRevision + 1L
        val effectiveRevision = assetSessionPort.registerCommitted(sessionId, proposedRevision, manifest)
        check(effectiveRevision >= proposedRevision) {
            "Reader asset session port must not move manifest revision backwards."
        }
        mutableAssetSessionState = mutableAssetSessionState.acceptCommitted(
            effectiveManifestRevision = effectiveRevision,
            chapterId = manifest.canonicalChapterId,
        )
        return result.copy(assetManifestRevision = effectiveRevision)
    }

    fun close() {
        val job = synchronized(stateLock) {
            if (closed) return
            closed = true
            prefetchToken += 1L
            prefetchTargetChapterId = null
            prefetchTargetGroup = null
            prefetchJob.also { prefetchJob = null }
        }
        job?.cancel()
        assetSessionPort.releaseSession(sessionId)
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

    private fun buildContext(active: ReaderSessionActiveExecution): ReaderRouteExecutionContext {
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
                    prefetchToken = token,
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

    internal fun acceptPrefetchedArtifactIfCurrent(
        context: ReaderRoutePlanningContext,
        artifact: app.openstory.reader.assets.ReaderPrefetchedDocumentArtifact,
    ): Boolean = synchronized(stateLock) {
        val token = context.prefetchToken ?: return@synchronized false
        val currentGroup = latestChapterGraph?.group(context.targetChapterId)
        val current = !closed &&
            activeExecution == null &&
            prefetchToken == token &&
            prefetchTargetChapterId == context.targetChapterId &&
            chapterGraphRevision == context.chapterGraphRevision &&
            currentGroup == prefetchTargetGroup &&
            artifact.sessionId == sessionId &&
            artifact.prefetchToken == token &&
            artifact.graphRevision == ReaderAssetGraphRevision(context.chapterGraphRevision.value) &&
            artifact.targetChapterId == context.targetChapterId &&
            currentGroup?.releases?.any { it == artifact.selectedRelease } == true
        if (!current) return@synchronized false
        assetSessionPort.acceptPrefetchedArtifact(artifact)
        true
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
        return identity.sessionId == sessionId &&
            active.generationId == identity.generationId &&
            active.planRevision == identity.planRevision &&
            active.targetChapterId == identity.targetChapterId
    }

    private fun matchesActiveLocked(identity: ReaderAttemptIdentity): Boolean {
        val active = activeExecution ?: return false
        return identity.belongsTo(identityFor(active))
    }

    private fun identityFor(active: ReaderSessionActiveExecution) = ReaderExecutionIdentity(
        sessionId = sessionId,
        generationId = active.generationId,
        planRevision = active.planRevision,
        targetChapterId = active.targetChapterId,
    )

    private sealed interface CompletionDisposition {
        data object Replan : CompletionDisposition
        data class Finished(val result: ReaderForegroundResult) : CompletionDisposition
    }
}
