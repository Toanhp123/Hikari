package app.openstory.catalog.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.Clock
import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadScheduler
import app.openstory.downloads.DownloadService
import app.openstory.downloads.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    repository: DownloadRepository,
    private val service: DownloadService,
    private val scheduler: DownloadScheduler,
    chapters: ChapterRepository,
    catalog: CatalogStoryProjectionRepository,
    private val clock: Clock,
) : ViewModel() {
    private val commandState = MutableStateFlow(CommandState())
    private val observationFailure = MutableStateFlow<String?>(null)

    val state = combine(
        repository.observeAll().preserveLatest(emptyList()),
        chapters.observeAll().preserveLatest(emptyList()),
        catalog.observe().preserveLatest(emptyList()),
        combine(commandState, observationFailure) { command, failure -> command to failure },
    ) { records, groups, projections, commandAndFailure ->
        val (command, observationError) = commandAndFailure
        val metadata = groups.flatMap { group -> group.releases.map { it.id to (group.chapter to it) } }.toMap()
        val stories = projections.associateBy { it.storyId }
        val items = records.sortedByDescending { it.updatedAtEpochMillis }.map { record ->
            val releaseMetadata = metadata[record.key.releaseId]
            val chapter = releaseMetadata?.first
            val release = releaseMetadata?.second
            val storyId = release?.storyId ?: chapter?.storyId
            DownloadItemUiModel(
                releaseId = record.key.releaseId,
                storyId = storyId,
                storyTitle = storyId?.let { stories[it]?.title ?: it.value } ?: record.key.releaseId.value,
                chapterLabel = release?.displayLabel ?: chapter?.displayLabel ?: record.key.releaseId.value,
                sourceLabel = release?.pluginId?.value,
                state = record.state,
                sizeBytes = record.sizeBytes,
                failureReason = record.failureReason,
                updatedAtEpochMillis = record.updatedAtEpochMillis,
            )
        }
        DownloadsUiState(
            active = items.filter { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING },
            completed = items.filter { it.state == DownloadState.COMPLETED },
            failed = items.filter { it.state == DownloadState.FAILED },
            pendingRemoval = command.pendingRemoval,
            loading = false,
            failure = command.failure ?: observationError,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), DownloadsUiState())

    fun retry(releaseId: ChapterReleaseId) = command {
        service.queue(releaseId, clock.nowEpochMillis())
        scheduler.schedule(releaseId)
    }

    fun cancel(releaseId: ChapterReleaseId) = command {
        scheduler.cancel(releaseId)
        service.cancel(releaseId, clock.nowEpochMillis())
    }

    fun requestRemoval(releaseId: ChapterReleaseId) = commandState.update { it.copy(pendingRemoval = releaseId) }
    fun dismissRemoval() = commandState.update { it.copy(pendingRemoval = null) }
    fun confirmRemoval() {
        val releaseId = commandState.value.pendingRemoval ?: return
        commandState.update { it.copy(pendingRemoval = null) }
        cancel(releaseId)
    }

    private fun command(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
            commandState.update { it.copy(failure = null) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            commandState.update { it.copy(failure = "downloads.command_failed") }
        }
    }

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
            observationFailure.value = "downloads.observe_failed"
            emit(latest)
        }
    }

    private data class CommandState(val pendingRemoval: ChapterReleaseId? = null, val failure: String? = null)
}
