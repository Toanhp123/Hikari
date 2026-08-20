package app.openstory.catalog.ui.chapters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterOverrideKind
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.sync.ChapterSyncReport
import app.openstory.chapters.sync.ChapterSyncService
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ChapterListViewModel.Factory::class)
class ChapterListViewModel @AssistedInject constructor(
    @Assisted assistedArgs: ChapterListAssistedArgs,
    private val repository: ChapterRepository,
    private val readerSources: ReaderSourceAvailability,
    private val syncService: ChapterSyncService,
) : ViewModel() {
    private val storyId = assistedArgs.storyId
    private val filter = MutableStateFlow(ChapterListFilter.ALL)
    private val tombstonesVisible = MutableStateFlow(false)
    private val refreshing = MutableStateFlow(false)
    private val failure = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null

    private val groupsWithReaderSources = combine(
        repository.observe(storyId).catch {
            failure.value = OBSERVE_FAILED
            emit(emptyList())
        },
        flow {
            emit(
                ReaderAvailability(
                    readablePluginIds = readerSources.enabledPluginIds(),
                    offlineDownloadPluginIds = readerSources.offlineDownloadPluginIds(),
                ),
            )
        }.catch { emit(ReaderAvailability()) },
    ) { groups, availability -> groups to availability }

    private val refreshState = combine(refreshing, failure) { isRefreshing, currentFailure ->
        RefreshState(isRefreshing, currentFailure)
    }

    val state = combine(
        groupsWithReaderSources,
        filter,
        tombstonesVisible,
        refreshState,
    ) { (groups, availability), selectedFilter, showTombstones, refresh ->
        val activeGroups = groups.filterNot { group -> group.chapter.tombstoned }
        val visible = groups
            .filter { group -> showTombstones || !group.chapter.tombstoned }
            .filter { group -> selectedFilter.accepts(group) }
            .sortedWith(chapterNewestFirstComparator)
            .map { group -> group.toUiModel(availability) }
        ChapterListUiState(
            storyId = storyId,
            loading = false,
            chapters = visible,
            releaseTargets = activeGroups.flatMap { group ->
                group.releases.map { release -> ReaderTarget(storyId, group.chapter.id, release.id) }
            },
            readableTargets = activeGroups.flatMap { group ->
                group.releases
                    .filter { release -> release.pluginId in availability.readablePluginIds }
                    .map { release -> ReaderTarget(storyId, group.chapter.id, release.id) }
            },
            downloadableTargets = activeGroups.flatMap { group ->
                group.releases
                    .filter { release -> release.pluginId in availability.offlineDownloadPluginIds }
                    .map { release -> ReaderTarget(storyId, group.chapter.id, release.id) }
            },
            chapterCount = activeGroups.size,
            selectedFilter = selectedFilter,
            showTombstones = showTombstones,
            refreshing = refresh.refreshing,
            failure = refresh.failure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ChapterListUiState(storyId = storyId),
    )

    fun selectFilter(selected: ChapterListFilter) {
        filter.value = selected
    }

    fun setTombstonesVisible(visible: Boolean) {
        tombstonesVisible.value = visible
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            refreshing.value = true
            failure.value = null
            try {
                when (val report = syncService.sync(storyId)) {
                    is ChapterSyncReport.Success -> Unit
                    is ChapterSyncReport.Failure -> {
                        failure.value = report.failures.firstOrNull()?.code ?: SYNC_FAILED
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure.value = SYNC_FAILED
            } finally {
                refreshing.value = false
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
            failure.value = null
            try {
                repository.saveOverride(storyId, override)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure.value = CORRECTION_FAILED
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
        const val CORRECTION_FAILED = "chapter.list.correction_failed"
        const val SYNC_FAILED = "chapter.sync_failed"
    }
}

private data class RefreshState(
    val refreshing: Boolean,
    val failure: String?,
)

data class ChapterListAssistedArgs(val storyId: StoryId)
