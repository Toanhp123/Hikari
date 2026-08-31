package app.openstory.catalog.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.ObservationState
import app.openstory.catalog.ui.state.forExpectedKey
import app.openstory.catalog.ui.state.hasIssueOrUnavailable
import app.openstory.catalog.ui.state.hasRetainedIssue
import app.openstory.catalog.ui.state.retainedObservation
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.DownloadRepository
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryService
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.reader.content.ReaderSourceAvailability
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class HomeDashboardViewModel @Inject constructor(
    library: LibraryService,
    catalog: CatalogStoryProjectionRepository,
    progress: ReadingProgressRepository,
    chapters: ChapterRepository,
    mappings: ContentMappingRepository,
    downloads: DownloadRepository,
    readerSources: ReaderSourceAvailability,
) : ViewModel() {
    private val projector = HomeDashboardProjector()
    private val started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)

    private val libraryObservation = viewModelScope.retainedObservation(
        key = flowOf(Unit),
        initialKey = Unit,
        started = started,
        observe = { library.observe() },
        mapFailure = { _, _ -> CatalogUiFailure(LIBRARY_FAILURE, retryable = true) },
    )

    private val libraryStoryIds: Flow<Set<StoryId>> = libraryObservation.state
        .map { state ->
            when (state) {
                is ObservationState.Available -> state.value.storyIdKey()
                is ObservationState.Pending,
                is ObservationState.Unavailable -> emptySet()
            }
        }
        .distinctUntilChanged()

    private val catalogObservation = viewModelScope.retainedObservation(
        key = libraryStoryIds,
        initialKey = emptySet(),
        started = started,
        observe = { storyIds ->
            if (storyIds.isEmpty()) flowOf(emptyList()) else catalog.observeForStories(storyIds)
        },
        mapFailure = { _, _ -> CatalogUiFailure(CATALOG_FAILURE, retryable = true) },
    )

    private val progressObservation = viewModelScope.retainedObservation(
        key = libraryStoryIds,
        initialKey = emptySet(),
        started = started,
        observe = { storyIds ->
            if (storyIds.isEmpty()) flowOf(emptyList()) else progress.observeForStories(storyIds)
        },
        mapFailure = { _, _ -> CatalogUiFailure(PROGRESS_FAILURE, retryable = true) },
    )

    private val chapterObservation = viewModelScope.retainedObservation(
        key = libraryStoryIds,
        initialKey = emptySet(),
        started = started,
        observe = { storyIds ->
            if (storyIds.isEmpty()) flowOf(emptyList()) else chapters.observeForStories(storyIds)
        },
        mapFailure = { _, _ -> CatalogUiFailure(CHAPTERS_FAILURE, retryable = true) },
    )

    private val mappingObservation = viewModelScope.retainedObservation(
        key = libraryStoryIds,
        initialKey = emptySet(),
        started = started,
        observe = { storyIds ->
            if (storyIds.isEmpty()) flowOf(emptyList()) else mappings.observeForStories(storyIds)
        },
        mapFailure = { _, _ -> CatalogUiFailure(MAPPINGS_FAILURE, retryable = true) },
    )

    private val downloadObservation = viewModelScope.retainedObservation(
        key = flowOf(Unit),
        initialKey = Unit,
        started = started,
        observe = { downloads.observeCompletedCount() },
        mapFailure = { _, _ -> CatalogUiFailure(DOWNLOADS_FAILURE, retryable = true) },
    )

    private val readerObservation = viewModelScope.retainedObservation(
        key = flowOf(Unit),
        initialKey = Unit,
        started = started,
        observe = {
            flow {
                emit(readerSources.enabledPluginIds())
            }
        },
        mapFailure = { _, _ -> CatalogUiFailure(READER_FAILURE, retryable = true) },
    )

    private val scopedObservations = combine(
        catalogObservation.state,
        progressObservation.state,
        chapterObservation.state,
        mappingObservation.state,
    ) { catalogState, progressState, chapterState, mappingState ->
        HomeScopedObservations(catalogState, progressState, chapterState, mappingState)
    }

    private val globalEnrichment = combine(
        downloadObservation.state,
        readerObservation.state,
    ) { downloadState, readerState ->
        HomeGlobalEnrichment(downloadState, readerState)
    }

    val state = combine(
        libraryObservation.state,
        scopedObservations,
        globalEnrichment,
    ) { libraryState, scoped, global ->
        reduceHomeState(
            libraryState = libraryState,
            scoped = scoped,
            global = global,
            projector = projector,
        )
    }.stateIn(
        scope = viewModelScope,
        started = started,
        initialValue = HomeDashboardUiState(),
    )

    fun retryContent() {
        if (libraryObservation.state.value is ObservationState.Unavailable) {
            libraryObservation.retry()
        }
    }

    fun retryObservation() {
        observationRetryAction()?.invoke()
    }

    private fun observationRetryAction(): (() -> Unit)? {
        val libraryState = libraryObservation.state.value
        val expectedKey = (libraryState as? ObservationState.Available)
            ?.value
            ?.storyIdKey()
        return when {
            libraryState.hasRetainedIssue() -> libraryObservation::retry
            expectedKey == null -> null
            catalogObservation.state.value
                .forExpectedKey(expectedKey)
                .hasIssueOrUnavailable() -> catalogObservation::retry
            progressObservation.state.value
                .forExpectedKey(expectedKey)
                .hasIssueOrUnavailable() -> progressObservation::retry
            chapterObservation.state.value
                .forExpectedKey(expectedKey)
                .hasIssueOrUnavailable() -> chapterObservation::retry
            mappingObservation.state.value
                .forExpectedKey(expectedKey)
                .hasIssueOrUnavailable() -> mappingObservation::retry
            downloadObservation.state.value.hasIssueOrUnavailable() -> downloadObservation::retry
            readerObservation.state.value.hasIssueOrUnavailable() -> readerObservation::retry
            else -> null
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val LIBRARY_FAILURE = "home.library.observe_exception"
        const val CATALOG_FAILURE = "home.catalog.observe_exception"
        const val PROGRESS_FAILURE = "home.progress.observe_exception"
        const val CHAPTERS_FAILURE = "home.chapters.observe_exception"
        const val MAPPINGS_FAILURE = "home.mappings.observe_exception"
        const val DOWNLOADS_FAILURE = "home.downloads.observe_exception"
        const val READER_FAILURE = "home.reader.observe_exception"
    }
}

