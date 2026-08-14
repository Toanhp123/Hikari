package app.openstory.catalog.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LibraryViewModel @Inject constructor(
    library: LibraryService,
    catalog: CatalogStoryProjectionRepository,
    mappings: ContentMappingRepository,
    progress: ReadingProgressRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val selectedStatus = MutableStateFlow(savedState.enumOrNull<LibraryStatus>(STATUS_KEY))
    private val query = MutableStateFlow(savedState[QUERY_KEY] ?: "")
    private val sort = MutableStateFlow(savedState.enumOrDefault(SORT_KEY, LibrarySort.LAST_ACTIVITY))
    private val displayMode = MutableStateFlow(savedState.enumOrDefault(DISPLAY_MODE_KEY, LibraryDisplayMode.GRID))
    private val sourceFilter = MutableStateFlow(savedState.sourceFilterOrNull(SOURCE_FILTER_KEY))

    private val controls = combine(selectedStatus, query, sort, displayMode, sourceFilter) {
            status, currentQuery, currentSort, mode, source ->
        LibraryControls(status, currentQuery, currentSort, mode, source)
    }
    private val enrichment = combine(
        catalog.observe().preserveLatest(emptyList()),
        mappings.observeAll().preserveLatest(emptyList()),
        progress.observeAll().preserveLatest(emptyList()),
    ) {
            projections, currentMappings, records ->
        LibraryEnrichment(projections, currentMappings, records)
    }

    val state = combine(library.observe(), enrichment, controls) { entries, enrichment, controls ->
        projectLibrary(entries, enrichment, controls)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = LibraryUiState(),
    )

    private fun <T> Flow<T>.preserveLatest(initial: T): Flow<T> = flow {
        var latest = initial
        emit(initial)
        try {
            collect { value ->
                latest = value
                emit(value)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emit(latest)
        }
    }

    fun selectStatus(status: LibraryStatus?) {
        selectedStatus.value = status
        savedState[STATUS_KEY] = status?.name
    }

    fun updateQuery(value: String) {
        query.value = value
        savedState[QUERY_KEY] = value
    }

    fun selectSort(value: LibrarySort) {
        sort.value = value
        savedState[SORT_KEY] = value.name
    }

    fun selectDisplayMode(value: LibraryDisplayMode) {
        displayMode.value = value
        savedState[DISPLAY_MODE_KEY] = value.name
    }

    fun selectSourceFilter(value: LibrarySourceState?) {
        sourceFilter.value = value
        savedState[SOURCE_FILTER_KEY] = value?.name
    }

    fun clearFilters() {
        selectStatus(null)
        updateQuery("")
        selectSourceFilter(null)
    }

    fun resetFilterSelections() {
        selectStatus(null)
        selectSourceFilter(null)
        selectSort(LibrarySort.LAST_ACTIVITY)
    }

    private companion object {
        const val QUERY_KEY = "library.query"
        const val STATUS_KEY = "library.status"
        const val SORT_KEY = "library.sort"
        const val DISPLAY_MODE_KEY = "library.display-mode"
        const val SOURCE_FILTER_KEY = "library.source-filter"
    }
}

private data class LibraryControls(
    val status: LibraryStatus?,
    val query: String,
    val sort: LibrarySort,
    val displayMode: LibraryDisplayMode,
    val sourceFilter: LibrarySourceState?,
)

private data class LibraryEnrichment(
    val catalog: List<CatalogStoryProjection>,
    val mappings: List<ContentMapping>,
    val progress: List<ReadingProgress>,
)

private fun projectLibrary(
    entries: List<LibraryEntry>,
    enrichment: LibraryEnrichment,
    controls: LibraryControls,
): LibraryUiState {
    val catalog = enrichment.catalog.associateBy(CatalogStoryProjection::storyId)
    val mappedStories = enrichment.mappings.mapTo(hashSetOf(), ContentMapping::storyId)
    val latestProgress = enrichment.progress.groupBy(ReadingProgress::storyId).mapValues { (_, records) ->
        records.maxWith(compareBy<ReadingProgress> { it.updatedAtEpochMillis }.thenBy { it.releaseId.value })
    }
    val allItems = entries.map { entry ->
        entry.toUiModel(catalog[entry.storyId], entry.storyId in mappedStories, latestProgress[entry.storyId])
    }
    val normalizedQuery = controls.query.trim().lowercase(Locale.ROOT)
    val visible = allItems.asSequence()
        .filter { controls.status == null || it.status == controls.status }
        .filter { controls.sourceFilter == null || it.sourceState == controls.sourceFilter }
        .filter { normalizedQuery.isEmpty() || normalizedQuery in it.title.lowercase(Locale.ROOT) }
        .sortedWith(controls.sort.comparator())
        .toList()
    return LibraryUiState(
        items = visible,
        totalCount = entries.size,
        statusCounts = LibraryStatus.entries.associateWith { status -> entries.count { it.status == status } },
        selectedStatus = controls.status,
        query = controls.query,
        sort = controls.sort,
        displayMode = controls.displayMode,
        sourceFilter = controls.sourceFilter,
        loading = false,
    )
}

private fun LibraryEntry.toUiModel(
    projection: CatalogStoryProjection?,
    mapped: Boolean,
    progress: ReadingProgress?,
) = LibraryItemUiModel(
    storyId = storyId,
    title = projection?.title ?: storyId.value,
    contentType = projection?.contentType,
    coverUrl = projection?.coverUrl,
    status = status,
    sourceState = if (mapped) LibrarySourceState.LINKED else LibrarySourceState.NO_MAPPING,
    progressFraction = progress?.position?.fraction,
    addedAt = addedAt,
    updatedAt = maxOf(updatedAt, progress?.updatedAtEpochMillis ?: updatedAt),
)

private fun LibrarySort.comparator(): Comparator<LibraryItemUiModel> = when (this) {
    LibrarySort.LAST_ACTIVITY -> compareByDescending<LibraryItemUiModel> { it.updatedAt }.thenBy { it.storyId.value }
    LibrarySort.TITLE -> compareBy<LibraryItemUiModel> { it.title.lowercase(Locale.ROOT) }.thenBy { it.storyId.value }
    LibrarySort.DATE_ADDED -> compareByDescending<LibraryItemUiModel> { it.addedAt }.thenBy { it.storyId.value }
}

private inline fun <reified T : Enum<T>> SavedStateHandle.enumOrNull(key: String): T? =
    get<String>(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

private inline fun <reified T : Enum<T>> SavedStateHandle.enumOrDefault(key: String, default: T): T =
    enumOrNull<T>(key) ?: default

private fun SavedStateHandle.sourceFilterOrNull(key: String): LibrarySourceState? =
    enumOrNull<LibrarySourceState>(key)?.takeIf {
        it == LibrarySourceState.LINKED || it == LibrarySourceState.NO_MAPPING
    }
