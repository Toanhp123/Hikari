package app.openstory.catalog.feature.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.model.CatalogSectionCaps
import app.openstory.catalog.domain.model.CatalogSectionKind
import app.openstory.catalog.domain.read.DiscoverCard
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.feature.state.CatalogIssueUi
import app.openstory.catalog.feature.state.CatalogIssueKind
import app.openstory.catalog.feature.state.toCatalogIssueUi
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionStatus
import app.openstory.catalog.runtime.discover.DiscoverSessionState
import java.util.Locale
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal interface DiscoverRuntime : AutoCloseable {
    suspend fun activate(): DiscoverRuntimeActivation
}

internal sealed interface DiscoverRuntimeActivation {
    data class Unavailable(val failure: CatalogFailure) : DiscoverRuntimeActivation

    data class Available(
        val observe: (CatalogMediaType) -> Flow<DiscoverSessionState>,
        val refresh: suspend (CatalogMediaType) -> CatalogAcquisitionResult,
        val quiesce: suspend (CatalogMediaType) -> Unit = {},
    ) : DiscoverRuntimeActivation
}

internal class DiscoverViewModel(
    private val runtime: DiscoverRuntime,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = mutableState.asStateFlow()

    private var available: DiscoverRuntimeActivation.Available? = null
    private var activationIssue: CatalogIssueUi? = null
    private var activationJob: Job? = null
    private var observationJob: Job? = null
    private var refreshJob: Job? = null
    private var quiesceJob: Job? = null
    private var quiescent = false

    init {
        activateIfNeeded()
    }

    fun refresh() {
        requestRefresh()
    }

    fun retry() {
        val issue = mutableState.value.content.issueOrNull()
        val activation = available
        when {
            issue == null || !issue.retryable -> Unit
            activation == null -> activateIfNeeded()
            issue.kind == CatalogIssueKind.STORAGE_FAILED -> {
                observeSelectedMedia(activation, mutableState.value.selectedMediaType)
            }
            else -> requestRefresh()
        }
    }

    private fun activateIfNeeded() {
        if (activationJob?.isActive == true || available != null || quiescent) return
        activationJob = viewModelScope.launch {
            try {
                when (val activation = runtime.activate()) {
                    is DiscoverRuntimeActivation.Available -> {
                        available = activation
                        activationIssue = null
                        if (!quiescent) observeSelectedMedia(activation, mutableState.value.selectedMediaType)
                    }
                    is DiscoverRuntimeActivation.Unavailable -> showFailure(activation.failure)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: CatalogFailureException) {
                showFailure(failure.failure)
            } catch (@Suppress("TooGenericExceptionCaught") error: Throwable) {
                showActivationIssue(error.toCatalogIssueUi())
            } finally {
                activationJob = null
            }
        }
    }

    fun selectMedia(mediaType: CatalogMediaType) {
        val current = mutableState.value
        if (current.selectedMediaType == mediaType) return
        mutableState.value = current.copy(
            selectedMediaType = mediaType,
            content = activationIssue?.let(DiscoverContentState::NoContentFailure)
                ?: DiscoverContentState.NoContentLoading,
        )
        available?.takeUnless { quiescent }?.let { activation -> observeSelectedMedia(activation, mediaType) }
    }

    private fun requestRefresh() {
        val activation = available ?: return
        if (refreshJob?.isActive == true) return
        val mediaType = mutableState.value.selectedMediaType
        mutableState.update { current -> current.copy(content = current.content.withRefreshRunning()) }
        refreshJob = viewModelScope.launch {
            try {
                when (val result = activation.refresh(mediaType)) {
                    CatalogAcquisitionResult.Success -> Unit
                    is CatalogAcquisitionResult.Failed -> {
                        if (mutableState.value.selectedMediaType == mediaType) {
                            mutableState.update { current ->
                                current.copy(content = current.content.withIssue(result.failure.toCatalogIssueUi()))
                            }
                        }
                    }
                }
            } finally {
                refreshJob = null
            }
        }
    }

    fun quiesce() {
        if (quiescent) return
        quiescent = true
        activationJob?.cancel()
        activationJob = null
        observationJob?.cancel()
        observationJob = null
        refreshJob?.cancel()
        refreshJob = null
        val activation = available ?: return
        val mediaType = mutableState.value.selectedMediaType
        quiesceJob = viewModelScope.launch { activation.quiesce(mediaType) }
    }

    fun resume() {
        if (!quiescent) return
        quiescent = false
        val activation = available
        if (activation != null) {
            observeSelectedMedia(activation, mutableState.value.selectedMediaType)
        } else if (activationIssue?.retryable != false) {
            activateIfNeeded()
        }
    }

    override fun onCleared() {
        runtime.close()
    }

    private fun observeSelectedMedia(
        activation: DiscoverRuntimeActivation.Available,
        mediaType: CatalogMediaType,
    ) {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            val pendingQuiesce = quiesceJob
            pendingQuiesce?.join()
            if (quiesceJob === pendingQuiesce) quiesceJob = null
            if (quiescent || mutableState.value.selectedMediaType != mediaType) return@launch
            activation.observe(mediaType).collect { runtimeState ->
                if (mutableState.value.selectedMediaType != mediaType) return@collect
                mutableState.update { current ->
                    current.copy(content = runtimeState.toContentState(current.content, mediaType))
                }
            }
        }
    }

    private fun showFailure(failure: CatalogFailure) {
        showActivationIssue(failure.toCatalogIssueUi())
    }

    private fun showActivationIssue(issue: CatalogIssueUi) {
        activationIssue = issue
        mutableState.update { current ->
            current.copy(content = current.content.withIssue(issue))
        }
    }

    companion object {
        fun factory(createRuntime: () -> DiscoverRuntime): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(DiscoverViewModel::class.java))
                    return DiscoverViewModel(createRuntime()) as T
                }
            }
    }
}

