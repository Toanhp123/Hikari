package app.openstory.catalog.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LibraryViewModel @Inject constructor(
    library: LibraryService,
    catalog: CatalogStoryProjectionRepository,
) : ViewModel() {
    private val selectedStatus = MutableStateFlow<LibraryStatus?>(null)
    private val sort = MutableStateFlow(LibrarySort.LAST_ACTIVITY)

    val state = combine(
        library.observe(),
        catalog.observe(),
        selectedStatus,
        sort,
    ) { entries, projections, selected, currentSort ->
        val catalogByStory = projections.associateBy(CatalogStoryProjection::storyId)
        val visible = entries
            .asSequence()
            .filter { selected == null || it.status == selected }
            .map { entry -> entry.toUiModel(catalogByStory[entry.storyId]) }
            .sortedWith(currentSort.comparator())
            .toList()
        LibraryUiState(
            items = visible,
            selectedStatus = selected,
            sort = currentSort,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LibraryUiState(),
    )

    fun selectStatus(status: LibraryStatus?) {
        selectedStatus.value = status
    }

    fun selectSort(sort: LibrarySort) {
        this.sort.value = sort
    }
}

private fun LibraryEntry.toUiModel(projection: CatalogStoryProjection?) = LibraryItemUiModel(
    storyId = storyId,
    title = projection?.title ?: storyId.value,
    contentType = projection?.contentType,
    coverUrl = projection?.coverUrl,
    status = status,
    sourceState = LibrarySourceState.NO_MAPPING,
    addedAt = addedAt,
    updatedAt = updatedAt,
)

private fun LibrarySort.comparator(): Comparator<LibraryItemUiModel> = when (this) {
    LibrarySort.LAST_ACTIVITY -> compareByDescending<LibraryItemUiModel> { it.updatedAt }
        .thenBy { it.storyId.value }
    LibrarySort.TITLE -> compareBy<LibraryItemUiModel> { it.title.lowercase(Locale.ROOT) }
        .thenBy { it.storyId.value }
    LibrarySort.DATE_ADDED -> compareByDescending<LibraryItemUiModel> { it.addedAt }
        .thenBy { it.storyId.value }
}
