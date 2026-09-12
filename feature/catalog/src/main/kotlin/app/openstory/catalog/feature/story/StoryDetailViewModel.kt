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
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

internal interface StoryDetailRuntime {
    suspend fun activate(ref: StorySourceRef): StoryDetailRuntimeActivation
}

internal sealed interface StoryDetailRuntimeActivation {
    data class Unavailable(val failure: CatalogFailure) : StoryDetailRuntimeActivation

    data class Available(
        val states: Flow<StoryDetailSessionState>,
        val retry: suspend () -> CatalogAcquisitionResult,
        val quiesce: suspend () -> Unit = {},
        val release: suspend () -> Unit,
    ) : StoryDetailRuntimeActivation
}

internal class CatalogStoryDetailRuntime(
    private val activateCatalog: suspend () -> CatalogCapabilityActivation,
    private val onCollectorStarted: () -> Unit = {},
    private val onCollectorStopped: () -> Unit = {},
) : StoryDetailRuntime {
    override suspend fun activate(ref: StorySourceRef): StoryDetailRuntimeActivation =
        when (val activation = activateCatalog()) {
            is CatalogCapabilityActivation.Unavailable ->
                StoryDetailRuntimeActivation.Unavailable(activation.failure)
            is CatalogCapabilityActivation.Available -> {
                val session = activation.storyDetailSession(ref)
                StoryDetailRuntimeActivation.Available(
                    states = session.activate()
                        .onStart { onCollectorStarted() }
                        .onCompletion { onCollectorStopped() },
                    retry = session::retry,
                    quiesce = session::quiesce,
                    release = session::release,
                )
            }
        }
}

internal class StoryDetailViewModel(
    private val runtime: StoryDetailRuntime,
    private val onUiPublished: () -> Unit = {},
) : ViewModel() {
    private val mutableState = MutableStateFlow<StoryDetailUiState?>(null)
    val state: StateFlow<StoryDetailUiState?> = mutableState.asStateFlow()

    private var openJob: Job? = null
    private var releaseJob: Job? = null
    private var quiesceJob: Job? = null
    private var activeRef: StorySourceRef? = null
    private var activeDemand: StoryDetailRuntimeActivation.Available? = null
    private var routeCoverAssetKey: CoverAssetKey? = null
    private var quiescent = false

    fun open(
        ref: StorySourceRef,
        coverAssetKey: CoverAssetKey?,
        onDestinationRejected: () -> Unit = {},
        onDestinationReady: () -> Unit,
    ) {
        quiescent = false
        if (activeRef == ref && activeDemand != null) {
            onDestinationReady()
            return
        }
        if (openJob?.isActive == true) return
        openJob = viewModelScope.launch {
            awaitPendingRelease()
            awaitPendingQuiesce()
            releaseActiveDemand(clearRoute = false)
            try {
                when (val activation = runtime.activate(ref)) {
                    is StoryDetailRuntimeActivation.Unavailable -> rejectDestination(onDestinationRejected)
                    is StoryDetailRuntimeActivation.Available -> {
                        activeRef = ref
                        activeDemand = activation
                        routeCoverAssetKey = coverAssetKey
                        if (mutableState.value?.ref != ref) {
                            mutableState.value = StoryDetailUiState(
                                ref = ref,
                                summary = null,
                                detail = null,
                                detailLoading = true,
                                issue = null,
                                destinationActive = true,
                                artwork = StoryArtworkUi(assetKey = coverAssetKey, locator = null),
                            )
                        }
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
        val demand = detachActiveDemand()
        if (demand == null && quiesceJob == null) return
        releaseJob = viewModelScope.launch {
            try {
                awaitPendingQuiesce()
                demand?.release()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _: Throwable) {
                // The destination is already closed; bounded retention cleanup can retry on a later mutation.
            }
        }
    }

    fun quiesce() {
        if (quiescent) return
        quiescent = true
        openJob?.cancel()
        openJob = null
        val demand = activeDemand ?: return
        activeDemand = null
        quiesceJob = viewModelScope.launch {
            demand.quiesce()
        }
    }

    private fun reduce(runtimeState: StoryDetailSessionState) {
        mutableState.update { previous -> runtimeState.toUiState(previous, routeCoverAssetKey) }
        if (runtimeState.projection != null) onUiPublished()
    }

    private suspend fun awaitPendingRelease() {
        val pendingRelease = releaseJob ?: return
        pendingRelease.join()
        if (releaseJob === pendingRelease) releaseJob = null
    }

    private suspend fun awaitPendingQuiesce() {
        val pendingQuiesce = quiesceJob ?: return
        pendingQuiesce.join()
        if (quiesceJob === pendingQuiesce) quiesceJob = null
    }

    private suspend fun releaseActiveDemand(clearRoute: Boolean = true) {
        val demand = detachActiveDemand(clearRoute) ?: return
        awaitPendingQuiesce()
        demand.release()
    }

    private fun detachActiveDemand(clearRoute: Boolean = true): StoryDetailRuntimeActivation.Available? {
        val demand = activeDemand
        activeDemand = null
        if (clearRoute) clearRouteState()
        return demand
    }

    private fun clearRouteState() {
        activeRef = null
        routeCoverAssetKey = null
        mutableState.value = null
    }

    private suspend fun rejectDestination(onDestinationRejected: () -> Unit) {
        try {
            releaseActiveDemand()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (@Suppress("TooGenericExceptionCaught", "SwallowedException") _: Throwable) {
            // Rejection must remain fail-closed even when bounded release cleanup also fails.
        } finally {
            clearRouteState()
            onDestinationRejected()
        }
    }

    companion object {
        fun factory(
            createRuntime: () -> StoryDetailRuntime,
            onUiPublished: () -> Unit = {},
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(StoryDetailViewModel::class.java))
                    return StoryDetailViewModel(createRuntime(), onUiPublished) as T
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
    val artwork = currentProjection?.summary?.let { currentSummary ->
        currentSummary.coverAssetKey?.let { assetKey ->
            StoryArtworkUi(assetKey = assetKey, locator = currentSummary.coverLocator)
        }
    } ?: previous?.artwork ?: StoryArtworkUi(assetKey = routeCoverAssetKey, locator = null)
    return StoryDetailUiState(
        ref = currentProjection?.ref ?: requireNotNull(previous).ref,
        summary = summary,
        detail = detail,
        detailLoading = currentProjection?.detail == null && issue == null,
        issue = issue,
        destinationActive = true,
        artwork = artwork,
    )
}

private fun StoryDetailProjection.toSummaryUi(): StorySummaryUi =
    StorySummaryUi(
        title = summary.title,
        contentType = summary.contentType,
        ratingLabel = summary.rating?.let { rating ->
            String.format(Locale.ROOT, "%.1f / %.0f", rating.value, rating.scale)
        },
        publicationStatus = summary.publicationStatusSummary,
        latestUpdateLabel = summary.latestUpdateEpochMs?.let(::formatLatestUpdate),
    )

private val STORY_UPDATE_DATE_FORMATTER = DateTimeFormatter
    .ofPattern("MMM d, uuuu", Locale.ENGLISH)
    .withZone(ZoneOffset.UTC)

private fun formatLatestUpdate(epochMs: Long): String =
    "Updated ${STORY_UPDATE_DATE_FORMATTER.format(Instant.ofEpochMilli(epochMs))}"
