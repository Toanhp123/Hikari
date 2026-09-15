package app.openstory.story.feature

import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.read.StoryRouteArgs
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.story.StoryDetailSessionState
import app.openstory.library.domain.LibraryArtworkSnapshot
import app.openstory.library.domain.LibraryEntry
import app.openstory.library.domain.LibraryPresentationSnapshot
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
    private val libraryFacet: StoryLibraryFacet? = null,
    private val onUiPublished: () -> Unit = {},
    private val coroutineScope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(initialStoryDetailUiState(args))
    val state: StateFlow<StoryDetailUiState> = mutableState.asStateFlow()

    private var activeDemand: StoryCatalogFacetActivation.Available? = null
    private var quiescedDemand: StoryCatalogFacetActivation.Available? = null
    private var activationJob: Job? = null
    private var membershipJob: Job? = null
    private var membershipMutationJob: Job? = null
    private var enrichmentJob: Job? = null
    private var observedLibraryEntry: LibraryEntry? = null
    private var trustedLibrarySnapshot: LibraryPresentationSnapshot? = null
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
        membershipJob = libraryFacet?.let { facet ->
            coroutineScope.launch { observeMembership(facet, epoch) }
        }
    }

    private suspend fun observeMembership(
        facet: StoryLibraryFacet,
        epoch: Long,
    ) {
        try {
            facet.observeMembership(args.ref).collect { entry ->
                if (isCurrentActiveEpoch(epoch)) {
                    observedLibraryEntry = entry
                    mutableState.update { current -> current.withLibraryMembership(entry) }
                    scheduleSnapshotEnrichment()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Local observation failure retains the last stable membership presentation.
        }
    }

    fun toggleLibraryMembership() {
        val facet = libraryFacet ?: return
        if (released || !routeActive || membershipMutationJob?.isActive == true) return
        val previous = mutableState.value.libraryMembership
        val snapshot = if (previous == LibraryMembershipUi.NotSaved) {
            mutableState.value.toLibrarySnapshot(args.originMediaContext)
        } else {
            null
        }
        when (previous) {
            LibraryMembershipUi.NotSaved -> {
                if (snapshot != null) {
                    mutableState.update {
                        it.copy(libraryMembership = LibraryMembershipUi.Saving, libraryMutationFailed = false)
                    }
                    membershipMutationJob = coroutineScope.launch {
                        mutateMembership(previous) {
                            facet.add(args.ref, args.originMediaContext, snapshot)
                            LibraryMembershipUi.Saved
                        }
                    }
                }
            }
            LibraryMembershipUi.Saved -> {
                mutableState.update {
                    it.copy(libraryMembership = LibraryMembershipUi.Removing, libraryMutationFailed = false)
                }
                membershipMutationJob = coroutineScope.launch {
                    mutateMembership(previous) {
                        facet.remove(args.ref)
                        LibraryMembershipUi.NotSaved
                    }
                }
            }
            LibraryMembershipUi.Saving, LibraryMembershipUi.Removing -> Unit
        }
    }

    private suspend fun mutateMembership(
        previous: LibraryMembershipUi,
        mutation: suspend () -> LibraryMembershipUi,
    ) {
        try {
            val stable = mutation()
            if (!released) {
                mutableState.update {
                    it.copy(libraryMembership = stable, libraryMutationFailed = false)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            if (!released) {
                mutableState.update {
                    it.copy(libraryMembership = previous, libraryMutationFailed = true)
                }
            }
        }
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
        membershipJob?.cancel()
        membershipJob = null
        enrichmentJob?.cancel()
        enrichmentJob = null
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
        membershipJob?.cancel()
        membershipJob = null
        enrichmentJob?.cancel()
        enrichmentJob = null

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
        if (runtimeState.projection != null) {
            trustedLibrarySnapshot = mutableState.value.toLibrarySnapshot(args.originMediaContext)
            scheduleSnapshotEnrichment()
            onUiPublished()
        }
    }

    private fun scheduleSnapshotEnrichment() {
        val facet = libraryFacet
        val entry = observedLibraryEntry
        val snapshot = trustedLibrarySnapshot
        if (facet != null && entry != null && snapshot != null) {
            val lifecycleAllowsEnrichment = routeActive && !released
            val snapshotChanged = entry.snapshot != snapshot
            val enrichmentIdle = enrichmentJob?.isActive != true
            if (lifecycleAllowsEnrichment && snapshotChanged && enrichmentIdle) {
                enrichmentJob = coroutineScope.launch {
                    try {
                        facet.enrichSnapshot(args.ref, snapshot)
                        observedLibraryEntry = observedLibraryEntry?.copy(snapshot = snapshot)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (_: Exception) {
                        // Enrichment is opportunistic and never changes stable membership state.
                    } finally {
                        enrichmentJob = null
                        if (trustedLibrarySnapshot != snapshot) scheduleSnapshotEnrichment()
                    }
                }
            }
        }
    }
}

private fun StoryDetailUiState.toLibrarySnapshot(
    originMediaContext: app.openstory.catalog.domain.model.CatalogMediaType,
): LibraryPresentationSnapshot? {
    val currentSummary = summary ?: return null
    val currentArtwork = artwork.assetKey?.let { key ->
        artwork.locator?.let { locator -> LibraryArtworkSnapshot(key, locator) }
    }
    val supportingText = when (originMediaContext) {
        app.openstory.catalog.domain.model.CatalogMediaType.MANGA -> "Manga"
        app.openstory.catalog.domain.model.CatalogMediaType.LIGHT_NOVEL -> "Light Novel"
    }
    return LibraryPresentationSnapshot(
        title = currentSummary.title,
        artwork = currentArtwork,
        supportingText = supportingText,
    )
}
