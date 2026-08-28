package app.openstory.catalog.ui.story

import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.details.CatalogFullMetadataFallbackService
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.ObservationState
import app.openstory.catalog.ui.state.forExpectedKey
import app.openstory.catalog.ui.state.hasIssueOrUnavailable
import app.openstory.catalog.ui.state.retainedObservation
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class StoryCanonicalReadinessController(
    private val scope: CoroutineScope,
    private val routeStoryId: StoryId,
    private val canonical: CanonicalCatalogRepository,
    private val bootstrap: CanonicalBootstrapUseCase,
    private val fullMetadata: CatalogFullMetadataFallbackService,
    started: SharingStarted,
) {
    private val bootstrapAttempt = MutableStateFlow(StoryBootstrapAttemptState.initial())
    private var bootstrapAttemptId: Long = 0L
    private var bootstrapJob: Job? = null

    private val canonicalObservation = scope.retainedObservation(
        key = flowOf(routeStoryId),
        initialKey = routeStoryId,
        started = started,
        observe = canonical::observeStory,
        mapFailure = { _, _ -> CatalogUiFailure(OBSERVE_EXCEPTION_CODE, retryable = true) },
    )

    val state: StateFlow<StoryCanonicalReadiness> = combine(
        canonicalObservation.state,
        bootstrapAttempt,
    ) { observation, attempt ->
        StoryCanonicalReducerInput(
            observation = observation.forExpectedKey(routeStoryId),
            bootstrapAttempt = attempt,
        )
    }.scan(StoryCanonicalReducerState.initial(routeStoryId)) { previous, input ->
        reduceCanonicalReducerState(routeStoryId, previous, input)
    }.map { reducerState -> reducerState.readiness }
        .stateIn(
            scope = scope,
            started = started,
            initialValue = StoryCanonicalReducerState.initial(routeStoryId).readiness,
        )

    val resolvedStoryId: StateFlow<StoryId> = state
        .map { readiness -> readiness.resolvedStoryId }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = started,
            initialValue = routeStoryId,
        )

    init {
        startBootstrapAttempt()
    }

    fun retryContent() {
        val canonicalState = canonicalObservation.state.value.forExpectedKey(routeStoryId)
        if (canonicalState is ObservationState.Unavailable) canonicalObservation.retry()
        startBootstrapAttempt()
    }

    fun retryObservation(): Boolean {
        val canonicalState = canonicalObservation.state.value.forExpectedKey(routeStoryId)
        if (!canonicalState.hasIssueOrUnavailable()) return false
        canonicalObservation.retry()
        return true
    }

    private fun launchFullMetadataEnrichment() {
        scope.launch {
            try {
                fullMetadata.requireFull(routeStoryId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Full metadata is best-effort enrichment after canonical readiness.
            }
        }
    }

    private fun startBootstrapAttempt() {
        if (bootstrapJob?.isActive == true) return
        bootstrapAttemptId += 1L
        val attemptId = bootstrapAttemptId
        bootstrapAttempt.value = StoryBootstrapAttemptState(
            attemptId = attemptId,
            state = StoryBootstrapState.InFlight,
        )
        bootstrapJob = scope.launch {
            try {
                when (val result = bootstrap.ensureReady(routeStoryId)) {
                    is CanonicalStoryState.Ready -> {
                        bootstrapAttempt.value = StoryBootstrapAttemptState(
                            attemptId = attemptId,
                            state = StoryBootstrapState.Completed(result),
                            ready = StoryBootstrapReady(
                                attemptId = attemptId,
                                state = result,
                                observationAtCompletion = canonicalObservation.state.value.forExpectedKey(routeStoryId),
                            ),
                        )
                        launchFullMetadataEnrichment()
                    }
                    is CanonicalStoryState.Preparing -> {
                        bootstrapAttempt.value = StoryBootstrapAttemptState(
                            attemptId = attemptId,
                            state = StoryBootstrapState.Completed(result),
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                bootstrapAttempt.value = StoryBootstrapAttemptState(
                    attemptId = attemptId,
                    state = StoryBootstrapState.Failed(
                        CatalogUiFailure(BOOTSTRAP_FAILURE_CODE, retryable = true),
                    ),
                )
            }
        }
    }

    private companion object {
        const val OBSERVE_EXCEPTION_CODE = "catalog.story.observe_exception"
        const val BOOTSTRAP_FAILURE_CODE = "catalog.story.canonical_bootstrap_failed"
    }
}

private sealed interface StoryBootstrapState {
    data object InFlight : StoryBootstrapState
    data class Completed(val state: CanonicalStoryState) : StoryBootstrapState
    data class Failed(val failure: CatalogUiFailure) : StoryBootstrapState
}

private data class StoryBootstrapReady(
    val attemptId: Long,
    val state: CanonicalStoryState.Ready,
    val observationAtCompletion: ObservationState<StoryId, CanonicalStoryState?>,
)

private data class StoryBootstrapAttemptState(
    val attemptId: Long,
    val state: StoryBootstrapState,
    val ready: StoryBootstrapReady? = null,
) {
    init {
        require(ready == null || ready.attemptId == attemptId)
        require(ready == null || state is StoryBootstrapState.Completed && state.state is CanonicalStoryState.Ready)
    }

    companion object {
        fun initial() = StoryBootstrapAttemptState(
            attemptId = 0L,
            state = StoryBootstrapState.InFlight,
        )
    }
}

private data class StoryCanonicalReducerInput(
    val observation: ObservationState<StoryId, CanonicalStoryState?>,
    val bootstrapAttempt: StoryBootstrapAttemptState,
)

private data class StoryCanonicalReducerState(
    val activeBootstrapReady: StoryBootstrapReady?,
    val handledBootstrapReadyAttemptId: Long?,
    val readiness: StoryCanonicalReadiness,
) {
    companion object {
        fun initial(routeStoryId: StoryId): StoryCanonicalReducerState {
            val observation = ObservationState.Pending<StoryId>(routeStoryId)
            return StoryCanonicalReducerState(
                activeBootstrapReady = null,
                handledBootstrapReadyAttemptId = null,
                readiness = reduceCanonicalReadiness(
                    routeStoryId = routeStoryId,
                    observation = observation,
                    bootstrap = StoryBootstrapState.InFlight,
                    retainedBootstrapReady = null,
                ),
            )
        }
    }
}

internal data class StoryCanonicalReadiness(
    val routeObservation: ObservationState<StoryId, CanonicalStoryState?>,
    val content: ContentState<CanonicalStoryState.Ready>,
    val resolvedStoryId: StoryId,
)

private fun reduceCanonicalReducerState(
    routeStoryId: StoryId,
    previous: StoryCanonicalReducerState,
    input: StoryCanonicalReducerInput,
): StoryCanonicalReducerState {
    var activeReady = previous.activeBootstrapReady
    var handledAttemptId = previous.handledBootstrapReadyAttemptId
    val candidate = input.bootstrapAttempt.ready

    if (candidate != null && candidate.attemptId != handledAttemptId) {
        val observedReadyAtCompletion =
            (candidate.observationAtCompletion as? ObservationState.Available)?.value is CanonicalStoryState.Ready
        activeReady = candidate.takeUnless { observedReadyAtCompletion }
        handledAttemptId = candidate.attemptId
    }

    if (activeReady != null && input.observation.invalidates(activeReady)) {
        activeReady = null
    }

    return StoryCanonicalReducerState(
        activeBootstrapReady = activeReady,
        handledBootstrapReadyAttemptId = handledAttemptId,
        readiness = reduceCanonicalReadiness(
            routeStoryId = routeStoryId,
            observation = input.observation,
            bootstrap = input.bootstrapAttempt.state,
            retainedBootstrapReady = activeReady?.state,
        ),
    )
}

private fun ObservationState<StoryId, CanonicalStoryState?>.invalidates(
    bootstrapReady: StoryBootstrapReady,
): Boolean = this is ObservationState.Available &&
    value != null &&
    this != bootstrapReady.observationAtCompletion

private fun reduceCanonicalReadiness(
    routeStoryId: StoryId,
    observation: ObservationState<StoryId, CanonicalStoryState?>,
    bootstrap: StoryBootstrapState,
    retainedBootstrapReady: CanonicalStoryState.Ready?,
): StoryCanonicalReadiness {
    val observedReady = (observation as? ObservationState.Available)?.value as? CanonicalStoryState.Ready
    val usableReady = observedReady ?: retainedBootstrapReady
    val content = when {
        usableReady != null -> ContentState.Ready(usableReady)
        bootstrap is StoryBootstrapState.InFlight -> ContentState.Pending
        bootstrap is StoryBootstrapState.Failed -> ContentState.Failed(bootstrap.failure)
        bootstrap is StoryBootstrapState.Completed && bootstrap.state is CanonicalStoryState.Preparing -> {
            ContentState.Failed(
                CatalogUiFailure(
                    code = "catalog.story.canonical_still_preparing",
                    retryable = false,
                ),
            )
        }
        observation is ObservationState.Unavailable -> ContentState.Failed(observation.failure)
        bootstrap is StoryBootstrapState.Completed && bootstrap.state is CanonicalStoryState.Ready -> {
            ContentState.Failed(
                CatalogUiFailure(
                    code = "catalog.story.canonical_still_preparing",
                    retryable = false,
                ),
            )
        }
        else -> ContentState.Pending
    }
    val resolvedStoryId = (content as? ContentState.Ready)?.value?.story?.id ?: routeStoryId
    return StoryCanonicalReadiness(observation, content, resolvedStoryId)
}
