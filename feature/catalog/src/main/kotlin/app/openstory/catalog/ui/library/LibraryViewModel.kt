package app.openstory.catalog.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.model.Score
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.ObservationState
import app.openstory.catalog.ui.state.hasRetainedIssue
import app.openstory.catalog.ui.state.retainedObservation
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LibraryViewModel @Inject constructor(
    library: LibraryService,
    catalog: CatalogStoryProjectionRepository,
    mappings: ContentMappingRepository,
    progress: ReadingProgressRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
    private val selectedStatus = MutableStateFlow(savedState.enumOrNull<LibraryStatus>(STATUS_KEY))
    private val query = MutableStateFlow(savedState[QUERY_KEY] ?: "")
    private val sort = MutableStateFlow(savedState.enumOrDefault(SORT_KEY, LibrarySort.LAST_ACTIVITY))
    private val displayMode = MutableStateFlow(savedState.enumOrDefault(DISPLAY_MODE_KEY, LibraryDisplayMode.GRID))
    private val sourceFilter = MutableStateFlow(savedState.sourceFilterOrNull(SOURCE_FILTER_KEY))

    private val membershipObservation = viewModelScope.retainedObservation(
        key = flowOf(Unit),
        initialKey = Unit,
        started = started,
        observe = { library.observe() },
        mapFailure = { _, _ -> CatalogUiFailure("library.membership.observe_failed", retryable = true) },
    )
    private val catalogObservation = viewModelScope.retainedObservation(
        key = flowOf(Unit),
        initialKey = Unit,
        started = started,
        observe = { catalog.observe() },
        mapFailure = { _, _ -> CatalogUiFailure("library.catalog.observe_failed", retryable = true) },
    )
    private val mappingObservation = viewModelScope.retainedObservation(
        key = flowOf(Unit),
        initialKey = Unit,
        started = started,
        observe = { mappings.observeAll() },
        mapFailure = { _, _ -> CatalogUiFailure("library.mappings.observe_failed", retryable = true) },
    )
    private val progressObservation = viewModelScope.retainedObservation(
        key = flowOf(Unit),
        initialKey = Unit,
        started = started,
        observe = { progress.observeAll() },
        mapFailure = { _, _ -> CatalogUiFailure("library.progress.observe_failed", retryable = true) },
    )

    private val observations = combine(
        membershipObservation.state,
        catalogObservation.state,
        mappingObservation.state,
        progressObservation.state,
    ) { membership, catalogState, mappingState, progressState ->
        LibraryObservations(membership, catalogState, mappingState, progressState)
    }

    private val presentationControls = combine(
        selectedStatus,
        query,
        sort,
        displayMode,
        sourceFilter,
    ) { status, currentQuery, currentSort, mode, source ->
        LibraryPresentationControls(status, currentQuery, currentSort, mode, source)
    }

    val state = combine(observations, presentationControls) { current, controls ->
        reduceLibraryState(current, controls)
    }.stateIn(
        scope = viewModelScope,
        started = started,
        initialValue = LibraryUiState(),
    )

    fun retryContent() {
        if (membershipObservation.state.value is ObservationState.Unavailable) {
            membershipObservation.retry()
        }
    }

    fun retryCollection() {
        val membership = membershipObservation.state.value as? ObservationState.Available ?: return
        if (membership.value.isEmpty()) return
        val required = requiredLocalDependencies(currentLibraryControls())
        if (LibraryDependency.CATALOG in required && catalogObservation.state.value is ObservationState.Unavailable) {
            catalogObservation.retry()
        }
        if (LibraryDependency.MAPPINGS in required && mappingObservation.state.value is ObservationState.Unavailable) {
            mappingObservation.retry()
        }
        if (LibraryDependency.PROGRESS in required && progressObservation.state.value is ObservationState.Unavailable) {
            progressObservation.retry()
        }
    }

    fun retryObservation() {
        observationRetryAction()?.invoke()
    }

    private fun observationRetryAction(): (() -> Unit)? {
        val membership = membershipObservation.state.value
        val required = (membership as? ObservationState.Available)
            ?.takeIf { it.value.isNotEmpty() }
            ?.let { requiredLocalDependencies(currentLibraryControls()) }
        return when {
            membership.hasRetainedIssue() -> membershipObservation::retry
            required == null -> null
            catalogObservation.state.value.hasSurfacedIssue(LibraryDependency.CATALOG in required) ->
                catalogObservation::retry
            mappingObservation.state.value.hasSurfacedIssue(LibraryDependency.MAPPINGS in required) ->
                mappingObservation::retry
            progressObservation.state.value.hasSurfacedIssue(LibraryDependency.PROGRESS in required) ->
                progressObservation::retry
            else -> null
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
        val accepted = value.takeIf { it == LibrarySourceState.LINKED || it == LibrarySourceState.NO_MAPPING }
        sourceFilter.value = accepted
        savedState[SOURCE_FILTER_KEY] = accepted?.name
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

    private fun currentLibraryControls() = LibraryControls(
        query = query.value,
        sort = sort.value,
        sourceFilter = sourceFilter.value,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val QUERY_KEY = "library.query"
        const val STATUS_KEY = "library.status"
        const val SORT_KEY = "library.sort"
        const val DISPLAY_MODE_KEY = "library.display-mode"
        const val SOURCE_FILTER_KEY = "library.source-filter"
    }
}

private enum class LibraryDependency {
    CATALOG,
    MAPPINGS,
    PROGRESS,
}

private data class LibraryControls(
    val query: String,
    val sort: LibrarySort,
    val sourceFilter: LibrarySourceState?,
)

private data class LibraryPresentationControls(
    val status: LibraryStatus?,
    val query: String,
    val sort: LibrarySort,
    val displayMode: LibraryDisplayMode,
    val sourceFilter: LibrarySourceState?,
) {
    val dependencies: LibraryControls
        get() = LibraryControls(query, sort, sourceFilter)
}

private data class LibraryObservations(
    val membership: ObservationState<Unit, List<LibraryEntry>>,
    val catalog: ObservationState<Unit, List<CatalogStoryProjection>>,
    val mappings: ObservationState<Unit, List<ContentMapping>>,
    val progress: ObservationState<Unit, List<ReadingProgress>>,
)

private fun reduceLibraryState(
    observations: LibraryObservations,
    controls: LibraryPresentationControls,
): LibraryUiState {
    val baseState = LibraryUiState(
        selectedStatus = controls.status,
        query = controls.query,
        sort = controls.sort,
        displayMode = controls.displayMode,
        sourceFilter = controls.sourceFilter,
    )
    return when (val membership = observations.membership) {
        is ObservationState.Pending -> baseState
        is ObservationState.Unavailable -> baseState.copy(
            content = ContentState.Failed(membership.failure),
        )
        is ObservationState.Available -> {
            val entries = membership.value
            val statusCounts = LibraryStatus.entries.associateWith { status ->
                entries.count { it.status == status }
            }
            if (entries.isEmpty()) {
                baseState.copy(
                    content = ContentState.Ready(
                        LibraryContent(0, statusCounts, LibraryCollectionState.Ready(emptyList())),
                    ),
                    observationIssue = membership.issue,
                )
            } else {
                val required = requiredLocalDependencies(controls.dependencies)
                val baseItems = projectLibraryBaseItems(
                    entries = entries,
                    catalog = observations.catalog.availableValueOrNull(),
                    mappings = observations.mappings.availableValueOrNull(),
                    progress = observations.progress.availableValueOrNull(),
                ).filter { controls.status == null || it.status == controls.status }
                val collection = projectCollectionState(
                    observations = observations,
                    required = required,
                    baseItems = baseItems,
                    controls = controls.dependencies,
                )
                baseState.copy(
                    content = ContentState.Ready(
                        LibraryContent(entries.size, statusCounts, collection),
                    ),
                    observationIssue = nonBlockingIssue(observations, required),
                )
            }
        }
    }
}

private fun projectCollectionState(
    observations: LibraryObservations,
    required: Set<LibraryDependency>,
    baseItems: List<LibraryItemUiModel>,
    controls: LibraryControls,
): LibraryCollectionState {
    val unavailable = LibraryDependency.entries.firstNotNullOfOrNull { dependency ->
        if (dependency !in required) return@firstNotNullOfOrNull null
        observations.stateFor(dependency).unavailableFailureOrNull()
    }
    return when {
        unavailable != null -> LibraryCollectionState.Unavailable(unavailable)
        required.any { observations.stateFor(it) is ObservationState.Pending } -> LibraryCollectionState.Resolving
        else -> LibraryCollectionState.Ready(projectLibraryCollection(baseItems, controls))
    }
}

private fun projectLibraryBaseItems(
    entries: List<LibraryEntry>,
    catalog: List<CatalogStoryProjection>?,
    mappings: List<ContentMapping>?,
    progress: List<ReadingProgress>?,
): List<LibraryItemUiModel> {
    val catalogByStory = catalog.orEmpty().associateBy(CatalogStoryProjection::storyId)
    val mappedStories = mappings?.mapTo(hashSetOf(), ContentMapping::storyId)
    val latestProgress = progress.orEmpty()
        .groupBy(ReadingProgress::storyId)
        .mapValues { (_, records) ->
            records.maxWith(
                compareBy<ReadingProgress> { it.updatedAtEpochMillis }
                    .thenBy { it.releaseId.value },
            )
        }
    return entries.map { entry ->
        entry.toLibraryItemUiModel(
            projection = catalogByStory[entry.storyId],
            sourceState = when {
                mappedStories == null -> LibrarySourceState.UNKNOWN
                entry.storyId in mappedStories -> LibrarySourceState.LINKED
                else -> LibrarySourceState.NO_MAPPING
            },
            progress = latestProgress[entry.storyId],
        )
    }
}

private fun requiredLocalDependencies(controls: LibraryControls): Set<LibraryDependency> = buildSet {
    if (controls.query.isNotBlank() || controls.sort == LibrarySort.TITLE) {
        add(LibraryDependency.CATALOG)
    }
    if (controls.sourceFilter == LibrarySourceState.LINKED ||
        controls.sourceFilter == LibrarySourceState.NO_MAPPING
    ) {
        add(LibraryDependency.MAPPINGS)
    }
    if (controls.sort == LibrarySort.LAST_ACTIVITY) {
        add(LibraryDependency.PROGRESS)
    }
}

private fun projectLibraryCollection(
    baseItems: List<LibraryItemUiModel>,
    controls: LibraryControls,
): List<LibraryItemUiModel> {
    val normalizedQuery = controls.query.trim().lowercase(Locale.ROOT)
    return baseItems.asSequence()
        .filter { controls.sourceFilter == null || it.sourceState == controls.sourceFilter }
        .filter { normalizedQuery.isEmpty() || normalizedQuery in it.title.lowercase(Locale.ROOT) }
        .sortedWith(controls.sort.comparator())
        .toList()
}

internal fun LibraryEntry.toLibraryItemUiModel(
    projection: CatalogStoryProjection?,
    sourceState: LibrarySourceState,
    progress: ReadingProgress?,
) = LibraryItemUiModel(
    storyId = storyId,
    title = projection?.title ?: storyId.value,
    contentType = projection?.contentType,
    coverUrl = projection?.coverUrl,
    status = status,
    sourceState = sourceState,
    progressFraction = progress?.position?.fraction,
    addedAt = addedAt,
    updatedAt = maxOf(updatedAt, progress?.updatedAtEpochMillis ?: updatedAt),
    publicationStatus = projection?.publicationStatus,
    score = projection?.score?.let { canonical ->
        Score(canonical.normalizedValue * PRESENTATION_SCORE_SCALE, PRESENTATION_SCORE_SCALE)
    },
)

private const val PRESENTATION_SCORE_SCALE = 10.0

private fun LibrarySort.comparator(): Comparator<LibraryItemUiModel> = when (this) {
    LibrarySort.LAST_ACTIVITY -> compareByDescending<LibraryItemUiModel> { it.updatedAt }
        .thenBy { it.storyId.value }
    LibrarySort.TITLE -> compareBy<LibraryItemUiModel> { it.title.lowercase(Locale.ROOT) }
        .thenBy { it.storyId.value }
    LibrarySort.DATE_ADDED -> compareByDescending<LibraryItemUiModel> { it.addedAt }
        .thenBy { it.storyId.value }
}

private fun LibraryObservations.stateFor(dependency: LibraryDependency): ObservationState<*, *> =
    when (dependency) {
        LibraryDependency.CATALOG -> catalog
        LibraryDependency.MAPPINGS -> mappings
        LibraryDependency.PROGRESS -> progress
    }

private fun ObservationState<*, *>.unavailableFailureOrNull(): CatalogUiFailure? =
    (this as? ObservationState.Unavailable<*>)?.failure

private fun <T> ObservationState<Unit, T>.availableValueOrNull(): T? =
    (this as? ObservationState.Available)?.value

private fun nonBlockingIssue(
    observations: LibraryObservations,
    required: Set<LibraryDependency>,
): CatalogUiFailure? = observations.membership.issueOrNull()
    ?: observations.catalog.nonBlockingIssue(LibraryDependency.CATALOG in required)
    ?: observations.mappings.nonBlockingIssue(LibraryDependency.MAPPINGS in required)
    ?: observations.progress.nonBlockingIssue(LibraryDependency.PROGRESS in required)

private fun ObservationState<*, *>.issueOrNull(): CatalogUiFailure? =
    (this as? ObservationState.Available<*, *>)?.issue

private fun ObservationState<*, *>.nonBlockingIssue(required: Boolean): CatalogUiFailure? = when (this) {
    is ObservationState.Available<*, *> -> issue
    is ObservationState.Unavailable<*> -> failure.takeUnless { required }
    is ObservationState.Pending<*> -> null
}

private fun ObservationState<*, *>.hasSurfacedIssue(required: Boolean): Boolean =
    nonBlockingIssue(required) != null

private inline fun <reified T : Enum<T>> SavedStateHandle.enumOrNull(key: String): T? =
    get<String>(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

private inline fun <reified T : Enum<T>> SavedStateHandle.enumOrDefault(key: String, default: T): T =
    enumOrNull<T>(key) ?: default

private fun SavedStateHandle.sourceFilterOrNull(key: String): LibrarySourceState? =
    enumOrNull<LibrarySourceState>(key)?.takeIf {
        it == LibrarySourceState.LINKED || it == LibrarySourceState.NO_MAPPING
    }
