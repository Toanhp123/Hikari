package app.openstory.catalog.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.activity.LibraryActivityProjector
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
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryService
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.reader.content.ReaderSourceAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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
class UpdatesViewModel @Inject constructor(
    library: LibraryService,
    catalog: CatalogStoryProjectionRepository,
    chapters: ChapterRepository,
    mappings: ContentMappingRepository,
    readerSources: ReaderSourceAvailability,
    private val projector: LibraryActivityProjector,
) : ViewModel() {
    private val started = SharingStarted.WhileSubscribed(5_000L)

    private val libraryObservation = viewModelScope.retainedObservation(
        key = flowOf(Unit),
        initialKey = Unit,
        started = started,
        observe = { library.observe() },
        mapFailure = { _, _ -> CatalogUiFailure("updates.library.observe_failed", retryable = true) },
    )

    private val libraryStoryIds: Flow<Set<StoryId>> = libraryObservation.state
        .map { state ->
            when (state) {
                is ObservationState.Available -> state.value.mapTo(linkedSetOf(), LibraryEntry::storyId)
                is ObservationState.Pending,
                is ObservationState.Unavailable -> emptySet()
            }
        }
        .distinctUntilChanged()

    private val chapterObservation = viewModelScope.retainedObservation(
        key = libraryStoryIds,
        initialKey = emptySet(),
        started = started,
        observe = { storyIds ->
            if (storyIds.isEmpty()) flowOf(emptyList()) else chapters.observeForStories(storyIds)
        },
        mapFailure = { _, _ -> CatalogUiFailure("updates.chapters.observe_failed", retryable = true) },
    )

    private val mappingObservation = viewModelScope.retainedObservation(
        key = libraryStoryIds,
        initialKey = emptySet(),
        started = started,
        observe = { storyIds ->
            if (storyIds.isEmpty()) flowOf(emptyList()) else mappings.observeForStories(storyIds)
        },
        mapFailure = { _, _ -> CatalogUiFailure("updates.mappings.observe_failed", retryable = true) },
    )

    private val catalogObservation = viewModelScope.retainedObservation(
        key = libraryStoryIds,
        initialKey = emptySet(),
        started = started,
        observe = { storyIds ->
            if (storyIds.isEmpty()) flowOf(emptyList()) else catalog.observeForStories(storyIds)
        },
        mapFailure = { _, _ -> CatalogUiFailure("updates.catalog.observe_failed", retryable = true) },
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
        mapFailure = { _, _ -> CatalogUiFailure("updates.reader.observe_failed", retryable = true) },
    )

    val state = combine(
        libraryObservation.state,
        chapterObservation.state,
        mappingObservation.state,
        catalogObservation.state,
        readerObservation.state,
    ) { libraryState, chapterState, mappingState, catalogState, readerState ->
        reduceUpdatesState(
            libraryState = libraryState,
            chapterState = chapterState,
            mappingState = mappingState,
            catalogState = catalogState,
            readerState = readerState,
            projector = projector,
        )
    }.stateIn(
        viewModelScope,
        started,
        UpdatesUiState(),
    )

    fun retryContent() {
        when (val libraryState = libraryObservation.state.value) {
            is ObservationState.Unavailable -> libraryObservation.retry()
            is ObservationState.Available -> {
                val expectedKey = libraryState.value.storyIdKey()
                if (expectedKey.isEmpty()) return

                if (chapterObservation.state.value.forExpectedKey(expectedKey) is ObservationState.Unavailable) {
                    chapterObservation.retry()
                }
                if (mappingObservation.state.value.forExpectedKey(expectedKey) is ObservationState.Unavailable) {
                    mappingObservation.retry()
                }
            }
            is ObservationState.Pending -> Unit
        }
    }

    fun retryObservation() {
        val libraryState = libraryObservation.state.value
        if (libraryState.hasRetainedIssue()) {
            libraryObservation.retry()
            return
        }
        if (libraryState !is ObservationState.Available) return

        val expectedKey = libraryState.value.storyIdKey()
        if (expectedKey.isEmpty()) return

        val chapterState = chapterObservation.state.value.forExpectedKey(expectedKey)
        if (chapterState.hasIssueOrUnavailable()) {
            chapterObservation.retry()
            return
        }
        val mappingState = mappingObservation.state.value.forExpectedKey(expectedKey)
        if (mappingState.hasIssueOrUnavailable()) {
            mappingObservation.retry()
            return
        }
        val catalogState = catalogObservation.state.value.forExpectedKey(expectedKey)
        if (catalogState.hasIssueOrUnavailable()) {
            catalogObservation.retry()
            return
        }
        if (readerObservation.state.value.hasIssueOrUnavailable()) {
            readerObservation.retry()
        }
    }
}

