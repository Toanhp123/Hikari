package app.openstory.catalog.ui.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.details.CatalogFullMetadataFallbackService
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataAccess
import app.openstory.catalog.metadata.CatalogMetadataCoordinator
import app.openstory.catalog.metadata.CatalogMetadataFailure
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataResult
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.RefreshState
import app.openstory.catalog.ui.state.completeFailure
import app.openstory.catalog.ui.state.completeSuccess
import app.openstory.catalog.ui.state.forExpectedKey
import app.openstory.catalog.ui.state.hasIssueOrUnavailable
import app.openstory.catalog.ui.state.retainedObservation
import app.openstory.catalog.ui.state.startAttempt
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import app.openstory.reader.progress.ReadingProgressRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = StoryViewModel.Factory::class)
class StoryViewModel internal constructor(
    private val assistedArgs: StoryAssistedArgs,
    private val canonical: CanonicalCatalogRepository,
    private val bootstrap: CanonicalBootstrapUseCase,
    private val fullMetadata: CatalogFullMetadataFallbackService,
    private val metadata: CatalogMetadataAccess,
    private val orchestrator: CanonicalEngineEventSink,
    private val library: LibraryService,
    private val progress: ReadingProgressRepository,
    private val reconciliation: StoryReconciliationController,
) : ViewModel() {
    @AssistedInject
    internal constructor(
        @Assisted assistedArgs: StoryAssistedArgs,
        canonical: CanonicalCatalogRepository,
        bootstrap: CanonicalBootstrapUseCase,
        fullMetadata: CatalogFullMetadataFallbackService,
        metadata: CatalogMetadataCoordinator,
        orchestrator: CanonicalEngineEventSink,
        library: LibraryService,
        progress: ReadingProgressRepository,
        reconciliation: StoryReconciliationController,
    ) : this(
        assistedArgs,
        canonical,
        bootstrap,
        fullMetadata,
        metadata as CatalogMetadataAccess,
        orchestrator,
        library,
        progress,
        reconciliation,
    )

    private val routeStoryId = assistedArgs.storyId
    private val started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
    private val selectedSource = MutableStateFlow<SourceKey?>(null)
    private val refreshState = MutableStateFlow(RefreshState())
    private val commandFailure = MutableStateFlow<CatalogUiFailure?>(null)
    private val selectedSection = MutableStateFlow(StorySection.OVERVIEW)
    private var refreshJob: Job? = null
    private val canonicalReadiness = StoryCanonicalReadinessController(
        scope = viewModelScope,
        routeStoryId = routeStoryId,
        canonical = canonical,
        bootstrap = bootstrap,
        fullMetadata = fullMetadata,
        started = started,
    )
    private val resolvedStoryId = canonicalReadiness.resolvedStoryId

    private val libraryObservation = viewModelScope.retainedObservation(
        key = resolvedStoryId,
        initialKey = routeStoryId,
        started = started,
        observe = { storyId ->
            library.observe().map { entries -> entries.firstOrNull { it.storyId == storyId }?.status }
        },
        mapFailure = { _, _ -> CatalogUiFailure(LIBRARY_OBSERVE_EXCEPTION_CODE, retryable = true) },
    )

    private val progressObservation = viewModelScope.retainedObservation(
        key = resolvedStoryId,
        initialKey = routeStoryId,
        started = started,
        observe = { storyId ->
            progress.observeAll().map { records -> records.latestResumeTarget(storyId) }
        },
        mapFailure = { _, _ -> CatalogUiFailure(PROGRESS_OBSERVE_EXCEPTION_CODE, retryable = true) },
    )

    private val observations = combine(
        canonicalReadiness.state,
        libraryObservation.state,
        progressObservation.state,
    ) { canonicalState, libraryState, progressState ->
        StoryObservations(canonicalState, libraryState, progressState)
    }

    private val controls = combine(
        selectedSource,
        refreshState,
        commandFailure,
        selectedSection,
    ) { source, refresh, commandIssue, section ->
        StoryControls(source, refresh, commandIssue, section)
    }

    private val reconciliationUi = reconciliation.observe(resolvedStoryId)

    val state = combine(
        observations,
        controls,
        reconciliationUi,
    ) { currentObservations, currentControls, review ->
        reduceStoryState(currentObservations, currentControls, review)
    }.stateIn(
        scope = viewModelScope,
        started = started,
        initialValue = StoryUiState(routeStoryId),
    )

    fun selectInspectionSource(sourceKey: SourceKey?) {
        selectedSource.value = sourceKey
    }

    fun selectSource(pluginId: PluginId, sourceId: String) {
        val sourceKey = SourceKey(pluginId, sourceId)
        selectInspectionSource(sourceKey.takeUnless { it == selectedSource.value })
    }

    fun pinPrimary(sourceKey: SourceKey) {
        viewModelScope.launch { updatePreference(CanonicalSourcePreferenceMode.PINNED, sourceKey) }
    }

    fun pinPrimary(pluginId: PluginId, sourceId: String) {
        pinPrimary(SourceKey(pluginId, sourceId))
    }

    fun useAutomaticPrimary() {
        viewModelScope.launch { updatePreference(CanonicalSourcePreferenceMode.AUTO, null) }
    }

    private suspend fun updatePreference(mode: CanonicalSourcePreferenceMode, pinned: SourceKey?) {
        commandFailure.value = null
        try {
            val current = canonical.state(routeStoryId) ?: return
            canonical.setSourcePreference(
                current.preference.copy(storyId = routeStoryId, mode = mode, pinnedSource = pinned),
            )
            when (val result = orchestrator.onSourcePreferenceChanged(routeStoryId)) {
                is CanonicalFusionResult.Failed -> {
                    commandFailure.value = CatalogUiFailure(result.code, result.retryable)
                }
                else -> commandFailure.value = null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            commandFailure.value = CatalogUiFailure(PREFERENCE_EXCEPTION_CODE, retryable = true)
        }
    }

    fun selectSection(section: StorySection) {
        selectedSection.value = section
        if (section != StorySection.SOURCES) {
            selectedSource.value = null
        }
    }

    fun changeLibraryStatus(status: LibraryStatus?) {
        val snapshot = state.value
        if (!snapshot.libraryStatusResolved) return
        viewModelScope.launch {
            when {
                status == null -> library.remove(snapshot.storyId)
                snapshot.libraryStatus == null -> library.add(snapshot.storyId, status)
                snapshot.libraryStatus != status -> library.changeStatus(snapshot.storyId, status)
            }
        }
    }

    fun mergeReconciliationPrompt(onProtectedConflict: (String) -> Unit) {
        reconciliation.merge(state.value.reconciliationPrompt, viewModelScope, onProtectedConflict)
    }

    fun keepReconciliationSeparate() {
        reconciliation.keepSeparate(state.value.reconciliationPrompt, viewModelScope)
    }

    fun deferReconciliationPrompt() {
        reconciliation.defer(state.value.reconciliationPrompt, viewModelScope)
    }

    fun retryContent() {
        canonicalReadiness.retryContent()
    }

    fun retryObservation() {
        val snapshot = state.value
        if (snapshot.content !is ContentState.Ready) return
        val expectedResolvedId = snapshot.storyId
        val libraryState = libraryObservation.state.value.forExpectedKey(expectedResolvedId)
        val progressState = progressObservation.state.value.forExpectedKey(expectedResolvedId)
        when {
            canonicalReadiness.retryObservation() -> Unit
            libraryState.hasIssueOrUnavailable() -> libraryObservation.retry()
            progressState.hasIssueOrUnavailable() -> progressObservation.retry()
        }
    }

    fun refresh(requestedSource: SourceKey? = null) {
        if (state.value.content !is ContentState.Ready) return
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshState.update(RefreshState::startAttempt)
            try {
                val currentState = canonical.state(routeStoryId)
                val targetSource = requestedSource
                    ?: (currentState as? CanonicalStoryState.Ready)?.generation?.effectivePrimary
                val source = currentState?.sources.orEmpty().firstOrNull { it.sourceKey == targetSource }
                if (currentState == null || source == null) {
                    refreshState.update {
                        it.completeFailure(CatalogUiFailure(SOURCE_UNAVAILABLE_CODE, retryable = false))
                    }
                } else {
                    val failure = metadata.refresh(
                        CatalogMetadataKey(source.sourceKey.pluginId, source.sourceKey.sourceId),
                        CatalogMetadataLevel.Full,
                    ).failureOrNull()
                    refreshState.update { current ->
                        if (failure == null) current.completeSuccess() else current.completeFailure(failure)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                refreshState.update {
                    it.completeFailure(CatalogUiFailure(REFRESH_EXCEPTION_CODE, retryable = true))
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(assistedArgs: StoryAssistedArgs): StoryViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SOURCE_UNAVAILABLE_CODE = "catalog.story.source_unavailable"
        const val LIBRARY_OBSERVE_EXCEPTION_CODE = "catalog.story.library.observe_exception"
        const val PROGRESS_OBSERVE_EXCEPTION_CODE = "catalog.story.progress.observe_exception"
        const val REFRESH_EXCEPTION_CODE = "catalog.story.refresh_exception"
        const val PREFERENCE_EXCEPTION_CODE = "catalog.story.preference_exception"
    }
}

data class StoryAssistedArgs(val storyId: StoryId)

private fun CatalogMetadataResult.failureOrNull(): CatalogUiFailure? = when (this) {
    is CatalogMetadataResult.Ready -> null
    is CatalogMetadataResult.Failure -> failure.toUiFailure()
    CatalogMetadataResult.Missing -> CatalogUiFailure("catalog.story.source_unavailable", false)
}

private fun CatalogMetadataFailure.toUiFailure(): CatalogUiFailure = when (this) {
    is CatalogMetadataFailure.SourceUnavailable -> CatalogUiFailure("catalog.source_unavailable", false)
    is CatalogMetadataFailure.SourceFailure -> CatalogUiFailure(code, retryable)
    is CatalogMetadataFailure.SourceIdMismatch -> CatalogUiFailure("catalog.details_source_mismatch", false)
    is CatalogMetadataFailure.StoreFailure -> CatalogUiFailure(code, retryable)
}
