package app.openstory.catalog.ui.chapters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterOverrideKind
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ChapterListViewModel.Factory::class)
class ChapterListViewModel @AssistedInject constructor(
    @Assisted assistedArgs: ChapterListAssistedArgs,
    private val repository: ChapterRepository,
) : ViewModel() {
    private val storyId = assistedArgs.storyId
    private val expanded = MutableStateFlow<Set<CanonicalChapterId>>(emptySet())
    private val filter = MutableStateFlow(ChapterListFilter.ALL)
    private val tombstonesVisible = MutableStateFlow(false)
    private val failure = MutableStateFlow<String?>(null)

    val state = combine(
        repository.observe(storyId).catch {
            failure.value = OBSERVE_FAILED
            emit(emptyList())
        },
        expanded,
        filter,
        tombstonesVisible,
        failure,
    ) { groups, expandedIds, selectedFilter, showTombstones, currentFailure ->
        val activeGroups = groups.filterNot { group -> group.chapter.tombstoned }
        val visible = groups
            .filter { group -> showTombstones || !group.chapter.tombstoned }
            .filter { group -> selectedFilter.accepts(group) }
            .map { group -> group.toUiModel(group.chapter.id in expandedIds) }
        ChapterListUiState(
            chapters = visible,
            unreadCount = activeGroups.size,
            selectedFilter = selectedFilter,
            showTombstones = showTombstones,
            failure = currentFailure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ChapterListUiState(),
    )

    fun toggleExpanded(chapterId: CanonicalChapterId) {
        expanded.value = expanded.value.toggle(chapterId)
    }

    fun selectFilter(selected: ChapterListFilter) {
        filter.value = selected
    }

    fun setTombstonesVisible(visible: Boolean) {
        tombstonesVisible.value = visible
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
        const val OBSERVE_FAILED = "chapter.list.observe_failed"
        const val CORRECTION_FAILED = "chapter.list.correction_failed"
    }
}

data class ChapterListAssistedArgs(val storyId: StoryId)

enum class ChapterListFilter {
    ALL,
    MULTI_RELEASE,
    ;

    internal fun accepts(group: CanonicalChapterGroup): Boolean = when (this) {
        ALL -> true
        MULTI_RELEASE -> group.releases.size > 1
    }
}

data class ChapterListUiState(
    val chapters: List<ChapterItemUiModel> = emptyList(),
    val unreadCount: Int = 0,
    val selectedFilter: ChapterListFilter = ChapterListFilter.ALL,
    val showTombstones: Boolean = false,
    val failure: String? = null,
)

data class ChapterItemUiModel(
    val id: CanonicalChapterId,
    val label: String,
    val tombstoned: Boolean,
    val expanded: Boolean,
    val releases: List<ChapterReleaseUiModel>,
)

data class ChapterReleaseUiModel(
    val id: ChapterReleaseId,
    val pluginId: PluginId,
    val sourceName: String,
    val languageLabel: String,
    val publishedAtEpochMillis: Long?,
)

data class ChapterListActions(
    val onToggleExpanded: (CanonicalChapterId) -> Unit = {},
    val onFilterSelected: (ChapterListFilter) -> Unit = {},
    val onTombstonesVisible: (Boolean) -> Unit = {},
    val onKeepGrouped: (ChapterReleaseId, CanonicalChapterId) -> Unit = { _, _ -> },
    val onSeparate: (ChapterReleaseId) -> Unit = {},
)

private fun CanonicalChapterGroup.toUiModel(expanded: Boolean) = ChapterItemUiModel(
    id = chapter.id,
    label = chapter.displayLabel,
    tombstoned = chapter.tombstoned,
    expanded = expanded,
    releases = releases.map { release ->
        ChapterReleaseUiModel(
            id = release.id,
            pluginId = release.pluginId,
            sourceName = release.pluginId.value,
            languageLabel = release.languageTag.languageDisplayName(),
            publishedAtEpochMillis = release.publishedAtEpochMillis,
        )
    },
)

private fun Set<CanonicalChapterId>.toggle(id: CanonicalChapterId): Set<CanonicalChapterId> =
    if (id in this) this - id else this + id

private fun String.languageDisplayName(): String = Locale.forLanguageTag(this)
    .getDisplayLanguage(Locale.ENGLISH)
    .takeIf(String::isNotBlank)
    ?: this
