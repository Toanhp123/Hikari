package app.openstory.catalog.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.ObservationState
import app.openstory.catalog.ui.state.RefreshState
import app.openstory.catalog.ui.state.completeFailure
import app.openstory.catalog.ui.state.completeSuccess
import app.openstory.catalog.ui.state.forExpectedKey
import app.openstory.catalog.ui.state.retainedObservation
import app.openstory.catalog.ui.state.startAttempt
import app.openstory.common.id.StoryId
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModel @Inject constructor(
    repository: CatalogRepository,
    projections: CatalogStoryProjectionRepository,
    private val refreshPipeline: DiscoverRefreshPipeline,
    private val projectionPipeline: DiscoverProjectionPipeline,
    private val canonicalBootstrap: DiscoverCanonicalBootstrapPipeline,
) : ViewModel() {
    private val started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
    private val selectedContentType = MutableStateFlow(ContentType.MANGA)
    private val bootstrapState = MutableStateFlow<DiscoverBootstrapState>(DiscoverBootstrapState.AwaitingHome)
    private val refreshState = MutableStateFlow(RefreshState())
    private val refreshReport = MutableStateFlow<DiscoverRefreshReport?>(null)
    private val homeEmissionVersion = AtomicLong(0L)
    private var bootstrapJob: Job? = null
    private var refreshJob: Job? = null

    private val homeObservation = viewModelScope.retainedObservation(
        key = flowOf(Unit),
        initialKey = Unit,
        started = started,
        observe = {
            repository.observeHomes().map { homes ->
                DiscoverObservedHomes(
                    version = homeEmissionVersion.incrementAndGet(),
                    homes = homes,
                )
            }
        },
        mapFailure = { _, _ -> CatalogUiFailure(HOME_OBSERVE_EXCEPTION_CODE, retryable = true) },
    )

    private val bootstrapRetired = homeObservation.state
        .runningFold(false) { retired, homeState ->
            retired || (
                (homeState as? ObservationState.Available<Unit, DiscoverObservedHomes>)
                    ?.value
                    ?.homes
                    ?.isNotEmpty() == true
                )
        }
        .stateIn(
            scope = viewModelScope,
            started = started,
            initialValue = false,
        )

    private val effectiveBootstrapState = combine(
        bootstrapState,
        bootstrapRetired,
        homeObservation.state,
    ) { bootstrap, retired, homeState ->
        bootstrap.effectiveFor(homeState, retired)
    }.stateIn(
        scope = viewModelScope,
        started = started,
        initialValue = DiscoverBootstrapState.AwaitingHome,
    )

    private val settlementKey = combine(
        homeObservation.state,
        effectiveBootstrapState,
        selectedContentType,
    ) { homeState, bootstrap, contentType ->
        discoverSettlementKey(
            homeState = homeState,
            bootstrap = bootstrap,
            contentType = contentType,
        )
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = started,
            initialValue = DiscoverSettlementKey(ContentType.MANGA, emptyList()),
        )

    private val settlementObservation = viewModelScope.retainedObservation(
        key = settlementKey,
        initialKey = DiscoverSettlementKey(ContentType.MANGA, emptyList()),
        started = started,
        observe = { key -> canonicalBootstrap.settle(key.storyIds, key.contentType) },
        mapFailure = { _, _ -> CatalogUiFailure(SETTLEMENT_OBSERVE_EXCEPTION_CODE, retryable = true) },
    )

    private val projectionObservation = viewModelScope.retainedObservation(
        key = settlementKey,
        initialKey = DiscoverSettlementKey(ContentType.MANGA, emptyList()),
        started = started,
        observe = { key ->
            if (key.storyIds.isEmpty()) {
                flowOf(emptyList())
            } else {
                projections.observeForStories(key.storyIds.toSet())
            }
        },
        mapFailure = { _, _ -> CatalogUiFailure(PROJECTION_OBSERVE_EXCEPTION_CODE, retryable = true) },
    )

    private val canonicalReadiness = combine(
        settlementKey,
        settlementObservation.state,
        projectionObservation.state,
    ) { key, settlements, liveProjections ->
        DiscoverCanonicalReadiness(
            key = key,
            settlements = settlements.forExpectedKey(key),
            liveProjections = liveProjections.forExpectedKey(key),
        )
    }

    private val candidate = combine(
        homeObservation.state,
        effectiveBootstrapState,
        selectedContentType,
        canonicalReadiness,
    ) { homeState, bootstrap, contentType, canonical ->
        DiscoverCandidateInput(
            homeState = homeState,
            bootstrap = bootstrap,
            contentType = contentType,
            settlementKey = discoverSettlementKey(homeState, bootstrap, contentType),
            canonical = canonical,
        )
    }.mapLatest(::buildCandidate)

    private val retainedCandidate = candidate
        .runningFold(DiscoverReducedContent()) { previous, current ->
            reduceDiscoverCandidate(previous, current)
        }
        .stateIn(
            scope = viewModelScope,
            started = started,
            initialValue = DiscoverReducedContent(),
        )

    val state = combine(
        retainedCandidate,
        refreshState,
        refreshReport,
    ) { reduced, refresh, report ->
        DiscoverUiState(
            content = reduced.content,
            refresh = refresh,
            refreshReport = report,
            observationIssue = reduced.observationIssue,
        )
    }.stateIn(
        scope = viewModelScope,
        started = started,
        initialValue = DiscoverUiState(),
    )

    init {
        viewModelScope.launch {
            val firstAvailable = homeObservation.state.first { it is ObservationState.Available }
                as ObservationState.Available<Unit, DiscoverObservedHomes>
            if (firstAvailable.value.homes.isEmpty()) {
                startAutomaticBootstrap()
            } else {
                bootstrapState.value = DiscoverBootstrapState.NotNeeded
            }
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshState.update(RefreshState::startAttempt)
            try {
                val execution = refreshPipeline.refresh()
                refreshReport.value = execution.report
                reclassifyBootstrapAfterManualRefresh(execution)
                refreshState.update(RefreshState::completeSuccess)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                refreshState.update {
                    it.completeFailure(CatalogUiFailure(REFRESH_EXCEPTION_CODE, retryable = true))
                }
            }
        }
    }

    fun retryContent() {
        when (val homeState = homeObservation.state.value) {
            is ObservationState.Unavailable -> {
                if (homeState.failure.retryable) homeObservation.retry()
                return
            }
            is ObservationState.Pending,
            is ObservationState.Available -> Unit
        }

        val bootstrap = effectiveBootstrapState.value
        if (bootstrap is DiscoverBootstrapState.Failed) {
            if (bootstrap.failure.retryable) startAutomaticBootstrap()
            return
        }

        val key = settlementKey.value
        when (val settlements = settlementObservation.state.value.forExpectedKey(key)) {
            is ObservationState.Unavailable -> {
                if (settlements.failure.retryable) settlementObservation.retry()
            }
            is ObservationState.Available -> {
                if (
                    state.value.content is ContentState.Failed &&
                    settlements.value.preferredSettlementFailure(key.storyIds)?.retryable == true
                ) {
                    settlementObservation.retry()
                }
            }
            is ObservationState.Pending -> Unit
        }
    }

    fun retryObservation() {
        observationRetryAction()?.invoke()
    }

    fun selectContentType(contentType: ContentType) {
        if (contentType != ContentType.MANGA) return
        selectedContentType.value = contentType
    }

    private fun startAutomaticBootstrap() {
        if (bootstrapJob?.isActive == true) return
        val currentHome = (homeObservation.state.value as? ObservationState.Available)?.value ?: return
        if (currentHome.homes.isNotEmpty()) {
            bootstrapState.value = DiscoverBootstrapState.NotNeeded
        } else {
            val startedAfterHomeVersion = currentHome.version
            bootstrapJob = viewModelScope.launch {
                bootstrapState.value = DiscoverBootstrapState.InFlight(startedAfterHomeVersion)
                try {
                    val execution = refreshPipeline.refresh()
                    refreshReport.value = execution.report
                    val inFlight = bootstrapState.value as? DiscoverBootstrapState.InFlight
                    if (inFlight != null) {
                        bootstrapState.value = execution.toBootstrapState(inFlight.startedAfterHomeVersion)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    val inFlight = bootstrapState.value as? DiscoverBootstrapState.InFlight
                    if (inFlight != null) {
                        bootstrapState.value = DiscoverBootstrapState.Failed(
                            failure = CatalogUiFailure(BOOTSTRAP_EXCEPTION_CODE, retryable = true),
                            startedAfterHomeVersion = inFlight.startedAfterHomeVersion,
                        )
                    }
                }
            }
        }
    }

    private fun reclassifyBootstrapAfterManualRefresh(execution: DiscoverRefreshExecution) {
        val currentHome = (homeObservation.state.value as? ObservationState.Available)?.value ?: return
        if (
            currentHome.homes.isNotEmpty() ||
            bootstrapRetired.value ||
            !bootstrapState.value.allowsManualReclassification(execution)
        ) {
            return
        }
        bootstrapState.value = execution.toBootstrapState(currentHome.version)
    }

    private fun DiscoverRefreshExecution.toBootstrapState(
        startedAfterHomeVersion: Long,
    ): DiscoverBootstrapState = when {
        homes.isNotEmpty() || noEnabledProviders -> DiscoverBootstrapState.Completed(
            execution = this,
            startedAfterHomeVersion = startedAfterHomeVersion,
        )
        allProvidersFailed -> DiscoverBootstrapState.Failed(
            failure = CatalogUiFailure(
                BOOTSTRAP_ALL_PROVIDERS_FAILED_CODE,
                retryable = anyRetryableFailure,
            ),
            startedAfterHomeVersion = startedAfterHomeVersion,
        )
        else -> DiscoverBootstrapState.Completed(
            execution = this,
            startedAfterHomeVersion = startedAfterHomeVersion,
        )
    }

    private suspend fun buildCandidate(input: DiscoverCandidateInput): DiscoverCandidate =
        when (val homeState = input.homeState) {
            is ObservationState.Pending -> DiscoverCandidate(input.contentType, ContentState.Pending)
            is ObservationState.Unavailable -> DiscoverCandidate(
                identity = input.contentType,
                content = ContentState.Failed(homeState.failure),
                observationIssue = homeState.failure,
            )
            is ObservationState.Available -> buildAvailableHomeCandidate(input, homeState)
        }

    private suspend fun buildAvailableHomeCandidate(
        input: DiscoverCandidateInput,
        availableHome: ObservationState.Available<Unit, DiscoverObservedHomes>,
    ): DiscoverCandidate {
        val identity = input.contentType
        val homes = availableHome.effectiveHomes(input.bootstrap)
        val canonical = input.canonical
        return when {
            homes.isEmpty() -> emptyHomesCandidate(
                identity = identity,
                homeIssue = availableHome.issue,
                bootstrap = input.bootstrap,
            )
            canonical.key != input.settlementKey -> DiscoverCandidate(
                identity = identity,
                content = ContentState.Pending,
                observationIssue = availableHome.issue,
            )
            else -> buildCanonicalCandidate(identity, homes, availableHome, canonical)
        }
    }

    private suspend fun buildCanonicalCandidate(
        identity: ContentType,
        homes: List<CatalogHomeSnapshot>,
        availableHome: ObservationState.Available<Unit, DiscoverObservedHomes>,
        canonical: DiscoverCanonicalReadiness,
    ): DiscoverCandidate {
        val baseIssue = availableHome.issue
            ?: canonical.settlements.issueOrUnavailable()
            ?: canonical.liveProjections.issueOrUnavailable()
        return when (val settlements = canonical.settlements) {
            is ObservationState.Pending -> DiscoverCandidate(identity, ContentState.Pending, baseIssue)
            is ObservationState.Unavailable -> DiscoverCandidate(
                identity = identity,
                content = ContentState.Failed(settlements.failure),
                observationIssue = baseIssue ?: settlements.failure,
            )
            is ObservationState.Available -> buildProjectedCandidate(
                identity = identity,
                homes = homes,
                availableHome = availableHome,
                canonical = canonical,
                availableSettlements = settlements,
            )
        }
    }

    private suspend fun buildProjectedCandidate(
        identity: ContentType,
        homes: List<CatalogHomeSnapshot>,
        availableHome: ObservationState.Available<Unit, DiscoverObservedHomes>,
        canonical: DiscoverCanonicalReadiness,
        availableSettlements: ObservationState.Available<
            DiscoverSettlementKey,
            Map<StoryId, DiscoverCanonicalSettlement>,
        >,
    ): DiscoverCandidate {
        val liveProjections = canonical.liveProjections.availableProjectionsOrEmpty()
        val projected = projectionPipeline.project(
            homes = homes,
            projections = liveProjections,
            selectedContentType = identity,
            settlements = availableSettlements.value,
        )
        val terminalFailure = projected.failures.preferredProjectionFailure(canonical.key.storyIds)
        val observationIssue = availableHome.issue
            ?: availableSettlements.issue
            ?: terminalFailure
            ?: canonical.liveProjections.issueOrUnavailable()

        return when {
            projected.content.hasContent -> DiscoverCandidate(
                identity = identity,
                content = ContentState.Ready(projected.content.toContent()),
                observationIssue = observationIssue,
            )
            projected.pendingSlots > 0 -> DiscoverCandidate(
                identity = identity,
                content = ContentState.Pending,
                observationIssue = observationIssue,
            )
            terminalFailure != null -> DiscoverCandidate(
                identity = identity,
                content = ContentState.Failed(terminalFailure),
                observationIssue = observationIssue,
            )
            else -> DiscoverCandidate(
                identity = identity,
                content = ContentState.Ready(
                    projected.content.toContent(DiscoverNoContentReason.EMPTY_FEED),
                ),
                observationIssue = observationIssue,
            )
        }
    }

    private fun observationRetryAction(): (() -> Unit)? {
        if (state.value.content !is ContentState.Ready) return null

        val homeIssue = (homeObservation.state.value as? ObservationState.Available)?.issue
        return if (homeIssue != null) {
            if (homeIssue.retryable) homeObservation::retry else null
        } else {
            settlementObservationRetryAction(settlementKey.value)
        }
    }

    private fun settlementObservationRetryAction(key: DiscoverSettlementKey): (() -> Unit)? =
        when (val settlements = settlementObservation.state.value.forExpectedKey(key)) {
            is ObservationState.Unavailable -> if (settlements.failure.retryable) {
                settlementObservation::retry
            } else {
                null
            }
            is ObservationState.Available -> {
                val failure = settlements.issue
                    ?: settlements.value.preferredSettlementFailure(key.storyIds)
                if (failure != null) {
                    if (failure.retryable) settlementObservation::retry else null
                } else {
                    projectionObservationRetryAction(key)
                }
            }
            is ObservationState.Pending -> projectionObservationRetryAction(key)
        }

    private fun projectionObservationRetryAction(key: DiscoverSettlementKey): (() -> Unit)? =
        when (val projections = projectionObservation.state.value.forExpectedKey(key)) {
            is ObservationState.Unavailable -> if (projections.failure.retryable) {
                projectionObservation::retry
            } else {
                null
            }
            is ObservationState.Available -> projections.issue?.let { issue ->
                if (issue.retryable) projectionObservation::retry else null
            }
            is ObservationState.Pending -> null
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val HOME_OBSERVE_EXCEPTION_CODE = "catalog.home.observe_exception"
        const val SETTLEMENT_OBSERVE_EXCEPTION_CODE = "catalog.discover.settlement_observe_exception"
        const val PROJECTION_OBSERVE_EXCEPTION_CODE = "catalog.home.ranking_exception"
        const val BOOTSTRAP_ALL_PROVIDERS_FAILED_CODE = "catalog.discover.bootstrap_all_providers_failed"
        const val BOOTSTRAP_EXCEPTION_CODE = "catalog.discover.bootstrap_refresh_exception"
        const val REFRESH_EXCEPTION_CODE = "catalog.home.refresh_exception"
    }
}

private data class DiscoverSettlementKey(
    val contentType: ContentType,
    val storyIds: List<StoryId>,
)

private sealed interface DiscoverBootstrapState {
    data object AwaitingHome : DiscoverBootstrapState
    data object NotNeeded : DiscoverBootstrapState
    data class InFlight(val startedAfterHomeVersion: Long) : DiscoverBootstrapState
    data class Completed(
        val execution: DiscoverRefreshExecution,
        val startedAfterHomeVersion: Long,
    ) : DiscoverBootstrapState
    data class Failed(
        val failure: CatalogUiFailure,
        val startedAfterHomeVersion: Long,
    ) : DiscoverBootstrapState
}

private fun DiscoverBootstrapState.allowsManualReclassification(
    newExecution: DiscoverRefreshExecution,
): Boolean = when (this) {
    DiscoverBootstrapState.NotNeeded -> false
    is DiscoverBootstrapState.Completed -> execution.homes.isEmpty() && !newExecution.allProvidersFailed
    DiscoverBootstrapState.AwaitingHome,
    is DiscoverBootstrapState.InFlight,
    is DiscoverBootstrapState.Failed -> true
}

private data class DiscoverObservedHomes(
    val version: Long,
    val homes: List<CatalogHomeSnapshot>,
)

private fun DiscoverBootstrapState.effectiveFor(
    homeState: ObservationState<Unit, DiscoverObservedHomes>,
    retired: Boolean,
): DiscoverBootstrapState {
    val observed = (homeState as? ObservationState.Available)?.value
    return when {
        retired -> DiscoverBootstrapState.NotNeeded
        observed == null -> this
        else -> when (this) {
            DiscoverBootstrapState.AwaitingHome,
            DiscoverBootstrapState.NotNeeded -> this
            is DiscoverBootstrapState.InFlight -> if (
                observed.version > startedAfterHomeVersion && observed.homes.isNotEmpty()
            ) {
                DiscoverBootstrapState.NotNeeded
            } else {
                this
            }
            is DiscoverBootstrapState.Completed -> if (
                observed.homes.isNotEmpty() ||
                (execution.homes.isNotEmpty() && observed.version > startedAfterHomeVersion)
            ) {
                DiscoverBootstrapState.NotNeeded
            } else {
                this
            }
            is DiscoverBootstrapState.Failed -> if (observed.homes.isNotEmpty()) {
                DiscoverBootstrapState.NotNeeded
            } else {
                this
            }
        }
    }
}

private fun ObservationState<Unit, DiscoverObservedHomes>.availableHomeVersionOrNull(): Long? =
    (this as? ObservationState.Available)?.value?.version

private data class DiscoverCanonicalReadiness(
    val key: DiscoverSettlementKey,
    val settlements: ObservationState<DiscoverSettlementKey, Map<StoryId, DiscoverCanonicalSettlement>>,
    val liveProjections: ObservationState<DiscoverSettlementKey, List<CatalogStoryProjection>>,
)

private data class DiscoverCandidateInput(
    val homeState: ObservationState<Unit, DiscoverObservedHomes>,
    val bootstrap: DiscoverBootstrapState,
    val contentType: ContentType,
    val settlementKey: DiscoverSettlementKey,
    val canonical: DiscoverCanonicalReadiness,
)

private data class DiscoverCandidate(
    val identity: ContentType,
    val content: ContentState<DiscoverContent>,
    val observationIssue: CatalogUiFailure? = null,
)

private data class DiscoverReducedContent(
    val identity: ContentType = ContentType.MANGA,
    val content: ContentState<DiscoverContent> = ContentState.Pending,
    val observationIssue: CatalogUiFailure? = null,
)

private fun reduceDiscoverCandidate(
    previous: DiscoverReducedContent,
    current: DiscoverCandidate,
): DiscoverReducedContent {
    val previousReady = previous.content as? ContentState.Ready
    val canRetain = previousReady != null && previous.identity == current.identity
    return when (val candidate = current.content) {
        is ContentState.Ready -> DiscoverReducedContent(
            identity = current.identity,
            content = candidate,
            observationIssue = current.observationIssue,
        )
        ContentState.Pending -> if (canRetain) {
            DiscoverReducedContent(
                identity = current.identity,
                content = previousReady,
                observationIssue = current.observationIssue,
            )
        } else {
            DiscoverReducedContent(identity = current.identity)
        }
        is ContentState.Failed -> if (canRetain) {
            DiscoverReducedContent(
                identity = current.identity,
                content = previousReady,
                observationIssue = current.observationIssue ?: candidate.failure,
            )
        } else {
            DiscoverReducedContent(
                identity = current.identity,
                content = candidate,
            )
        }
    }
}

private fun emptyHomesCandidate(
    identity: ContentType,
    homeIssue: CatalogUiFailure?,
    bootstrap: DiscoverBootstrapState,
): DiscoverCandidate = when (bootstrap) {
    DiscoverBootstrapState.AwaitingHome,
    is DiscoverBootstrapState.InFlight -> DiscoverCandidate(identity, ContentState.Pending, homeIssue)
    DiscoverBootstrapState.NotNeeded -> DiscoverCandidate(
        identity,
        ContentState.Ready(
            DiscoverSemanticContent.empty(identity).toContent(DiscoverNoContentReason.EMPTY_FEED),
        ),
        homeIssue,
    )
    is DiscoverBootstrapState.Completed -> {
        val reason = if (bootstrap.execution.noEnabledProviders) {
            DiscoverNoContentReason.NO_ENABLED_PROVIDERS
        } else {
            DiscoverNoContentReason.EMPTY_FEED
        }
        DiscoverCandidate(
            identity,
            ContentState.Ready(DiscoverSemanticContent.empty(identity).toContent(reason)),
            homeIssue,
        )
    }
    is DiscoverBootstrapState.Failed -> DiscoverCandidate(
        identity,
        ContentState.Failed(bootstrap.failure),
        homeIssue ?: bootstrap.failure,
    )
}

private fun discoverSettlementKey(
    homeState: ObservationState<Unit, DiscoverObservedHomes>,
    bootstrap: DiscoverBootstrapState,
    contentType: ContentType,
): DiscoverSettlementKey = DiscoverSettlementKey(
    contentType = contentType,
    storyIds = homeState.effectiveHomesOrNull(bootstrap)
        ?.let { discoverFeedSlots(it, contentType).expectedStoryIds }
        .orEmpty(),
)

private fun ObservationState<Unit, DiscoverObservedHomes>.effectiveHomesOrNull(
    bootstrap: DiscoverBootstrapState,
): List<CatalogHomeSnapshot>? = (this as? ObservationState.Available)
    ?.effectiveHomes(bootstrap)

private fun ObservationState.Available<Unit, DiscoverObservedHomes>.effectiveHomes(
    bootstrap: DiscoverBootstrapState,
): List<CatalogHomeSnapshot> = when {
    value.homes.isNotEmpty() -> value.homes
    bootstrap is DiscoverBootstrapState.Completed && bootstrap.execution.homes.isNotEmpty() -> bootstrap.execution.homes
    else -> value.homes
}

private fun ObservationState<DiscoverSettlementKey, List<CatalogStoryProjection>>.availableProjectionsOrEmpty():
    List<CatalogStoryProjection> = when (this) {
        is ObservationState.Available -> value
        is ObservationState.Pending,
        is ObservationState.Unavailable -> emptyList()
    }

private fun ObservationState<*, *>.issueOrUnavailable(): CatalogUiFailure? = when (this) {
    is ObservationState.Available<*, *> -> issue
    is ObservationState.Unavailable<*> -> failure
    is ObservationState.Pending<*> -> null
}

private fun Map<StoryId, DiscoverCanonicalSettlement>.preferredSettlementFailure(
    storyIds: List<StoryId>,
): CatalogUiFailure? {
    val ordered = storyIds.mapNotNull { storyId ->
        (this[storyId] as? DiscoverCanonicalSettlement.Failed)?.failure
    }
    return ordered.firstOrNull(CatalogUiFailure::retryable) ?: ordered.firstOrNull()
}

private fun Map<StoryId, CatalogUiFailure>.preferredProjectionFailure(
    storyIds: List<StoryId>,
): CatalogUiFailure? {
    val ordered = storyIds.mapNotNull(::get)
    return ordered.firstOrNull(CatalogUiFailure::retryable) ?: ordered.firstOrNull()
}
