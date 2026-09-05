package app.openstory.reader.assets

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.reader.routing.ReaderNetworkFactsPort
import app.openstory.reader.routing.ReaderNetworkState
import app.openstory.reader.routing.ReaderSessionId
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

data class ReaderCommittedAssetManifestSnapshot(
    val sessionId: ReaderSessionId,
    val manifestRevision: Long,
    val manifest: ReaderAssetChapterManifest,
)

sealed interface ReaderDeliveryManifestReplacement {
    data class Applied(
        val snapshot: ReaderCommittedAssetManifestSnapshot,
    ) : ReaderDeliveryManifestReplacement

    data object Superseded : ReaderDeliveryManifestReplacement
    data object SemanticRouteMismatch : ReaderDeliveryManifestReplacement
}

class ReaderAssetCoordinator(
    private val store: ReaderAssetStorePort,
    private val networkFacts: ReaderNetworkFactsPort,
    private val coordinatorScope: CoroutineScope,
    private val loader: ReaderAssetLoader? = null,
    private val manifestFactory: ReaderAssetManifestFactory = ReaderAssetManifestFactory(),
    private val workingSetPolicy: ReaderAssetWorkingSetPolicy = ReaderAssetWorkingSetPolicy(),
    private val planner: ReaderAssetPrefetchPlanner = ReaderAssetPrefetchPlanner(workingSetPolicy),
    private val diagnostics: ReaderAssetDiagnosticsSink = ReaderAssetDiagnosticsSink.NO_OP,
) : ReaderAssetSessionPort {
    private data class SessionRuntime(
        var state: ReaderAssetSessionState,
        val inspectionJobs: MutableSet<Job> = mutableSetOf(),
        var planningJob: Job? = null,
        val acquisitionJobs: MutableMap<ReaderAssetAcquisitionId, Job> = mutableMapOf(),
        val requestJobs: MutableMap<Long, ActivePageRequest> = mutableMapOf(),
        var refreshPort: ReaderSelectedReleaseRefreshPort? = null,
        val refreshJobs:
            MutableMap<ReaderAssetRefreshFlightKey, Deferred<DeliveryRefreshResolution>> = mutableMapOf(),
        var securityInvalidatedManifestRevision: Long? = null,
    )

    private data class ActivePageRequest(
        val imageOrdinal: Int,
        val job: Job,
    )

    private data class ReaderAssetRefreshFlightKey(
        val manifestRevision: Long,
        val selectedReleaseId: ChapterReleaseId,
    )

    private sealed interface DeliveryRefreshResolution {
        data object Unchanged : DeliveryRefreshResolution
        data object Changed : DeliveryRefreshResolution
        data object Superseded : DeliveryRefreshResolution
        data object AuthorityUnavailable : DeliveryRefreshResolution
        data object RouteInvalidated : DeliveryRefreshResolution
        data class Failure(val failure: ReaderAssetFailure) : DeliveryRefreshResolution
    }

    private sealed interface DeliveryRefreshFlight {
        data class Pending(
            val deferred: Deferred<DeliveryRefreshResolution>,
        ) : DeliveryRefreshFlight

        data class Resolved(
            val resolution: DeliveryRefreshResolution,
        ) : DeliveryRefreshFlight
    }

    private data class ReaderAssetAcquisitionId(
        val key: ReaderPageAssetKey,
        val priority: ContentFetchPriority,
    )

    private data class PlannedAcquisition(
        val id: ReaderAssetAcquisitionId,
        val manifest: ReaderAssetChapterManifest,
        val descriptor: ReaderPageAssetDescriptor,
        val networkState: ReaderNetworkState,
        val manifestRevision: Long,
        val viewportRevision: Long,
    )

    private data class PlannedAsset(
        val manifest: ReaderAssetChapterManifest,
        val descriptor: ReaderPageAssetDescriptor,
        val priority: ContentFetchPriority,
    )

    private data class MaintenanceWork(
        val protections: ReaderAssetActiveProtections,
        val consumedKeys: Set<ReaderPageAssetKey>,
        val releasedSessions: Set<ReaderSessionId>,
    )

    private data class AcceptedPrefetch(
        val manifest: ReaderAssetChapterManifest?,
        val manifestRevision: Long,
        val shouldPlan: Boolean,
    )

    private val lock = Any()
    private val sessions = mutableMapOf<ReaderSessionId, SessionRuntime>()
    private val committedSnapshots = MutableStateFlow<Map<ReaderSessionId, ReaderCommittedAssetManifestSnapshot>>(
        emptyMap(),
    )
    private val nextConsumerToken = AtomicLong(1L)
    private var pendingProtections: ReaderAssetActiveProtections? = null
    private val pendingConsumedKeys = linkedSetOf<ReaderPageAssetKey>()
    private val pendingReleasedSessions = linkedSetOf<ReaderSessionId>()
    private var maintenanceJob: Job? = null

    override fun registerCommitted(
        sessionId: ReaderSessionId,
        proposedManifestRevision: Long,
        manifest: ReaderAssetChapterManifest,
    ): Long {
        require(proposedManifestRevision > 0L) { "Reader asset manifest revision proposal must be positive." }
        require(manifest.sessionId == sessionId) { "Reader asset manifest must belong to the registered session." }
        val cancelledJobs: List<Job>
        val effectiveRevision: Long
        synchronized(lock) {
            val runtime = sessions.getOrPut(sessionId) { SessionRuntime(ReaderAssetSessionState()) }
            effectiveRevision = maxOf(runtime.state.manifestRevision + 1L, proposedManifestRevision)
            cancelledJobs = runtime.cancelManifestWorkLocked()
            runtime.securityInvalidatedManifestRevision = null
            runtime.state = runtime.state.acceptCommitted(
                effectiveManifestRevision = effectiveRevision,
                chapterId = manifest.canonicalChapterId,
                manifest = manifest,
            )
            recomputeProtectionsLocked(runtime)
            publishCommittedSnapshotLocked(sessionId, effectiveRevision, manifest)
            enqueueMaintenanceLocked()
        }
        cancelledJobs.forEach(Job::cancel)
        scheduleInspection(sessionId, effectiveRevision, manifest)
        return effectiveRevision
    }

    override fun registerCommittedWithoutManifest(
        sessionId: ReaderSessionId,
        proposedManifestRevision: Long,
        chapterId: CanonicalChapterId,
    ): Long {
        require(proposedManifestRevision > 0L) { "Reader asset manifest revision proposal must be positive." }
        val cancelledJobs: List<Job>
        val effectiveRevision: Long
        synchronized(lock) {
            val runtime = sessions.getOrPut(sessionId) { SessionRuntime(ReaderAssetSessionState()) }
            effectiveRevision = maxOf(runtime.state.manifestRevision + 1L, proposedManifestRevision)
            cancelledJobs = runtime.cancelManifestWorkLocked()
            runtime.securityInvalidatedManifestRevision = null
            runtime.state = runtime.state.acceptCommitted(
                effectiveManifestRevision = effectiveRevision,
                chapterId = chapterId,
                manifest = null,
            )
            recomputeProtectionsLocked(runtime)
            committedSnapshots.value = committedSnapshots.value - sessionId
            enqueueMaintenanceLocked()
        }
        cancelledJobs.forEach(Job::cancel)
        return effectiveRevision
    }

    override fun acceptPrefetchedArtifact(artifact: ReaderPrefetchedDocumentArtifact) {
        val current = synchronized(lock) {
            sessions[artifact.sessionId]?.state?.takeIf { state ->
                state.committedManifest != null &&
                    artifact.prefetchToken > state.prefetchToken &&
                    artifact.graphRevision.value >= state.committedManifest.graphRevision.value &&
                    artifact.targetChapterId != state.committedManifest.canonicalChapterId &&
                    artifact.selectedRelease.storyId == state.committedManifest.storyId
            }
        } ?: return
        val prefetchedManifest = artifact.imageSourcePolicy?.let { policy ->
            val sourcePluginId = artifact.sourcePluginId ?: return@let null
            runCatching {
                manifestFactory.create(
                    sessionId = artifact.sessionId,
                    storyId = checkNotNull(current.committedManifest).storyId,
                    canonicalChapterId = artifact.targetChapterId,
                    selectedRelease = artifact.selectedRelease,
                    graphRevision = artifact.graphRevision,
                    document = artifact.document,
                    imageSourcePolicy = policy,
                    sourcePluginId = sourcePluginId,
                )
            }.getOrNull()
        }
        val accepted = synchronized(lock) {
            val runtime = sessions[artifact.sessionId] ?: return@synchronized null
            val state = runtime.state
            val stillCurrent = state.manifestRevision == current.manifestRevision &&
                artifact.prefetchToken > state.prefetchToken &&
                artifact.graphRevision.value >= (state.prefetchedManifest?.graphRevision?.value ?: 0L)
            if (!stillCurrent) return@synchronized null
            val oldPrefetchedKeys = state.prefetchedManifest
                ?.descriptors
                ?.mapTo(hashSetOf(), ReaderPageAssetDescriptor::key)
                .orEmpty()
            val currentKeys = state.committedManifest
                ?.descriptors
                ?.mapTo(hashSetOf(), ReaderPageAssetDescriptor::key)
                .orEmpty()
            val retainedPresence = state.localPresence.filterKeys { key ->
                key !in oldPrefetchedKeys || key in currentKeys
            }
            val prefetchedPresence = prefetchedManifest
                ?.descriptors
                ?.associate { it.key to ReaderAssetLocalPresence.UNKNOWN }
                .orEmpty()
            runtime.state = state.copy(
                prefetchedManifest = prefetchedManifest,
                prefetchToken = artifact.prefetchToken,
                localPresence = retainedPresence + prefetchedPresence,
            )
            AcceptedPrefetch(prefetchedManifest, state.manifestRevision, state.viewport != null)
        } ?: return
        accepted.manifest?.let { manifest ->
            scheduleInspection(artifact.sessionId, accepted.manifestRevision, manifest)
        }
        if (accepted.shouldPlan) schedulePlanning(artifact.sessionId)
    }

    override fun registerSelectedReleaseRefreshPort(
        sessionId: ReaderSessionId,
        port: ReaderSelectedReleaseRefreshPort,
    ) {
        val cancelled = synchronized(lock) {
            val runtime = sessions.getOrPut(sessionId) { SessionRuntime(ReaderAssetSessionState()) }
            if (runtime.refreshPort === port) return
            val stale = runtime.refreshJobs.values.toList()
            runtime.refreshJobs.clear()
            runtime.refreshPort = port
            stale
        }
        cancelled.forEach(Job::cancel)
    }

    override fun unregisterSelectedReleaseRefreshPort(sessionId: ReaderSessionId) {
        val cancelled = synchronized(lock) {
            val runtime = sessions[sessionId] ?: return
            runtime.refreshPort = null
            runtime.refreshJobs.values.toList().also { runtime.refreshJobs.clear() }
        }
        cancelled.forEach(Job::cancel)
    }

    override fun releaseSession(sessionId: ReaderSessionId) {
        val cancelledJobs = synchronized(lock) {
            val runtime = sessions.remove(sessionId) ?: return
            committedSnapshots.value = committedSnapshots.value - sessionId
            pendingReleasedSessions += sessionId
            pendingProtections = protectionUnionLocked()
            ensureMaintenanceLocked()
            runtime.cancelAllWorkLocked()
        }
        cancelledJobs.forEach(Job::cancel)
    }

    fun invalidateSecurityScopedSource(sourceNamespace: ReaderAssetSourceNamespace) {
        val cancelledJobs = mutableListOf<Job>()
        var changed = false
        synchronized(lock) {
            sessions.forEach { (sessionId, runtime) ->
                val state = runtime.state
                val committedInvalidated = state.committedManifest.isSecurityScopedFor(sourceNamespace)
                val prefetchedInvalidated = state.prefetchedManifest.isSecurityScopedFor(sourceNamespace)
                val retainedRecent = state.recentCommittedManifests.filterNot {
                    it.isSecurityScopedFor(sourceNamespace)
                }
                val recentChanged = retainedRecent.size != state.recentCommittedManifests.size
                if (!committedInvalidated && !prefetchedInvalidated && !recentChanged) return@forEach

                changed = true
                cancelledJobs += runtime.cancelSecurityScopedWorkLocked(
                    sourceNamespace = sourceNamespace,
                    currentManifestInvalidated = committedInvalidated,
                )
                if (committedInvalidated) {
                    runtime.securityInvalidatedManifestRevision = state.manifestRevision
                    committedSnapshots.value = committedSnapshots.value - sessionId
                }

                val retainedCommitted = state.committedManifest.takeUnless { committedInvalidated }
                val retainedPrefetched = state.prefetchedManifest.takeUnless { prefetchedInvalidated }
                val retainedKeys = buildSet {
                    retainedCommitted?.descriptors?.mapTo(this) { it.key }
                    retainedPrefetched?.descriptors?.mapTo(this) { it.key }
                    retainedRecent.forEach { manifest -> manifest.descriptors.mapTo(this) { it.key } }
                }
                runtime.state = state.copy(
                    committedManifest = retainedCommitted,
                    recentCommittedManifests = retainedRecent,
                    viewport = state.viewport.takeUnless { committedInvalidated },
                    activeProtections = ReaderAssetActiveProtections.EMPTY,
                    localPresence = state.localPresence.filterKeys { it in retainedKeys },
                    consumedKeys = if (committedInvalidated) {
                        emptySet()
                    } else {
                        state.consumedKeys.filterTo(linkedSetOf()) { it in retainedKeys }
                    },
                    prefetchedManifest = retainedPrefetched,
                    prefetchToken = if (prefetchedInvalidated) 0L else state.prefetchToken,
                    plan = if (committedInvalidated || prefetchedInvalidated) ReaderAssetPlan.EMPTY else state.plan,
                )
                recomputeProtectionsLocked(runtime)
            }
            if (changed) enqueueMaintenanceLocked()
        }
        loader?.invalidateSecurityScopedSource(sourceNamespace)
        cancelledJobs.distinct().forEach(Job::cancel)
    }

    fun observeCommittedManifest(
        sessionId: ReaderSessionId,
    ): Flow<ReaderCommittedAssetManifestSnapshot> = committedSnapshots
        .mapNotNull { snapshots -> snapshots[sessionId] }
        .distinctUntilChanged()

    @Suppress("ReturnCount")
    fun replaceDeliveryManifest(
        sessionId: ReaderSessionId,
        expectedManifestRevision: Long,
        refreshedManifest: ReaderAssetChapterManifest,
    ): ReaderDeliveryManifestReplacement {
        val cancelledJobs: List<Job>
        val snapshot: ReaderCommittedAssetManifestSnapshot
        synchronized(lock) {
            val runtime = sessions[sessionId] ?: return ReaderDeliveryManifestReplacement.Superseded
            val current = runtime.state.committedManifest
                ?: return ReaderDeliveryManifestReplacement.Superseded
            if (runtime.state.manifestRevision != expectedManifestRevision) {
                return ReaderDeliveryManifestReplacement.Superseded
            }
            if (!current.matchesDeliveryRoute(refreshedManifest, sessionId)) {
                return ReaderDeliveryManifestReplacement.SemanticRouteMismatch
            }
            cancelledJobs = runtime.cancelDeliveryReplacementWorkLocked()
            val nextRevision = runtime.state.manifestRevision + 1L
            val refreshedKeys = refreshedManifest.descriptors.mapTo(linkedSetOf()) { it.key }
            runtime.state = runtime.state.copy(
                manifestRevision = nextRevision,
                committedManifest = refreshedManifest,
                viewport = null,
                activeProtections = ReaderAssetActiveProtections.EMPTY,
                localPresence = refreshedManifest.descriptors.associate {
                    it.key to ReaderAssetLocalPresence.UNKNOWN
                },
                consumedKeys = runtime.state.consumedKeys.filterTo(linkedSetOf()) { it in refreshedKeys },
                plan = ReaderAssetPlan.EMPTY,
            )
            recomputeProtectionsLocked(runtime)
            snapshot = ReaderCommittedAssetManifestSnapshot(sessionId, nextRevision, refreshedManifest)
            committedSnapshots.value = committedSnapshots.value + (sessionId to snapshot)
            enqueueMaintenanceLocked()
        }
        cancelledJobs.forEach(Job::cancel)
        scheduleInspection(sessionId, snapshot.manifestRevision, refreshedManifest)
        return ReaderDeliveryManifestReplacement.Applied(snapshot)
    }

    @Suppress("ReturnCount")
    fun updateViewport(snapshot: ReaderViewportSnapshot): Boolean {
        val cancelledJobs: List<Job>
        synchronized(lock) {
            val runtime = sessions[snapshot.sessionId] ?: return false
            val manifest = runtime.state.committedManifest ?: return false
            if (!snapshot.matches(runtime.state.manifestRevision, manifest)) return false
            if (runtime.state.viewport == snapshot) return false
            cancelledJobs = runtime.cancelViewportWorkLocked(snapshot)
            val immediatePlan = ReaderAssetPlan(
                interactive = workingSetPolicy.visibleDescriptors(manifest, snapshot),
            )
            runtime.state = runtime.state.copy(
                viewportRevision = runtime.state.viewportRevision + 1L,
                viewport = snapshot,
                plan = immediatePlan,
            )
            recomputeProtectionsLocked(runtime)
            enqueueMaintenanceLocked()
        }
        cancelledJobs.forEach(Job::cancel)
        schedulePlanning(snapshot.sessionId)
        return true
    }

    fun assetPresented(request: ReaderPageAssetRequest): Boolean = synchronized(lock) {
        val runtime = sessions[request.sessionId] ?: return@synchronized false
        val manifest = runtime.state.committedManifest ?: return@synchronized false
        val descriptor = manifest.descriptorMatching(request) ?: return@synchronized false
        val viewport = runtime.state.viewport ?: return@synchronized false
        if (runtime.state.manifestRevision != request.manifestRevision || !viewport.contains(descriptor.imageOrdinal)) {
            return@synchronized false
        }
        if (descriptor.key !in runtime.state.consumedKeys) {
            runtime.state = runtime.state.copy(consumedKeys = runtime.state.consumedKeys + descriptor.key)
            recomputeProtectionsLocked(runtime)
            pendingConsumedKeys += descriptor.key
            enqueueMaintenanceLocked()
        }
        true
    }

    @Suppress("ReturnCount")
    suspend fun requestPage(request: ReaderPageAssetRequest): ReaderAssetLoadOutcome {
        val registrationId = nextConsumerToken.getAndIncrement()
        val requestJob = currentCoroutineContext()[Job]
        val initial = synchronized(lock) {
            val runtime = sessions[request.sessionId] ?: return supersededOutcome()
            if (runtime.securityInvalidatedManifestRevision == request.manifestRevision) {
                return routeInvalidatedOutcome()
            }
            val manifest = runtime.state.committedManifest ?: return supersededOutcome()
            val descriptor = manifest.descriptorMatching(request) ?: return supersededOutcome()
            if (runtime.state.manifestRevision != request.manifestRevision) {
                return supersededOutcome()
            }
            if (requestJob != null) {
                runtime.requestJobs[registrationId] = ActivePageRequest(descriptor.imageOrdinal, requestJob)
            }
            RequestState(
                manifest = manifest,
                descriptor = descriptor,
                presence = runtime.state.localPresence[descriptor.key] ?: ReaderAssetLocalPresence.UNKNOWN,
            )
        }
        try {
            val initialOutcome = load(
                requestState = initial,
                networkState = currentNetworkState(),
                priority = ContentFetchPriority.CRITICAL,
            )
            val outcome = if (initialOutcome.isDeliveryRejected()) {
                when (val refreshResolution = refreshRejectedDelivery(request, initial.manifest)) {
                    DeliveryRefreshResolution.Unchanged -> if (isRequestCurrent(request)) {
                        load(
                            requestState = initial,
                            networkState = currentNetworkState(),
                            priority = ContentFetchPriority.CRITICAL,
                        )
                    } else {
                        supersededOutcome()
                    }
                    DeliveryRefreshResolution.Changed,
                    DeliveryRefreshResolution.Superseded,
                    -> supersededOutcome()
                    DeliveryRefreshResolution.AuthorityUnavailable -> initialOutcome
                    DeliveryRefreshResolution.RouteInvalidated ->
                        ReaderAssetLoadOutcome.Failure(ReaderAssetFailure.RouteInvalidated)
                    is DeliveryRefreshResolution.Failure ->
                        ReaderAssetLoadOutcome.Failure(refreshResolution.failure)
                }
            } else {
                initialOutcome
            }
            return finishVisibleRequest(request, initial, outcome)
        } finally {
            synchronized(lock) {
                sessions[request.sessionId]?.requestJobs?.remove(registrationId)
            }
        }
    }

    private fun ReaderAssetLoadOutcome.isDeliveryRejected(): Boolean =
        this is ReaderAssetLoadOutcome.Failure && failure is ReaderAssetFailure.DeliveryRejected

    private fun finishVisibleRequest(
        request: ReaderPageAssetRequest,
        initial: RequestState,
        outcome: ReaderAssetLoadOutcome,
    ): ReaderAssetLoadOutcome {
        val validity = requestValidity(request)
        if (validity != RequestValidity.CURRENT) {
            (outcome as? ReaderAssetLoadOutcome.Local)?.lease?.close()
            return if (validity == RequestValidity.SECURITY_INVALIDATED) {
                routeInvalidatedOutcome()
            } else {
                supersededOutcome()
            }
        }
        val refreshedPresence = when (outcome) {
            is ReaderAssetLoadOutcome.Local -> ReaderAssetLocalPresence.LOCAL_AVAILABLE
            is ReaderAssetLoadOutcome.Remote -> ReaderAssetLocalPresence.UNKNOWN
            is ReaderAssetLoadOutcome.Failure -> null
        }
        refreshedPresence?.let { presence ->
            updatePresenceIfCurrent(
                request.sessionId,
                request.manifestRevision,
                initial.descriptor.key,
                presence,
            )
        }
        return outcome
    }

    private fun isRequestCurrent(request: ReaderPageAssetRequest): Boolean =
        requestValidity(request) == RequestValidity.CURRENT

    private fun requestValidity(request: ReaderPageAssetRequest): RequestValidity = synchronized(lock) {
        val runtime = sessions[request.sessionId] ?: return@synchronized RequestValidity.SUPERSEDED
        if (runtime.securityInvalidatedManifestRevision == request.manifestRevision) {
            return@synchronized RequestValidity.SECURITY_INVALIDATED
        }
        val state = runtime.state
        if (state.manifestRevision == request.manifestRevision &&
            state.committedManifest?.descriptorMatching(request) != null
        ) {
            RequestValidity.CURRENT
        } else {
            RequestValidity.SUPERSEDED
        }
    }

    private suspend fun refreshRejectedDelivery(
        request: ReaderPageAssetRequest,
        committedManifest: ReaderAssetChapterManifest,
    ): DeliveryRefreshResolution {
        val key = ReaderAssetRefreshFlightKey(
            manifestRevision = request.manifestRevision,
            selectedReleaseId = committedManifest.selectedReleaseId,
        )
        val flight = synchronized(lock) {
            val runtime = sessions[request.sessionId]
            val currentManifest = runtime?.state?.committedManifest
            when {
                runtime == null || currentManifest == null -> DeliveryRefreshFlight.Resolved(
                    DeliveryRefreshResolution.Superseded,
                )
                runtime.state.manifestRevision != request.manifestRevision ||
                    currentManifest != committedManifest -> DeliveryRefreshFlight.Resolved(
                    DeliveryRefreshResolution.Superseded,
                )
                runtime.refreshJobs[key] != null -> DeliveryRefreshFlight.Pending(
                    checkNotNull(runtime.refreshJobs[key]),
                )
                runtime.refreshPort == null -> DeliveryRefreshFlight.Resolved(
                    DeliveryRefreshResolution.AuthorityUnavailable,
                )
                else -> {
                    val refreshPort = checkNotNull(runtime.refreshPort)
                    val created = coordinatorScope.async(start = CoroutineStart.LAZY) {
                        diagnostics.recordSafely(ReaderAssetDiagnosticEvent.LocatorRefresh)
                        performSelectedReleaseRefresh(
                            request = request,
                            committedManifest = committedManifest,
                            refreshPort = refreshPort,
                        )
                    }
                    runtime.refreshJobs[key] = created
                    created.invokeOnCompletion {
                        synchronized(lock) {
                            sessions[request.sessionId]?.refreshJobs?.remove(key, created)
                        }
                    }
                    DeliveryRefreshFlight.Pending(created)
                }
            }
        }
        return when (flight) {
            is DeliveryRefreshFlight.Resolved -> flight.resolution
            is DeliveryRefreshFlight.Pending -> awaitRefreshFlight(request, flight.deferred)
        }
    }

    private suspend fun awaitRefreshFlight(
        request: ReaderPageAssetRequest,
        flight: Deferred<DeliveryRefreshResolution>,
    ): DeliveryRefreshResolution {
        flight.start()
        return try {
            flight.await()
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            if (requestValidity(request) == RequestValidity.SECURITY_INVALIDATED) {
                DeliveryRefreshResolution.RouteInvalidated
            } else {
                throw cancelled
            }
        }
    }

    private suspend fun performSelectedReleaseRefresh(
        request: ReaderPageAssetRequest,
        committedManifest: ReaderAssetChapterManifest,
        refreshPort: ReaderSelectedReleaseRefreshPort,
    ): DeliveryRefreshResolution {
        val refreshResult = try {
            refreshPort.refreshSelectedRelease(
                expectedManifestRevision = request.manifestRevision,
                expectedReleaseId = committedManifest.selectedReleaseId,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
        return if (refreshResult == null) {
            DeliveryRefreshResolution.Failure(ReaderAssetFailure.TransportUnavailable(retryable = true))
        } else {
            resolveSelectedReleaseRefresh(request, committedManifest, refreshResult)
        }
    }

    private fun resolveSelectedReleaseRefresh(
        request: ReaderPageAssetRequest,
        committedManifest: ReaderAssetChapterManifest,
        refreshResult: ReaderSelectedReleaseRefreshResult,
    ): DeliveryRefreshResolution = when (refreshResult) {
            ReaderSelectedReleaseRefreshResult.Superseded -> DeliveryRefreshResolution.Superseded
            ReaderSelectedReleaseRefreshResult.RouteInvalidated -> DeliveryRefreshResolution.RouteInvalidated
            is ReaderSelectedReleaseRefreshResult.Failure ->
                DeliveryRefreshResolution.Failure(refreshResult.failure)
            is ReaderSelectedReleaseRefreshResult.Refreshed -> {
                val refreshedManifest = try {
                    manifestFactory.create(
                        sessionId = committedManifest.sessionId,
                        storyId = committedManifest.storyId,
                        canonicalChapterId = committedManifest.canonicalChapterId,
                        selectedRelease = refreshResult.selectedRelease,
                        graphRevision = committedManifest.graphRevision,
                        document = refreshResult.document,
                        imageSourcePolicy = refreshResult.imageSourcePolicy,
                        sourcePluginId = refreshResult.selectedRelease.pluginId,
                    )
                } catch (_: IllegalArgumentException) {
                    null
                }

                if (refreshedManifest == null) {
                    DeliveryRefreshResolution.RouteInvalidated
                } else {
                    when (val comparison = ReaderAssetLocatorRefresh.compare(committedManifest, refreshedManifest)) {
                        ReaderRefreshedManifestDecision.Unchanged -> DeliveryRefreshResolution.Unchanged
                        ReaderRefreshedManifestDecision.RouteInvalidated -> DeliveryRefreshResolution.RouteInvalidated
                        is ReaderRefreshedManifestDecision.Changed -> when (
                            replaceDeliveryManifest(
                                sessionId = request.sessionId,
                                expectedManifestRevision = request.manifestRevision,
                                refreshedManifest = comparison.manifest,
                            )
                        ) {
                            is ReaderDeliveryManifestReplacement.Applied -> DeliveryRefreshResolution.Changed
                            ReaderDeliveryManifestReplacement.Superseded -> DeliveryRefreshResolution.Superseded
                            ReaderDeliveryManifestReplacement.SemanticRouteMismatch ->
                                DeliveryRefreshResolution.RouteInvalidated
                        }
                    }
                }
            }
        }

    internal fun sessionSnapshot(sessionId: ReaderSessionId): ReaderAssetSessionState? =
        synchronized(lock) { sessions[sessionId]?.state }

    private fun scheduleInspection(
        sessionId: ReaderSessionId,
        manifestRevision: Long,
        manifest: ReaderAssetChapterManifest,
    ) {
        lateinit var job: Job
        job = coordinatorScope.launch(start = CoroutineStart.LAZY) {
            try {
                manifest.descriptors
                    .map(ReaderPageAssetDescriptor::key)
                    .chunked(INSPECTION_BATCH_SIZE)
                    .forEach { batch ->
                        val presence = inspect(batch.toSet())
                        val accepted = synchronized(lock) {
                            val runtime = sessions[sessionId] ?: return@synchronized false
                            if (
                                runtime.state.manifestRevision != manifestRevision ||
                                !runtime.state.tracks(manifest)
                            ) {
                                return@synchronized false
                            }
                            runtime.state = runtime.state.copy(localPresence = runtime.state.localPresence + presence)
                            true
                        }
                        if (!accepted) return@launch
                    }
                schedulePlanning(sessionId)
            } finally {
                synchronized(lock) {
                    sessions[sessionId]?.inspectionJobs?.remove(job)
                }
            }
        }
        val keep = synchronized(lock) {
            val runtime = sessions[sessionId]
            if (
                runtime?.state?.manifestRevision == manifestRevision &&
                runtime.state.tracks(manifest)
            ) {
                runtime.inspectionJobs += job
                true
            } else {
                false
            }
        }
        if (keep) job.start() else job.cancel()
    }

    private fun schedulePlanning(sessionId: ReaderSessionId) {
        val job = coordinatorScope.launch(start = CoroutineStart.LAZY) {
            val networkState = currentNetworkState()
            val cachePressure = currentCachePressure()
            val planInput = synchronized(lock) {
                val runtime = sessions[sessionId] ?: return@launch
                val manifest = runtime.state.committedManifest ?: return@launch
                val viewport = runtime.state.viewport ?: return@launch
                PlanInput(
                    manifest = manifest,
                    viewport = viewport,
                    prefetchedManifest = runtime.state.prefetchedManifest,
                    manifestRevision = runtime.state.manifestRevision,
                    viewportRevision = runtime.state.viewportRevision,
                )
            }
            val plan = planner.plan(
                manifest = planInput.manifest,
                viewport = planInput.viewport,
                networkState = networkState,
                cachePressure = cachePressure,
                prefetchedManifest = planInput.prefetchedManifest,
            )
            val work = synchronized(lock) {
                val runtime = sessions[sessionId] ?: return@synchronized null
                if (
                    runtime.state.manifestRevision != planInput.manifestRevision ||
                    runtime.state.viewportRevision != planInput.viewportRevision ||
                    runtime.state.viewport != planInput.viewport
                ) {
                    return@synchronized null
                }
                val cancelled = runtime.acquisitionJobs.values.toList()
                runtime.acquisitionJobs.clear()
                runtime.state = runtime.state.copy(plan = plan)
                recomputeProtectionsLocked(runtime)
                enqueueMaintenanceLocked()
                PlanningWork(
                    cancelledJobs = cancelled,
                    acquisitions = plannedAcquisitions(planInput, plan, networkState),
                )
            } ?: return@launch
            work.cancelledJobs.forEach(Job::cancel)
            val speculativeCount = work.acquisitions.count { it.id.priority == ContentFetchPriority.SPECULATIVE }
            if (speculativeCount > 0) {
                diagnostics.recordSafely(ReaderAssetDiagnosticEvent.Prefetch(speculativeCount))
            }
            work.acquisitions.forEach { acquisition -> scheduleAcquisition(sessionId, acquisition) }
        }
        val oldJob = synchronized(lock) {
            val runtime = sessions[sessionId] ?: return
            runtime.planningJob.also { runtime.planningJob = job }
        }
        oldJob?.cancel()
        job.start()
    }

    private fun plannedAcquisitions(
        input: PlanInput,
        plan: ReaderAssetPlan,
        networkState: ReaderNetworkState,
    ): List<PlannedAcquisition> {
        val assets = linkedMapOf<ReaderPageAssetKey, PlannedAsset>()
        plan.interactive.forEach { descriptor ->
            assets[descriptor.key] = PlannedAsset(input.manifest, descriptor, ContentFetchPriority.INTERACTIVE)
        }
        plan.currentAhead.forEach { descriptor ->
            assets.putIfAbsent(
                descriptor.key,
                PlannedAsset(input.manifest, descriptor, ContentFetchPriority.INTERACTIVE),
            )
        }
        input.prefetchedManifest?.let { prefetchedManifest ->
            plan.transition.forEach { descriptor ->
                assets.putIfAbsent(
                    descriptor.key,
                    PlannedAsset(prefetchedManifest, descriptor, ContentFetchPriority.SPECULATIVE),
                )
            }
        }
        return assets.map { (key, value) ->
            PlannedAcquisition(
                id = ReaderAssetAcquisitionId(key, value.priority),
                manifest = value.manifest,
                descriptor = value.descriptor,
                networkState = networkState,
                manifestRevision = input.manifestRevision,
                viewportRevision = input.viewportRevision,
            )
        }
    }

    private fun scheduleAcquisition(
        sessionId: ReaderSessionId,
        acquisition: PlannedAcquisition,
    ) {
        if (loader == null) return
        val job = coordinatorScope.launch(start = CoroutineStart.LAZY) {
            val requestState = synchronized(lock) {
                val runtime = sessions[sessionId] ?: return@launch
                if (
                    runtime.state.manifestRevision != acquisition.manifestRevision ||
                    runtime.state.viewportRevision != acquisition.viewportRevision ||
                    !acquisition.matches(runtime.state)
                ) {
                    return@launch
                }
                RequestState(
                    manifest = acquisition.manifest,
                    descriptor = acquisition.descriptor,
                    presence = runtime.state.localPresence[acquisition.descriptor.key]
                        ?: ReaderAssetLocalPresence.UNKNOWN,
                )
            }
            val outcome = load(requestState, acquisition.networkState, acquisition.id.priority)
            if (outcome is ReaderAssetLoadOutcome.Local) {
                outcome.lease.close()
                updatePresenceIfCurrent(
                    sessionId,
                    acquisition.manifestRevision,
                    acquisition.descriptor.key,
                    ReaderAssetLocalPresence.LOCAL_AVAILABLE,
                )
            }
        }
        val keep = synchronized(lock) {
            val runtime = sessions[sessionId]
            if (
                runtime?.state?.manifestRevision == acquisition.manifestRevision &&
                runtime.state.viewportRevision == acquisition.viewportRevision &&
                acquisition.matches(runtime.state)
            ) {
                runtime.acquisitionJobs[acquisition.id] = job
                true
            } else {
                false
            }
        }
        if (keep) job.start() else job.cancel()
    }

    private suspend fun load(
        requestState: RequestState,
        networkState: ReaderNetworkState,
        priority: ContentFetchPriority,
    ): ReaderAssetLoadOutcome {
        val assetLoader = loader ?: return ReaderAssetLoadOutcome.Failure(
            ReaderAssetFailure.CacheStorageUnavailable,
        )
        val presence = if (networkState == ReaderNetworkState.OFFLINE &&
            requestState.presence == ReaderAssetLocalPresence.UNKNOWN
        ) {
            inspect(setOf(requestState.descriptor.key))[requestState.descriptor.key]
                ?: ReaderAssetLocalPresence.LOCAL_UNAVAILABLE
        } else {
            requestState.presence
        }
        return if (
            networkState == ReaderNetworkState.OFFLINE &&
            presence != ReaderAssetLocalPresence.LOCAL_AVAILABLE
        ) {
            ReaderAssetLoadOutcome.Failure(ReaderAssetFailure.TransportUnavailable(retryable = false))
        } else {
            assetLoader.load(
                facts = requestState.manifest.commitFacts(requestState.descriptor),
                descriptor = requestState.descriptor,
                localPresence = presence,
                priority = priority,
                consumer = ReaderAssetConsumerToken(nextConsumerToken.getAndIncrement()),
            )
        }
    }

    private fun updatePresenceIfCurrent(
        sessionId: ReaderSessionId,
        manifestRevision: Long,
        key: ReaderPageAssetKey,
        presence: ReaderAssetLocalPresence,
    ) {
        synchronized(lock) {
            val runtime = sessions[sessionId] ?: return
            if (runtime.state.manifestRevision != manifestRevision || key !in runtime.state.localPresence) return
            runtime.state = runtime.state.copy(localPresence = runtime.state.localPresence + (key to presence))
        }
    }

    private fun recomputeProtectionsLocked(runtime: SessionRuntime) {
        runtime.state = runtime.state.copy(
            activeProtections = workingSetPolicy.protections(
                manifest = runtime.state.committedManifest,
                viewport = runtime.state.viewport,
                consumedKeys = runtime.state.consumedKeys,
                recentManifests = runtime.state.recentCommittedManifests,
                plan = runtime.state.plan,
            ),
        )
        pendingProtections = protectionUnionLocked()
    }

    private fun protectionUnionLocked(): ReaderAssetActiveProtections =
        workingSetPolicy.union(sessions.values.map { it.state.activeProtections })

    private fun publishCommittedSnapshotLocked(
        sessionId: ReaderSessionId,
        revision: Long,
        manifest: ReaderAssetChapterManifest,
    ) {
        committedSnapshots.value = committedSnapshots.value + (
            sessionId to ReaderCommittedAssetManifestSnapshot(sessionId, revision, manifest)
        )
    }

    private fun enqueueMaintenanceLocked() {
        pendingProtections = protectionUnionLocked()
        ensureMaintenanceLocked()
    }

    private fun ensureMaintenanceLocked() {
        if (maintenanceJob?.isActive == true) return
        val job = coordinatorScope.launch(start = CoroutineStart.LAZY) { maintenanceLoop() }
        maintenanceJob = job
        job.start()
    }

    private suspend fun maintenanceLoop() {
        yield()
        while (true) {
            val work = synchronized(lock) {
                val protections = pendingProtections
                if (protections == null && pendingConsumedKeys.isEmpty() && pendingReleasedSessions.isEmpty()) {
                    maintenanceJob = null
                    return
                }
                MaintenanceWork(
                    protections = protections ?: protectionUnionLocked(),
                    consumedKeys = pendingConsumedKeys.toSet(),
                    releasedSessions = pendingReleasedSessions.toSet(),
                ).also {
                    pendingProtections = null
                    pendingConsumedKeys.clear()
                    pendingReleasedSessions.clear()
                }
            }
            runStorageMaintenance { store.reconcile(work.protections) }
            work.consumedKeys.forEach { key -> runStorageMaintenance { store.markConsumed(key) } }
            work.releasedSessions.forEach { sessionId ->
                runStorageMaintenance { store.releaseSession(sessionId) }
            }
            yield()
        }
    }

    private suspend fun inspect(
        keys: Set<ReaderPageAssetKey>,
    ): Map<ReaderPageAssetKey, ReaderAssetLocalPresence> = try {
        store.inspect(keys).let { result ->
            keys.associateWith { key -> result[key] ?: ReaderAssetLocalPresence.LOCAL_UNAVAILABLE }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
        keys.associateWith { ReaderAssetLocalPresence.LOCAL_UNAVAILABLE }
    }

    private suspend fun currentNetworkState(): ReaderNetworkState = try {
        networkFacts.current()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
        ReaderNetworkState.UNKNOWN
    }

    private suspend fun currentCachePressure(): ReaderAssetCachePressure = try {
        store.cachePressure()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
        ReaderAssetCachePressure.EMERGENCY
    }

    private suspend fun runStorageMaintenance(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
            // Cache maintenance is best effort and never blocks Reader state changes.
        }
    }

    private enum class RequestValidity { CURRENT, SUPERSEDED, SECURITY_INVALIDATED }

    private data class RequestState(
        val manifest: ReaderAssetChapterManifest,
        val descriptor: ReaderPageAssetDescriptor,
        val presence: ReaderAssetLocalPresence,
    )

    private data class PlanInput(
        val manifest: ReaderAssetChapterManifest,
        val viewport: ReaderViewportSnapshot,
        val prefetchedManifest: ReaderAssetChapterManifest?,
        val manifestRevision: Long,
        val viewportRevision: Long,
    )

    private data class PlanningWork(
        val cancelledJobs: List<Job>,
        val acquisitions: List<PlannedAcquisition>,
    )

    private fun SessionRuntime.cancelManifestWorkLocked(): List<Job> = buildList {
        addAll(inspectionJobs)
        planningJob?.let(::add)
        addAll(acquisitionJobs.values)
        addAll(requestJobs.values.map(ActivePageRequest::job))
        addAll(refreshJobs.values)
        inspectionJobs.clear()
        planningJob = null
        acquisitionJobs.clear()
        requestJobs.clear()
        refreshJobs.clear()
    }

    private fun SessionRuntime.cancelDeliveryReplacementWorkLocked(): List<Job> = buildList {
        addAll(inspectionJobs)
        planningJob?.let(::add)
        addAll(acquisitionJobs.values)
        inspectionJobs.clear()
        planningJob = null
        acquisitionJobs.clear()
    }

    private fun SessionRuntime.cancelSecurityScopedWorkLocked(
        sourceNamespace: ReaderAssetSourceNamespace,
        currentManifestInvalidated: Boolean,
    ): List<Job> = buildList {
        if (currentManifestInvalidated) {
            addAll(inspectionJobs)
            inspectionJobs.clear()
            addAll(refreshJobs.values)
            refreshJobs.clear()
        }
        planningJob?.let(::add)
        planningJob = null
        acquisitionJobs.entries.removeAll { (id, job) ->
            val matching = id.key.sourceNamespace == sourceNamespace &&
                id.key.securityScope != ReaderCacheSecurityScope.Public
            if (matching) add(job)
            matching
        }
    }

    private fun SessionRuntime.cancelViewportWorkLocked(
        nextViewport: ReaderViewportSnapshot,
    ): List<Job> = buildList {
        planningJob?.let(::add)
        addAll(acquisitionJobs.values)
        requestJobs.entries.removeAll { (_, request) ->
            (!nextViewport.contains(request.imageOrdinal)).also { obsolete ->
                if (obsolete) add(request.job)
            }
        }
        planningJob = null
        acquisitionJobs.clear()
    }

    private fun SessionRuntime.cancelAllWorkLocked(): List<Job> = cancelManifestWorkLocked()

    private fun ReaderViewportSnapshot.matches(
        currentManifestRevision: Long,
        manifest: ReaderAssetChapterManifest,
    ): Boolean = sessionId == manifest.sessionId &&
        manifestRevision == currentManifestRevision &&
        (trailingVisibleImageOrdinal == null || trailingVisibleImageOrdinal < manifest.descriptors.size)

    private fun ReaderAssetChapterManifest.descriptorMatching(
        request: ReaderPageAssetRequest,
    ): ReaderPageAssetDescriptor? = descriptors.getOrNull(request.descriptor.imageOrdinal)
        ?.takeIf { descriptor -> descriptor == request.descriptor }

    private fun ReaderAssetChapterManifest.matchesDeliveryRoute(
        refreshed: ReaderAssetChapterManifest,
        sessionId: ReaderSessionId,
    ): Boolean = refreshed.sessionId == sessionId &&
        refreshed.storyId == storyId &&
        refreshed.canonicalChapterId == canonicalChapterId &&
        refreshed.selectedReleaseId == selectedReleaseId &&
        refreshed.sourceNamespace == sourceNamespace &&
        refreshed.contentVariant == contentVariant

    private fun ReaderAssetChapterManifest?.isSecurityScopedFor(
        sourceNamespace: ReaderAssetSourceNamespace,
    ): Boolean = this?.let { manifest ->
        manifest.sourceNamespace == sourceNamespace && manifest.securityScope != ReaderCacheSecurityScope.Public
    } == true

    private fun ReaderAssetSessionState.tracks(manifest: ReaderAssetChapterManifest): Boolean =
        committedManifest == manifest || prefetchedManifest == manifest

    private fun PlannedAcquisition.matches(state: ReaderAssetSessionState): Boolean =
        if (id.priority == ContentFetchPriority.SPECULATIVE) {
            state.prefetchedManifest == manifest
        } else {
            state.committedManifest == manifest
        }

    private fun ReaderAssetChapterManifest.commitFacts(
        descriptor: ReaderPageAssetDescriptor,
    ) = ReaderAssetCommitFacts(
        key = descriptor.key,
        storyId = storyId,
        canonicalChapterId = canonicalChapterId,
        releaseId = selectedReleaseId,
        sourceNamespace = sourceNamespace,
        securityScope = securityScope,
        contentVariant = contentVariant,
        identityMode = identityMode,
        persistenceMode = persistenceMode,
        imageSetNamespace = imageSetNamespace,
        imageOrdinal = descriptor.imageOrdinal,
    )

    private companion object {
        const val INSPECTION_BATCH_SIZE = 128
    }
}

private fun supersededOutcome(): ReaderAssetLoadOutcome =
    ReaderAssetLoadOutcome.Failure(ReaderAssetFailure.Superseded)

private fun routeInvalidatedOutcome(): ReaderAssetLoadOutcome =
    ReaderAssetLoadOutcome.Failure(ReaderAssetFailure.RouteInvalidated)
