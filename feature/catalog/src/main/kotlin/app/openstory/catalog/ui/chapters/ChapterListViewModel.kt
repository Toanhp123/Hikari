package app.openstory.catalog.ui.chapters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.ObservationState
import app.openstory.catalog.ui.state.RefreshState
import app.openstory.catalog.ui.state.completeFailure
import app.openstory.catalog.ui.state.completeSuccess
import app.openstory.catalog.ui.state.hasIssueOrUnavailable
import app.openstory.catalog.ui.state.hasRetainedIssue
import app.openstory.catalog.ui.state.retainedObservation
import app.openstory.catalog.ui.state.startAttempt
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterOverrideKind
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.sync.ChapterSyncReport
import app.openstory.chapters.sync.ChapterSyncService
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.reader.content.ReaderSourceAvailability
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ChapterListViewModel.Factory::class)
class ChapterListViewModel @AssistedInject constructor(
    @Assisted assistedArgs: ChapterListAssistedArgs,
    private val repository: ChapterRepository,
    private val readerSources: ReaderSourceAvailability,
    private val syncService: ChapterSyncService,
) : ViewModel() {
    private val storyId = assistedArgs.storyId
    private val started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
    private val filter = MutableStateFlow(ChapterListFilter.ALL)
    private val tombstonesVisible = MutableStateFlow(false)
    private val refreshState = MutableStateFlow(RefreshState())
    private val correctionFailure = MutableStateFlow<CatalogUiFailure?>(null)
    private var refreshJob: Job? = null

    private val chapterObservation = viewModelScope.retainedObservation(
        key = flowOf(storyId),
        initialKey = storyId,
        started = started,
        observe = repository::observe,
        mapFailure = { _, _ -> CatalogUiFailure(OBSERVE_FAILED, retryable = true) },
    )

    private val readerObservation = viewModelScope.retainedObservation(
        key = flowOf(Unit),
        initialKey = Unit,
        started = started,
        observe = {
            flow {
                val readablePluginIds = readerSources.enabledPluginIds()
                val offlineDownloadPluginIds = readerSources.offlineDownloadPluginIds()
                emit(ReaderAvailability(readablePluginIds, offlineDownloadPluginIds))
            }
        },
        mapFailure = { _, _ -> CatalogUiFailure(READER_CAPABILITY_FAILED, retryable = true) },
    )

    private val observations = combine(
        chapterObservation.state,
        readerObservation.state,
    ) { chapters, reader -> ChapterObservations(chapters, reader) }

    val state = combine(
        observations,
        filter,
        tombstonesVisible,
        refreshState,
        correctionFailure,
    ) { observations, selectedFilter, showTombstones, refresh, correction ->
        reduceChapterState(
            storyId = storyId,
            observations = observations,
            selectedFilter = selectedFilter,
            showTombstones = showTombstones,
            refresh = refresh,
            correctionFailure = correction,
        )
    }.stateIn(
        scope = viewModelScope,
        started = started,
        initialValue = ChapterListUiState(storyId = storyId),
    )

    fun selectFilter(selected: ChapterListFilter) {
        filter.value = selected
    }

    fun setTombstonesVisible(visible: Boolean) {
        tombstonesVisible.value = visible
    }

    fun retryContent() {
        if (chapterObservation.state.value is ObservationState.Unavailable) {
            chapterObservation.retry()
        }
    }

    fun retryObservation() {
        when {
            chapterObservation.state.value.hasRetainedIssue() -> chapterObservation.retry()
            readerObservation.state.value.hasIssueOrUnavailable() -> readerObservation.retry()
        }
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshState.update(RefreshState::startAttempt)
            try {
                when (val report = syncService.sync(storyId)) {
                    is ChapterSyncReport.Success -> refreshState.update(RefreshState::completeSuccess)
                    is ChapterSyncReport.Failure -> refreshState.update {
                        it.completeFailure(report.toCatalogFailure())
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                refreshState.update {
                    it.completeFailure(CatalogUiFailure(SYNC_FAILED, retryable = true))
                }
            }
        }
    }

    fun keepGrouped(releaseId: ChapterReleaseId, chapterId: CanonicalChapterId) {
        saveOverride(ChapterAggregationOverride(releaseId, chapterId, ChapterOverrideKind.FORCE_LINK))
    }

    fun separate(releaseId: ChapterReleaseId) {
        saveOverride(ChapterAggregationOverride(releaseId, null, ChapterOverrideKind.FORCE_SEPARATE))
    }

    private fun saveOverride(override: ChapterAggregationOverride) {
        viewModelScope.launch {
            correctionFailure.value = null
            try {
                repository.saveOverride(storyId, override)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                correctionFailure.value = CatalogUiFailure(CORRECTION_FAILED, retryable = false)
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(assistedArgs: ChapterListAssistedArgs): ChapterListViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val OBSERVE_FAILED = "chapter.list.observe_failed"
        const val READER_CAPABILITY_FAILED = "chapter.list.reader_capability_failed"
        const val CORRECTION_FAILED = "chapter.list.correction_failed"
        const val SYNC_FAILED = "chapter.sync_failed"
    }
}

private data class ChapterObservations(
    val chapters: ObservationState<StoryId, List<CanonicalChapterGroup>>,
    val reader: ObservationState<Unit, ReaderAvailability>,
)

private fun reduceChapterState(
    storyId: StoryId,
    observations: ChapterObservations,
    selectedFilter: ChapterListFilter,
    showTombstones: Boolean,
    refresh: RefreshState,
    correctionFailure: CatalogUiFailure?,
): ChapterListUiState {
    val content = when (val chapters = observations.chapters) {
        is ObservationState.Pending -> ContentState.Pending
        is ObservationState.Unavailable -> ContentState.Failed(chapters.failure)
        is ObservationState.Available -> ContentState.Ready(
            projectChapterContent(
                storyId = storyId,
                groups = chapters.value,
                availability = observations.reader.usableAvailabilityOrNull(),
                readerAvailabilityResolved = observations.reader.isAuthoritativelyResolved(),
                selectedFilter = selectedFilter,
                showTombstones = showTombstones,
            ),
        )
    }
    return ChapterListUiState(
        storyId = storyId,
        content = content,
        refresh = refresh,
        selectedFilter = selectedFilter,
        showTombstones = showTombstones,
        observationIssue = observations.observationIssue(content),
        correctionFailure = correctionFailure,
    )
}

private fun projectChapterContent(
    storyId: StoryId,
    groups: List<CanonicalChapterGroup>,
    availability: ReaderAvailability?,
    readerAvailabilityResolved: Boolean,
    selectedFilter: ChapterListFilter,
    showTombstones: Boolean,
): ChapterListContent {
    val activeGroups = groups.filterNot { group -> group.chapter.tombstoned }
    val visible = groups
        .filter { group -> showTombstones || !group.chapter.tombstoned }
        .filter { group -> selectedFilter.accepts(group) }
        .sortedWith(chapterNewestFirstComparator)
        .map { group -> group.toUiModel(availability) }
    return ChapterListContent(
        chapters = visible,
        releaseTargets = activeGroups.flatMap { group ->
            group.releases.map { release -> ReaderTarget(storyId, group.chapter.id, release.id) }
        },
        readableTargets = activeGroups.flatMap { group ->
            group.releases
                .filter { release -> release.pluginId in availability?.readablePluginIds.orEmpty() }
                .map { release -> ReaderTarget(storyId, group.chapter.id, release.id) }
        },
        downloadableTargets = activeGroups.flatMap { group ->
            group.releases
                .filter { release -> release.pluginId in availability?.offlineDownloadPluginIds.orEmpty() }
                .map { release -> ReaderTarget(storyId, group.chapter.id, release.id) }
        },
        chapterCount = activeGroups.size,
        readerAvailabilityResolved = readerAvailabilityResolved,
    )
}

private fun ObservationState<Unit, ReaderAvailability>.usableAvailabilityOrNull(): ReaderAvailability? =
    when (this) {
        is ObservationState.Available -> value.takeIf { issue == null }
        is ObservationState.Pending,
        is ObservationState.Unavailable -> null
    }

private fun ObservationState<Unit, ReaderAvailability>.isAuthoritativelyResolved(): Boolean =
    this is ObservationState.Available && issue == null

private fun ChapterObservations.observationIssue(content: ContentState<ChapterListContent>): CatalogUiFailure? {
    if (content !is ContentState.Ready) return null
    val chapterIssue = (chapters as? ObservationState.Available)?.issue
    return chapterIssue ?: when (val readerState = reader) {
        is ObservationState.Available -> readerState.issue
        is ObservationState.Unavailable -> readerState.failure
        is ObservationState.Pending -> null
    }
}

private fun ChapterSyncReport.Failure.toCatalogFailure(): CatalogUiFailure {
    val primary = failures.firstOrNull()
    return CatalogUiFailure(
        code = primary?.code ?: "chapter.sync_failed",
        retryable = failures.any { it.retryable },
    )
}

data class ChapterListAssistedArgs(val storyId: StoryId)
