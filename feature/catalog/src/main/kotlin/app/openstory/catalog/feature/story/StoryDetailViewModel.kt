package app.openstory.catalog.feature.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.domain.asset.CoverAssetKey
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.read.StoryDetailProjection
import app.openstory.catalog.feature.state.toCatalogIssueUi
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.story.StoryDetailSessionState
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal interface StoryDetailRuntime {
    suspend fun activate(ref: StorySourceRef): StoryDetailRuntimeActivation
}

internal sealed interface StoryDetailRuntimeActivation {
    data class Unavailable(val failure: CatalogFailure) : StoryDetailRuntimeActivation

    data class Available(
        val states: Flow<StoryDetailSessionState>,
        val retry: suspend () -> CatalogAcquisitionResult,
        val release: suspend () -> Unit,
    ) : StoryDetailRuntimeActivation
}

internal class CatalogStoryDetailRuntime(
    private val activateCatalog: suspend () -> CatalogCapabilityActivation,
) : StoryDetailRuntime {
    override suspend fun activate(ref: StorySourceRef): StoryDetailRuntimeActivation =
        when (val activation = activateCatalog()) {
            is CatalogCapabilityActivation.Unavailable ->
                StoryDetailRuntimeActivation.Unavailable(activation.failure)
            is CatalogCapabilityActivation.Available -> {
                val session = activation.storyDetailSession(ref)
                StoryDetailRuntimeActivation.Available(
                    states = session.activate(),
                    retry = session::retry,
                    release = session::release,
                )
            }
        }
}

internal class StoryDetailViewModel(
    private val runtime: StoryDetailRuntime,
) : ViewModel() {
    private val mutableState = MutableStateFlow<StoryDetailUiState?>(null)
    val state: StateFlow<StoryDetailUiState?> = mutableState.asStateFlow()

    private var openJob: Job? = null
    private var releaseJob: Job? = null
    private var activeRef: StorySourceRef? = null
    private var activeDemand: StoryDetailRuntimeActivation.Available? = null
    private var routeCoverAssetKey: CoverAssetKey? = null

    fun open(
        ref: StorySourceRef,
        coverAssetKey: CoverAssetKey?,
        onDestinationRejected: () -> Unit = {},
        onDestinationReady: () -> Unit,
    ) {
        if (activeRef == ref && activeDemand != null) {
            onDestinationReady()
            return
        }
        if (openJob?.isActive == true) return
        openJob = viewModelScope.launch {
            awaitPendingRelease()
            releaseActiveDemand()
            try {
                when (val activation = runtime.activate(ref)) {
                    is StoryDetailRuntimeActivation.Unavailable -> onDestinationRejected()
                    is StoryDetailRuntimeActivation.Available -> {
                        activeRef = ref
                        activeDemand = activation
                        routeCoverAssetKey = coverAssetKey
                        mutableState.value = StoryDetailUiState(
                            ref = ref,
                            summary = null,
                            detail = null,
                            detailLoading = true,
                            issue = null,
                            destinationActive = true,
                            coverLocator = null,
                            coverAssetKey = coverAssetKey,
                        )
                        onDestinationReady()
                        activation.states.collect(::reduce)
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: CatalogFailureException) {
                rejectDestination(onDestinationRejected)
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _: Throwable) {
                rejectDestination(onDestinationRejected)
            }
        }
    }

    fun retry() {
        val demand = activeDemand ?: return
        viewModelScope.launch {
            mutableState.update { current -> current?.copy(detailLoading = true, issue = null) }
            when (val result = demand.retry()) {
                CatalogAcquisitionResult.Success -> Unit
                is CatalogAcquisitionResult.Failed -> mutableState.update { current ->
                    current?.copy(
                        detailLoading = false,
                        issue = result.failure.toCatalogIssueUi(),
                    )
                }
            }
        }
    }

    fun closeDestination() {
        openJob?.cancel()
        openJob = null
        val demand = detachActiveDemand() ?: return
        releaseJob = viewModelScope.launch {
            try {
                demand.release()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _: Throwable) {
                // The destination is already closed; bounded retention cleanup can retry on a later mutation.
            }
        }
    }

    private fun reduce(runtimeState: StoryDetailSessionState) {
        mutableState.update { previous -> runtimeState.toUiState(previous, routeCoverAssetKey) }
    }

    private suspend fun awaitPendingRelease() {
        val pendingRelease = releaseJob ?: return
        pendingRelease.join()
        if (releaseJob === pendingRelease) releaseJob = null
    }

    private suspend fun releaseActiveDemand() {
        val demand = detachActiveDemand() ?: return
        demand.release()
    }

    private fun detachActiveDemand(): StoryDetailRuntimeActivation.Available? {
        val demand = activeDemand ?: return null
        activeDemand = null
        activeRef = null
        routeCoverAssetKey = null
        mutableState.value = null
        return demand
    }

    private suspend fun rejectDestination(onDestinationRejected: () -> Unit) {
        try {
            releaseActiveDemand()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _: Throwable) {
            // Rejection must remain fail-closed even when bounded release cleanup also fails.
        } finally {
            onDestinationRejected()
        }
    }

    companion object {
        fun factory(createRuntime: () -> StoryDetailRuntime): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(StoryDetailViewModel::class.java))
                    return StoryDetailViewModel(createRuntime()) as T
                }
            }
    }
}

private fun StoryDetailSessionState.toUiState(
    previous: StoryDetailUiState?,
    routeCoverAssetKey: CoverAssetKey?,
): StoryDetailUiState {
    val currentProjection = projection
    val summary = currentProjection?.toSummaryUi() ?: previous?.summary
    val detail = currentProjection?.detail?.let { richDetail ->
        StoryDetailUi(
            description = richDetail.description,
            authors = richDetail.authors,
            artists = richDetail.artists,
            genres = richDetail.genres,
            publicationStatus = richDetail.publicationStatus,
            language = richDetail.language,
        )
    } ?: previous?.detail
    val issue = (acquisition as? CatalogAcquisitionStatus.Failed)?.failure?.toCatalogIssueUi()
    return StoryDetailUiState(
        ref = currentProjection?.ref ?: requireNotNull(previous).ref,
        summary = summary,
        detail = detail,
        detailLoading = currentProjection?.detail == null && issue == null,
        issue = issue,
        destinationActive = true,
        coverLocator = currentProjection?.summary?.coverLocator ?: previous?.coverLocator,
        coverAssetKey = if (currentProjection != null) {
            currentProjection.summary.coverAssetKey ?: previous?.coverAssetKey ?: routeCoverAssetKey
        } else {
            previous?.coverAssetKey ?: routeCoverAssetKey
        },
    )
}

private fun StoryDetailProjection.toSummaryUi(): StorySummaryUi =
    StorySummaryUi(
        title = summary.title,
        coverAssetKey = summary.coverAssetKey,
        ratingLabel = summary.rating?.let { rating ->
            String.format(Locale.ROOT, "%.1f / %.0f", rating.value, rating.scale)
        },
        publicationStatus = summary.publicationStatusSummary,
        coverLocator = summary.coverLocator,
    )