private fun DiscoverSessionState.toContentState(
    previous: DiscoverContentState,
    selectedMediaType: CatalogMediaType,
): DiscoverContentState {
    val failure = (acquisition as? CatalogAcquisitionStatus.Failed)?.failure?.toCatalogIssueUi()
    return when (val snapshot = persistence) {
        DiscoverPersistenceState.Absent -> if (failure == null) {
            DiscoverContentState.NoContentLoading
        } else {
            DiscoverContentState.NoContentFailure(failure)
        }
        is DiscoverPersistenceState.Published -> {
            val sections = snapshot.cards.toUiSections(selectedMediaType)
            if (sections.isEmpty()) {
                DiscoverContentState.Empty(
                    refreshing = acquisition == CatalogAcquisitionStatus.Running,
                    issue = failure,
                )
            } else {
                DiscoverContentState.Content(
                    sections = sections,
                    refreshing = acquisition == CatalogAcquisitionStatus.Running,
                    issue = failure,
                )
            }
        }
        null -> if (failure == null) previous else previous.withIssue(failure)
    }
}

private fun List<DiscoverCard>.toUiSections(mediaType: CatalogMediaType): List<DiscoverSectionUi> =
    CatalogSectionKind.entries.mapNotNull { kind ->
        val cards = asSequence()
            .filter { card -> card.contentType == mediaType && card.sectionKind == kind }
            .sortedBy(DiscoverCard::itemPosition)
            .take(CatalogSectionCaps.cap(kind))
            .map(DiscoverCard::toUi)
            .toList()
        cards.takeIf(List<DiscoverCardUi>::isNotEmpty)?.let { DiscoverSectionUi(kind, it) }
    }

private fun DiscoverCard.toUi() = DiscoverCardUi(
    ref = ref,
    title = title,
    coverAssetKey = coverAssetKey,
    ratingLabel = rating?.let { value ->
        String.format(Locale.ROOT, "%.1f / %.0f", value.value, value.scale)
    },
    supportingLabel = publicationStatusSummary ?: latestUpdateEpochMs?.let { "Recently updated" },
    coverLocator = coverLocator,
)

private fun DiscoverContentState.withRefreshRunning(): DiscoverContentState = when (this) {
    DiscoverContentState.NoContentLoading -> this
    is DiscoverContentState.NoContentFailure -> DiscoverContentState.NoContentLoading
    is DiscoverContentState.Empty -> copy(refreshing = true, issue = null)
    is DiscoverContentState.Content -> copy(refreshing = true, issue = null)
}

private fun DiscoverContentState.withIssue(issue: CatalogIssueUi): DiscoverContentState = when (this) {
    DiscoverContentState.NoContentLoading,
    is DiscoverContentState.NoContentFailure,
    -> DiscoverContentState.NoContentFailure(issue)
    is DiscoverContentState.Empty -> copy(refreshing = false, issue = issue)
    is DiscoverContentState.Content -> copy(refreshing = false, issue = issue)
}

private fun DiscoverContentState.issueOrNull(): CatalogIssueUi? = when (this) {
    DiscoverContentState.NoContentLoading -> null
    is DiscoverContentState.NoContentFailure -> issue
    is DiscoverContentState.Empty -> issue
    is DiscoverContentState.Content -> issue
}
