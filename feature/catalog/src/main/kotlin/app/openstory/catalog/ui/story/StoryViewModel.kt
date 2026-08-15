package app.openstory.catalog.ui.story

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.details.CatalogDetailsFailure
import app.openstory.catalog.details.CatalogDetailsResult
import app.openstory.catalog.details.CatalogDetailsService
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.catalog.ui.components.ReaderTarget
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = StoryViewModel.Factory::class)
class StoryViewModel @AssistedInject constructor(
    @Assisted private val assistedArgs: StoryAssistedArgs,
    private val repository: CatalogRepository,
    private val details: CatalogDetailsService,
    private val library: LibraryService,
    private val progress: ReadingProgressRepository,
    private val chapters: ChapterRepository,
) : ViewModel() {
    private val storyId = assistedArgs.storyId
    private val selectedSource = MutableStateFlow<StorySourceIdentity?>(null)
    private val refreshing = MutableStateFlow(false)
    private val failure = MutableStateFlow<StoryRefreshFailure?>(null)
    private val selectedSection = MutableStateFlow(StorySection.OVERVIEW)
    private val readableTargets = MutableStateFlow<List<ReaderTarget>>(emptyList())

    init {
        viewModelScope.launch {
            try {
                readableTargets.value = chapters.snapshot(storyId).asReaderTargets(storyId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                readableTargets.value = emptyList()
            }
        }
    }

    private val personal = combine(
        library.observe().preserveLatest(emptyList()),
        progress.observeAll().preserveLatest(emptyList()),
    ) { entries, records -> StoryPersonalState(entries, records) }

    private val catalogState = combine(
        repository.observeStory(storyId).catch {
            failure.value = StoryRefreshFailure(OBSERVE_EXCEPTION_CODE, retryable = true)
            emit(null)
        },
        selectedSource,
        refreshing,
        failure,
    ) { snapshot, selected, busy, currentFailure ->
        val sources = snapshot?.entries.orEmpty().sortedWith(sourceOrder)
        val selectedIdentity = selected?.takeIf { identity -> sources.any { it.matches(identity) } }
            ?: sources.firstOrNull()?.identity()
        StoryCatalogState(snapshot, sources, selectedIdentity, busy, currentFailure)
    }

    val state = combine(
        catalogState,
        personal,
        selectedSection,
        readableTargets,
    ) { catalog, personal, section, targets ->
        StoryUiState(
            storyId = storyId,
            story = catalog.snapshot?.toUiModel(catalog.sources, catalog.selectedIdentity),
            selectedSource = catalog.selectedIdentity,
            refreshing = catalog.refreshing,
            failure = catalog.failure,
            libraryStatus = personal.entries.firstOrNull { it.storyId == storyId }?.status,
            resumeTarget = personal.records.latestResumeTarget(storyId),
            readableTargets = targets,
            selectedSection = section,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = StoryUiState(storyId),
    )

    fun selectSource(pluginId: PluginId, sourceId: String) {
        selectedSource.value = StorySourceIdentity(pluginId, sourceId)
        failure.value = null
    }

    fun selectSection(section: StorySection) {
        selectedSection.value = section
    }

    fun changeLibraryStatus(status: LibraryStatus?) {
        viewModelScope.launch {
            val current = state.value.libraryStatus
            when {
                status == null -> library.remove(storyId)
                current == null -> library.add(storyId, status)
                current != status -> library.changeStatus(storyId, status)
            }
        }
    }

    fun refresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                val snapshot = repository.observeStory(storyId).first()
                val source = snapshot?.entries.orEmpty()
                    .sortedWith(sourceOrder)
                    .selectedEntry(selectedSource.value)
                failure.value = if (source == null) {
                    StoryRefreshFailure(SOURCE_UNAVAILABLE_CODE, retryable = false)
                } else {
                    details.load(source.pluginId, source.sourceId).failureOrNull()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                failure.value = StoryRefreshFailure(REFRESH_EXCEPTION_CODE, retryable = true)
            } finally {
                refreshing.value = false
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(assistedArgs: StoryAssistedArgs): StoryViewModel
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.preserveLatest(initial: T) = flow {
        var latest = initial
        emit(initial)
        try {
            collect { value -> latest = value; emit(value) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emit(latest)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val SOURCE_UNAVAILABLE_CODE = "catalog.story.source_unavailable"
        const val OBSERVE_EXCEPTION_CODE = "catalog.story.observe_exception"
        const val REFRESH_EXCEPTION_CODE = "catalog.story.refresh_exception"
        val sourceOrder = compareBy<CatalogEntry> { it.pluginId.value }.thenBy { it.sourceId }
    }
}

private data class StoryPersonalState(
    val entries: List<LibraryEntry>,
    val records: List<ReadingProgress>,
)

private data class StoryCatalogState(
    val snapshot: StoryCatalogSnapshot?,
    val sources: List<CatalogEntry>,
    val selectedIdentity: StorySourceIdentity?,
    val refreshing: Boolean,
    val failure: StoryRefreshFailure?,
)

data class StoryAssistedArgs(val storyId: StoryId)

private fun List<CatalogEntry>.selectedEntry(identity: StorySourceIdentity?): CatalogEntry? =
    firstOrNull { identity != null && it.matches(identity) } ?: firstOrNull()

private fun CatalogEntry.matches(identity: StorySourceIdentity): Boolean =
    pluginId == identity.pluginId && sourceId == identity.sourceId

private fun CatalogEntry.identity() = StorySourceIdentity(pluginId, sourceId)

private fun StoryCatalogSnapshot.toUiModel(
    sortedSources: List<CatalogEntry>,
    selectedIdentity: StorySourceIdentity?,
): StoryUiModel {
    val selected = sortedSources.firstOrNull { selectedIdentity != null && it.matches(selectedIdentity) }
    val artwork = selected?.takeIf { it.coverUrl != null } ?: sortedSources
        .filter { it.coverUrl != null }
        .maxWithOrNull(compareBy<CatalogEntry> { it.normalizedScore() }
            .thenByDescending { it.pluginId.value }
            .thenByDescending { it.sourceId })
    val metadata = selected ?: sortedSources.firstOrNull()
    return StoryUiModel(
        storyId = story.id,
        preferredTitle = metadata?.title ?: story.id.value,
        contentType = story.contentType,
        aliases = sortedSources.flatMap { it.aliases }.toSet(),
        description = metadata?.description ?: sortedSources.firstNotNullOfOrNull { it.description },
        coverUrl = artwork?.coverUrl,
        score = metadata?.score ?: sortedSources.mapNotNull { it.score }.maxByOrNull { it.value / it.scale },
        authors = sortedSources.flatMap { it.authors }.toSet(),
        genres = sortedSources.flatMap { it.genres }.toSet(),
        languageTags = sortedSources.flatMap { it.languageTags }.toSet(),
        sources = sortedSources,
    )
}

private fun CatalogEntry.normalizedScore(): Double = score?.let { it.value / it.scale } ?: Double.NEGATIVE_INFINITY

private fun List<ReadingProgress>.latestResumeTarget(storyId: StoryId): ReaderTarget? =
    asSequence().filter { it.storyId == storyId && it.completedAtEpochMillis == null }
        .maxWithOrNull(compareBy<ReadingProgress> { it.updatedAtEpochMillis }.thenBy { it.releaseId.value })
        ?.let { ReaderTarget(storyId, it.canonicalChapterId, it.releaseId) }

private fun CatalogDetailsResult.failureOrNull(): StoryRefreshFailure? = when (this) {
    is CatalogDetailsResult.Success -> null
    is CatalogDetailsResult.Failure -> failure.toUiFailure()
}

private fun CatalogDetailsFailure.toUiFailure(): StoryRefreshFailure = when (this) {
    is CatalogDetailsFailure.SourceUnavailable -> StoryRefreshFailure("catalog.source_unavailable", false)
    is CatalogDetailsFailure.SourceFailure -> StoryRefreshFailure(code, retryable)
    is CatalogDetailsFailure.SourceIdMismatch -> StoryRefreshFailure("catalog.details_source_mismatch", false)
    is CatalogDetailsFailure.StoreFailure -> StoryRefreshFailure(code, retryable)
}

private fun ChapterGraphSnapshot.asReaderTargets(storyId: StoryId): List<ReaderTarget> {
    val releasesByChapter = releases.groupBy { release -> release.canonicalChapterId }
    return chapters.filterNot { chapter -> chapter.tombstoned }.flatMap { chapter ->
        releasesByChapter[chapter.id].orEmpty().map { release ->
            ReaderTarget(storyId, chapter.id, release.id)
        }
    }
}
