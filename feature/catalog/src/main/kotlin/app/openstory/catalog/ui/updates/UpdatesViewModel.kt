package app.openstory.catalog.ui.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.activity.LibraryActivityProjector
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.library.LibraryService
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.reader.content.ReaderSourceAvailability
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModel @Inject constructor(
    library: LibraryService,
    catalog: CatalogStoryProjectionRepository,
    chapters: ChapterRepository,
    mappings: ContentMappingRepository,
    readerSources: ReaderSourceAvailability,
    private val projector: LibraryActivityProjector,
) : ViewModel() {
    private val observationFailure = MutableStateFlow<String?>(null)
    private val started = SharingStarted.WhileSubscribed(5_000L)
    private val libraryEntries = library.observe()
        .preserveLatest(emptyList())
        .shareIn(viewModelScope, started, replay = 1)
    private val libraryStoryIds = libraryEntries
        .map { entries -> entries.mapTo(linkedSetOf()) { it.storyId } }
        .distinctUntilChanged()
        .shareIn(viewModelScope, started, replay = 1)

    private val readerPluginIds = flow { emit(readerSources.enabledPluginIds()) }
        .preserveLatest(emptySet())

    private val content = combine(
        libraryEntries,
        libraryStoryIds.flatMapLatest { storyIds ->
            catalog.observeForStories(storyIds).preserveLatest(emptyList())
        },
        libraryStoryIds.flatMapLatest { storyIds ->
            chapters.observeForStories(storyIds).preserveLatest(emptyList())
        },
        libraryStoryIds.flatMapLatest { storyIds ->
            mappings.observeForStories(storyIds).preserveLatest(emptyList())
        },
        readerPluginIds,
    ) { entries, projections, groups, links, readerIds ->
        val activity = projector.project(entries, projections, groups, links, readerIds)
        UpdatesUiState(
            groups = activity.groupBy { it.publishedAtEpochMillis.dateLabel() }
                .map { (label, items) -> UpdatesGroupUiModel(label, items) },
            loading = false,
        )
    }

    val state = combine(content, observationFailure) { current, failure -> current.copy(failure = failure) }
        .stateIn(viewModelScope, started, UpdatesUiState())

    private fun <T> Flow<T>.preserveLatest(initial: T): Flow<T> = flow {
        var latest = initial
        try {
            collect { value ->
                latest = value
                emit(value)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            observationFailure.value = "updates.observe_failed"
            emit(latest)
        }
    }
}

private val updateDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneOffset.UTC)
private fun Long?.dateLabel(): String = this
    ?.let { updateDateFormatter.format(Instant.ofEpochMilli(it)) }
    ?: "Unknown date"