private fun reduceUpdatesState(
    libraryState: ObservationState<Unit, List<LibraryEntry>>,
    chapterState: ObservationState<Set<StoryId>, List<CanonicalChapterGroup>>,
    mappingState: ObservationState<Set<StoryId>, List<ContentMapping>>,
    catalogState: ObservationState<Set<StoryId>, List<CatalogStoryProjection>>,
    readerState: ObservationState<Unit, Set<PluginId>>,
    projector: LibraryActivityProjector,
): UpdatesUiState = when (libraryState) {
    is ObservationState.Pending -> UpdatesUiState()
    is ObservationState.Unavailable -> UpdatesUiState(
        content = ContentState.Failed(libraryState.failure),
    )
    is ObservationState.Available -> {
        val entries = libraryState.value
        val expectedKey = entries.storyIdKey()
        if (expectedKey.isEmpty()) {
            UpdatesUiState(
                content = ContentState.Ready(UpdatesContent()),
                observationIssue = libraryState.issue,
            )
        } else {
            val currentChapters = chapterState.forExpectedKey(expectedKey)
            val currentMappings = mappingState.forExpectedKey(expectedKey)
            val currentCatalog = catalogState.forExpectedKey(expectedKey)
            val content = requiredContent(
                entries = entries,
                chapters = currentChapters,
                mappings = currentMappings,
                catalog = currentCatalog,
                reader = readerState,
                projector = projector,
            )
            UpdatesUiState(
                content = content,
                observationIssue = nonBlockingIssue(
                    content = content,
                    library = libraryState,
                    chapters = currentChapters,
                    mappings = currentMappings,
                    catalog = currentCatalog,
                    reader = readerState,
                ),
            )
        }
    }
}

private fun requiredContent(
    entries: List<LibraryEntry>,
    chapters: ObservationState<Set<StoryId>, List<CanonicalChapterGroup>>,
    mappings: ObservationState<Set<StoryId>, List<ContentMapping>>,
    catalog: ObservationState<Set<StoryId>, List<CatalogStoryProjection>>,
    reader: ObservationState<Unit, Set<PluginId>>,
    projector: LibraryActivityProjector,
): ContentState<UpdatesContent> {
    val blockingFailure = chapters.unavailableFailureOrNull()
        ?: mappings.unavailableFailureOrNull()
    if (blockingFailure != null) return ContentState.Failed(blockingFailure)
    if (chapters is ObservationState.Pending || mappings is ObservationState.Pending) {
        return ContentState.Pending
    }

    val chapterValues = (chapters as ObservationState.Available).value
    val mappingValues = (mappings as ObservationState.Available).value
    return ContentState.Ready(
        projectUpdates(
            entries = entries,
            groups = chapterValues,
            mappings = mappingValues,
            projections = catalog.availableValueOrNull(),
            readerPluginIds = reader.availableValueOrNull(),
            projector = projector,
        ),
    )
}

private fun projectUpdates(
    entries: List<LibraryEntry>,
    groups: List<CanonicalChapterGroup>,
    mappings: List<ContentMapping>,
    projections: List<CatalogStoryProjection>?,
    readerPluginIds: Set<PluginId>?,
    projector: LibraryActivityProjector,
): UpdatesContent {
    val activity = projector.project(
        library = entries,
        catalog = projections,
        chapters = groups,
        mappings = mappings,
        readerPluginIds = readerPluginIds,
    )
    return UpdatesContent(
        groups = activity.groupBy { it.publishedAtEpochMillis.dateLabel() }
            .map { (label, items) -> UpdatesGroupUiModel(label, items) },
    )
}

private fun List<LibraryEntry>.storyIdKey(): Set<StoryId> =
    mapTo(linkedSetOf(), LibraryEntry::storyId)

private fun ObservationState<*, *>.unavailableFailureOrNull(): CatalogUiFailure? =
    (this as? ObservationState.Unavailable<*>)?.failure

private fun <K, T> ObservationState<K, T>.availableValueOrNull(): T? = when (this) {
    is ObservationState.Available -> value
    is ObservationState.Pending,
    is ObservationState.Unavailable -> null
}

private fun ObservationState<*, *>.issueOrUnavailable(): CatalogUiFailure? = when (this) {
    is ObservationState.Available -> issue
    is ObservationState.Unavailable -> failure
    is ObservationState.Pending -> null
}

private fun nonBlockingIssue(
    content: ContentState<UpdatesContent>,
    library: ObservationState<Unit, List<LibraryEntry>>,
    chapters: ObservationState<Set<StoryId>, List<CanonicalChapterGroup>>,
    mappings: ObservationState<Set<StoryId>, List<ContentMapping>>,
    catalog: ObservationState<Set<StoryId>, List<CatalogStoryProjection>>,
    reader: ObservationState<Unit, Set<PluginId>>,
): CatalogUiFailure? {
    if (content !is ContentState.Ready) return null
    return library.issueOrUnavailable()
        ?: chapters.issueOrUnavailable()
        ?: mappings.issueOrUnavailable()
        ?: catalog.issueOrUnavailable()
        ?: reader.issueOrUnavailable()
}

private val updateDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC)
private fun Long?.dateLabel(): String = this
    ?.let { updateDateFormatter.format(Instant.ofEpochMilli(it)) }
    ?: "Unknown date"