private data class HomeScopedObservations(
    val catalog: ObservationState<Set<StoryId>, List<CatalogStoryProjection>>,
    val progress: ObservationState<Set<StoryId>, List<ReadingProgress>>,
    val chapters: ObservationState<Set<StoryId>, List<CanonicalChapterGroup>>,
    val mappings: ObservationState<Set<StoryId>, List<ContentMapping>>,
)

private data class HomeGlobalEnrichment(
    val downloads: ObservationState<Unit, Int>,
    val reader: ObservationState<Unit, Set<PluginId>>,
)

private fun reduceHomeState(
    libraryState: ObservationState<Unit, List<LibraryEntry>>,
    scoped: HomeScopedObservations,
    global: HomeGlobalEnrichment,
    projector: HomeDashboardProjector,
): HomeDashboardUiState = when (libraryState) {
    is ObservationState.Pending -> HomeDashboardUiState()
    is ObservationState.Unavailable -> HomeDashboardUiState(
        content = ContentState.Failed(libraryState.failure),
    )
    is ObservationState.Available -> {
        val entries = libraryState.value
        val expectedKey = entries.storyIdKey()
        val currentCatalog = scoped.catalog.forExpectedKey(expectedKey)
        val currentProgress = scoped.progress.forExpectedKey(expectedKey)
        val currentChapters = scoped.chapters.forExpectedKey(expectedKey)
        val currentMappings = scoped.mappings.forExpectedKey(expectedKey)
        HomeDashboardUiState(
            content = ContentState.Ready(
                projector.project(
                    HomeDashboardInput(
                        library = entries,
                        catalog = currentCatalog.availableValueOrNull(),
                        progress = currentProgress.availableValueOrNull(),
                        chapters = currentChapters.availableValueOrNull(),
                        mappings = currentMappings.availableValueOrNull(),
                        readerPluginIds = global.reader.availableValueOrNull(),
                        downloadedCount = global.downloads.availableValueOrNull(),
                    ),
                ),
            ),
            observationIssue = libraryState.issue
                ?: currentCatalog.issueOrUnavailable()
                ?: currentProgress.issueOrUnavailable()
                ?: currentChapters.issueOrUnavailable()
                ?: currentMappings.issueOrUnavailable()
                ?: global.downloads.issueOrUnavailable()
                ?: global.reader.issueOrUnavailable(),
        )
    }
}

private fun List<LibraryEntry>.storyIdKey(): Set<StoryId> =
    mapTo(linkedSetOf(), LibraryEntry::storyId)

private fun <K, T> ObservationState<K, T>.availableValueOrNull(): T? =
    (this as? ObservationState.Available)?.value

private fun ObservationState<*, *>.issueOrUnavailable(): CatalogUiFailure? = when (this) {
    is ObservationState.Available<*, *> -> issue
    is ObservationState.Unavailable<*> -> failure
    is ObservationState.Pending<*> -> null
}
