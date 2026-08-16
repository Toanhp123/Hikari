package app.openstory.catalog.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.downloads.DownloadRepository
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryService
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
class HomeDashboardViewModel @Inject constructor(
    library: LibraryService,
    catalog: CatalogStoryProjectionRepository,
    progress: ReadingProgressRepository,
    chapters: ChapterRepository,
    mappings: ContentMappingRepository,
    downloads: DownloadRepository,
) : ViewModel() {
    private val projector = HomeDashboardProjector()
    private val failure = MutableStateFlow<HomeDashboardFailure?>(null)
    private val started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS)
    private val libraryEntries = library.observe()
        .preserveLatest(LIBRARY_FAILURE, emptyList())
        .shareIn(viewModelScope, started, replay = 1)
    private val libraryStoryIds = libraryEntries
        .map { entries -> entries.mapTo(linkedSetOf()) { it.storyId } }
        .distinctUntilChanged()
        .shareIn(viewModelScope, started, replay = 1)

    private val libraryContent = combine(
        libraryEntries,
        libraryStoryIds.flatMapLatest { storyIds ->
            catalog.observeForStories(storyIds)
                .preserveLatest(CATALOG_FAILURE, emptyList())
        },
        libraryStoryIds.flatMapLatest { storyIds ->
            progress.observeForStories(storyIds)
                .preserveLatest(PROGRESS_FAILURE, emptyList())
        },
    ) { entries, projections, records -> LibraryContent(entries, projections, records) }

    private val chapterContent = combine(
        libraryStoryIds.flatMapLatest { storyIds ->
            chapters.observeForStories(storyIds)
                .preserveLatest(CHAPTERS_FAILURE, emptyList())
        },
        libraryStoryIds.flatMapLatest { storyIds ->
            mappings.observeForStories(storyIds)
                .preserveLatest(MAPPINGS_FAILURE, emptyList())
        },
        downloads.observeCompletedCount().preserveLatest(DOWNLOADS_FAILURE, 0),
    ) { groups, links, downloadedCount -> ChapterContent(groups, links, downloadedCount) }

    private val content = combine(libraryContent, chapterContent) { personal, releases ->
        projector.project(
            HomeDashboardInput(
                library = personal.library,
                catalog = personal.catalog,
                progress = personal.progress,
                chapters = releases.chapters,
                mappings = releases.mappings,
                downloadedCount = releases.downloadedCount,
            ),
        )
    }

    val state = combine(content, failure) { dashboard, observationFailure ->
        dashboard.copy(failure = observationFailure)
    }.stateIn(
        scope = viewModelScope,
        started = started,
        initialValue = HomeDashboardUiState(),
    )

    private fun <T> Flow<T>.preserveLatest(code: String, initial: T): Flow<T> = flow {
        var latest = initial
        try {
            collect { value ->
                latest = value
                emit(value)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            failure.value = HomeDashboardFailure(code, retryable = true)
            emit(latest)
        }
    }

    private data class LibraryContent(
        val library: List<LibraryEntry>,
        val catalog: List<CatalogStoryProjection>,
        val progress: List<ReadingProgress>,
    )

    private data class ChapterContent(
        val chapters: List<CanonicalChapterGroup>,
        val mappings: List<ContentMapping>,
        val downloadedCount: Int,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val LIBRARY_FAILURE = "home.library.observe_exception"
        const val CATALOG_FAILURE = "home.catalog.observe_exception"
        const val PROGRESS_FAILURE = "home.progress.observe_exception"
        const val CHAPTERS_FAILURE = "home.chapters.observe_exception"
        const val MAPPINGS_FAILURE = "home.mappings.observe_exception"
        const val DOWNLOADS_FAILURE = "home.downloads.observe_exception"
    }
}
