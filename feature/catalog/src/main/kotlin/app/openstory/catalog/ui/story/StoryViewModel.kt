package app.openstory.catalog.ui.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.canonical.CanonicalBootstrapUseCase
import app.openstory.catalog.canonical.CanonicalCatalogRepository
import app.openstory.catalog.canonical.CanonicalSourcePreferenceMode
import app.openstory.catalog.canonical.CanonicalStoryState
import app.openstory.catalog.fusion.CanonicalFusionResult
import app.openstory.catalog.identity.SourceKey
import app.openstory.catalog.metadata.CatalogMetadataCoordinator
import app.openstory.catalog.metadata.CatalogMetadataFailure
import app.openstory.catalog.metadata.CatalogMetadataKey
import app.openstory.catalog.metadata.CatalogMetadataLevel
import app.openstory.catalog.metadata.CatalogMetadataResult
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.Score
import app.openstory.catalog.orchestration.CanonicalEngineEventSink
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = StoryViewModel.Factory::class)
class StoryViewModel @AssistedInject internal constructor(
    @Assisted private val assistedArgs: StoryAssistedArgs,
    private val canonical: CanonicalCatalogRepository,
    private val bootstrap: CanonicalBootstrapUseCase,
    private val metadata: CatalogMetadataCoordinator,
    private val orchestrator: CanonicalEngineEventSink,
    private val library: LibraryService,
    private val progress: ReadingProgressRepository,
    private val reconciliation: StoryReconciliationController,
) : ViewModel() {
    private val selectedSource = MutableStateFlow<SourceKey?>(null)
    private val refreshing = MutableStateFlow(false)
    private val failure = MutableStateFlow<StoryRefreshFailure?>(null)
    private val selectedSection = MutableStateFlow(StorySection.OVERVIEW)
    private val personal = combine(
        library.observe().preserveLatest(emptyList()),
        progress.observeAll().preserveLatest(emptyList()),
    ) { entries, records -> StoryPersonalState(entries, records) }

    private val catalogState = combine(
        canonical.observeStory(assistedArgs.storyId).catch {
            failure.value = StoryRefreshFailure(OBSERVE_EXCEPTION_CODE, retryable = true)
            emit(null)
        },
        selectedSource,
        refreshing,
        failure,
    ) { state, selected, busy, currentFailure ->
        val rawSources = state?.sources.orEmpty().map { it.entry }.sortedWith(sourceOrder)
        val inspection = selected?.takeIf { key -> rawSources.any { it.matches(key) } }
        StoryCatalogState(state, rawSources, inspection, busy, currentFailure)
    }

    private val resolvedStoryId = catalogState
        .map { catalog -> catalog.canonical?.story?.id ?: assistedArgs.storyId }
        .distinctUntilChanged()

    private val reconciliationUi = reconciliation.observe(resolvedStoryId)

    val state = combine(
        catalogState,
        personal,
        selectedSection,
        reconciliationUi,
    ) { catalog, personal, section, review ->
        val model = (catalog.canonical as? CanonicalStoryState.Ready)?.toStoryUiModel(catalog.sources)
        val resolvedId = catalog.canonical?.story?.id ?: assistedArgs.storyId
        StoryUiState(
            storyId = resolvedId,
            story = model,
            selectedSource = catalog.inspection?.toIdentity(),
            refreshing = catalog.refreshing,
            failure = catalog.failure,
            libraryStatus = personal.entries.firstOrNull { it.storyId == resolvedId }?.status,
            resumeTarget = personal.records.latestResumeTarget(resolvedId),
            selectedSection = section,
            reconciliationPrompt = review.prompt,
            reconciliationResolving = review.resolving,
            reconciliationFailureMessage = review.failureMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StoryUiState(assistedArgs.storyId),
    )

    init {
        viewModelScope.launch {
            try {
                bootstrap.ensureReady(assistedArgs.storyId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failure.value = StoryRefreshFailure(BOOTSTRAP_FAILURE_CODE, retryable = true)
            }
        }
    }

    fun selectInspectionSource(sourceKey: SourceKey?) {
        selectedSource.value = sourceKey
        failure.value = null
    }

    fun selectSource(pluginId: PluginId, sourceId: String) {
        selectInspectionSource(SourceKey(pluginId, sourceId))
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
        val current = canonical.state(assistedArgs.storyId) ?: return
        canonical.setSourcePreference(
            current.preference.copy(mode = mode, pinnedSource = pinned),
        )
        when (val result = orchestrator.onSourcePreferenceChanged(current.story.id)) {
            is CanonicalFusionResult.Failed -> failure.value = StoryRefreshFailure(result.code, result.retryable)
            else -> failure.value = null
        }
    }

    fun selectSection(section: StorySection) {
        selectedSection.value = section
    }

    fun changeLibraryStatus(status: LibraryStatus?) {
        viewModelScope.launch {
            val resolvedId = canonical.state(assistedArgs.storyId)?.story?.id ?: assistedArgs.storyId
            val current = state.value.libraryStatus
            when {
                status == null -> library.remove(resolvedId)
                current == null -> library.add(resolvedId, status)
                current != status -> library.changeStatus(resolvedId, status)
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

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                val currentState = canonical.state(assistedArgs.storyId)
                val source = currentState?.sources.orEmpty().firstOrNull { it.sourceKey == selectedSource.value }
                    ?: (currentState as? CanonicalStoryState.Ready)?.let { ready ->
                        ready.sources.firstOrNull { it.sourceKey == ready.generation.effectivePrimary }
                    }
                failure.value = if (currentState == null || source == null) {
                    StoryRefreshFailure(SOURCE_UNAVAILABLE_CODE, retryable = false)
                } else {
                    val refreshFailure = metadata.refresh(
                        CatalogMetadataKey(source.sourceKey.pluginId, source.sourceKey.sourceId),
                        CatalogMetadataLevel.Full,
                    ).failureOrNull()
                    refreshFailure
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failure.value = StoryRefreshFailure(REFRESH_EXCEPTION_CODE, retryable = true)
            } finally {
                refreshing.value = false
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(assistedArgs: StoryAssistedArgs): StoryViewModel
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.preserveLatest(initial: T) = flow {
        var latest = initial
        emit(initial)
        try {
            collect { value -> latest = value; emit(value) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emit(latest)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SOURCE_UNAVAILABLE_CODE = "catalog.story.source_unavailable"
        const val OBSERVE_EXCEPTION_CODE = "catalog.story.observe_exception"
        const val REFRESH_EXCEPTION_CODE = "catalog.story.refresh_exception"
        const val BOOTSTRAP_FAILURE_CODE = "catalog.story.canonical_bootstrap_failed"
        val sourceOrder = compareBy<CatalogEntry> { it.pluginId.value }.thenBy { it.sourceId }
    }
}

private data class StoryPersonalState(
    val entries: List<LibraryEntry>,
    val records: List<ReadingProgress>,
)

private data class StoryCatalogState(
    val canonical: CanonicalStoryState?,
    val sources: List<CatalogEntry>,
    val inspection: SourceKey?,
    val refreshing: Boolean,
    val failure: StoryRefreshFailure?,
)

data class StoryAssistedArgs(val storyId: StoryId)

private fun CatalogEntry.matches(key: SourceKey): Boolean = pluginId == key.pluginId && sourceId == key.sourceId
private fun SourceKey.toIdentity() = StorySourceIdentity(pluginId, sourceId)

internal fun CanonicalStoryState.Ready.toStoryUiModel(rawSources: List<CatalogEntry>): StoryUiModel {
    val canonicalScore = generation.metadata.score
    return StoryUiModel(
        storyId = story.id,
        preferredTitle = generation.metadata.title,
        contentType = story.contentType,
        aliases = generation.metadata.aliases.toSet(),
        description = generation.metadata.description,
        coverUrl = generation.metadata.coverUrl,
        score = canonicalScore?.let { Score(it.normalizedValue * PRESENTATION_SCORE_SCALE, PRESENTATION_SCORE_SCALE) },
        authors = generation.metadata.authors.toSet(),
        genres = generation.metadata.genres.toSet(),
        languageTags = generation.metadata.languageTags.toSet(),
        sources = rawSources,
        effectivePrimary = generation.effectivePrimary,
        preferenceMode = preference.mode,
        pinnedSource = preference.pinnedSource,
        publicationStatus = generation.metadata.publicationStatus,
    )
}

private const val PRESENTATION_SCORE_SCALE = 10.0

private fun List<ReadingProgress>.latestResumeTarget(storyId: StoryId): ReaderTarget? =
    asSequence().filter { it.storyId == storyId && it.completedAtEpochMillis == null }
        .maxWithOrNull(compareBy<ReadingProgress> { it.updatedAtEpochMillis }.thenBy { it.releaseId.value })
        ?.let { ReaderTarget(storyId, it.canonicalChapterId, it.releaseId) }

private fun CatalogMetadataResult.failureOrNull(): StoryRefreshFailure? = when (this) {
    is CatalogMetadataResult.Ready -> null
    is CatalogMetadataResult.Failure -> failure.toUiFailure()
    CatalogMetadataResult.Missing -> StoryRefreshFailure("catalog.story.source_unavailable", false)
}

private fun CatalogMetadataFailure.toUiFailure(): StoryRefreshFailure = when (this) {
    is CatalogMetadataFailure.SourceUnavailable -> StoryRefreshFailure("catalog.source_unavailable", false)
    is CatalogMetadataFailure.SourceFailure -> StoryRefreshFailure(code, retryable)
    is CatalogMetadataFailure.SourceIdMismatch -> StoryRefreshFailure("catalog.details_source_mismatch", false)
    is CatalogMetadataFailure.StoreFailure -> StoryRefreshFailure(code, retryable)
}
