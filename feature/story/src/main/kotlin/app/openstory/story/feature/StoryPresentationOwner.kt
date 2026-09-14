package app.openstory.story.feature

import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.story.StoryDetailSessionState
import app.openstory.story.feature.state.StoryIssueUi
import app.openstory.story.feature.state.toStoryIssueUi
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class StoryPresentationOwner(
    val args: StoryRouteArgs,
    private val catalogFacet: StoryCatalogFacet,
    private val onUiPublished: () -> Unit = {},
    private val coroutineScope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(initialStoryDetailUiState(args))
    val state: StateFlow<StoryDetailUiState> = mutableState.asStateFlow()

    private var activeDemand: StoryCatalogFacetActivation.Available? = null
    private var quiescedDemand: StoryCatalogFacetActivation.Available? = null
    private var activationJob: Job? = null
    private var retryJob: Job? = null
    private var quiesceJob: Job? = null
    private var lifecycleEpoch = 0L
    private var routeActive = false
    private var released = false

    fun activate() {
        if (released) return
        if (routeActive && (activeDemand != null || activationJob?.isActive == true)) return

        routeActive = true
        val epoch = ++lifecycleEpoch
        activationJob = coroutineScope.launch { runActivation(epoch) }
    }

    private suspend fun runActivation(epoch: Long) {
        awaitPendingQuiesce()
        releaseQuiescedDemand()
        if (!isCurrentActiveEpoch(epoch)) return

        try {
            when (val activation = catalogFacet.activate(args.ref)) {
                is StoryCatalogFacetActivation.Unavailable -> {
                    if (isCurrentActiveEpoch(epoch)) {
                        mutableState.update { current ->
                            current.copy(
                                detailLoading = false,
                                issue = activation.failure.toStoryIssueUi(),
                            )
                        }
                    }
                }
                is StoryCatalogFacetActivation.Available -> collectActivation(activation, epoch)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: CatalogFailureException) {
            publishActivationFailure(epoch, failure)
        }
    }

    private suspend fun collectActivation(
        activation: StoryCatalogFacetActivation.Available,
        epoch: Long,
    ) {
        if (!isCurrentActiveEpoch(epoch)) {
            releaseDemand(activation)
            return
        }

        activeDemand = activation
        try {
            mutableState.update { it.copy(issue = null) }
            activation.states.collect { runtimeState ->
                if (isCurrentActiveEpoch(epoch) && activeDemand === activation) {
                    reduce(runtimeState)
                }
            }
        } finally {
            if (activeDemand === activation) {
                activeDemand = null
                releaseDemand(activation)
            }
        }
    }

    fun retry() {
        if (released || !routeActive || retryJob?.isActive == true) return
        val demand = activeDemand
        if (demand == null) {
            mutableState.update { current -> current.copy(detailLoading = true, issue = null) }
            activate()
            return
        }

        val epoch = lifecycleEpoch
        retryJob = coroutineScope.launch { runRetry(demand, epoch) }
    }

    private suspend fun runRetry(
        demand: StoryCatalogFacetActivation.Available,
        epoch: Long,
    ) {
        if (!ownsActiveDemand(demand, epoch)) return
        mutableState.update { current -> current.copy(detailLoading = true, issue = null) }
        try {
            when (val result = demand.retry()) {
                CatalogAcquisitionResult.Success -> Unit
                is CatalogAcquisitionResult.Failed -> publishRetryFailure(
                    demand = demand,
                    epoch = epoch,
                    issue = result.failure.toStoryIssueUi(),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: CatalogFailureException) {
            publishRetryFailure(demand, epoch, failure.toStoryIssueUi())
        }
    }

    private fun publishActivationFailure(
        epoch: Long,
        failure: CatalogFailureException,
    ) {
        if (!isCurrentActiveEpoch(epoch)) return
        mutableState.update { current ->
            current.copy(
                detailLoading = false,
                issue = failure.toStoryIssueUi(),
            )
        }
    }

    private fun publishRetryFailure(
        demand: StoryCatalogFacetActivation.Available,
        epoch: Long,
        issue: StoryIssueUi,
    ) {
        if (!ownsActiveDemand(demand, epoch)) return
        mutableState.update { current ->
            current.copy(
                detailLoading = false,
                issue = issue,
            )
        }
    }

    private fun ownsActiveDemand(
        demand: StoryCatalogFacetActivation.Available,
        epoch: Long,
    ): Boolean = isCurrentActiveEpoch(epoch) && activeDemand === demand

    fun quiesce() {
        if (released) return
        routeActive = false
        lifecycleEpoch++
        retryJob?.cancel()
        retryJob = null
        activationJob?.cancel()
        val demand = activeDemand ?: return
        activeDemand = null
        check(quiescedDemand == null) { "Story route already owns a quiesced demand" }
        quiescedDemand = demand
        quiesceJob = coroutineScope.launch {
            try {
                demand.quiesce()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // Route retention remains fail-closed. RELEASED still performs terminal cleanup.
            }
        }
    }

    fun release() {
        if (released) return
        released = true
        routeActive = false
        lifecycleEpoch++

        val active = activeDemand
        activeDemand = null
        val quiesced = quiescedDemand
        quiescedDemand = null
        val collecting = activationJob
        activationJob = null
        retryJob?.cancel()
        retryJob = null

        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                try {
                    collecting?.cancelAndJoin()
                    awaitPendingQuiesce()
                    releaseDemand(active)
                    if (quiesced !== active) releaseDemand(quiesced)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    // RELEASED is terminal even if runtime cleanup reports an error.
                }
            }
        }
    }

    private fun isCurrentActiveEpoch(epoch: Long): Boolean =
        !released && routeActive && lifecycleEpoch == epoch

    private suspend fun awaitPendingQuiesce() {
        val pending = quiesceJob ?: return
        pending.join()
        if (quiesceJob === pending) quiesceJob = null
    }

    private suspend fun releaseQuiescedDemand() {
        val demand = quiescedDemand ?: return
        quiescedDemand = null
        releaseDemand(demand)
    }

    private suspend fun releaseDemand(demand: StoryCatalogFacetActivation.Available?) {
        if (demand == null) return
        try {
            demand.release()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Cleanup failure must not reactivate or retain a terminal/obsolete demand.
        }
    }

    private fun reduce(runtimeState: StoryDetailSessionState) {
        mutableState.update { previous ->
            runtimeState.toStoryDetailUiState(previous)
        }
        if (runtimeState.projection != null) onUiPublished()
    }
}
