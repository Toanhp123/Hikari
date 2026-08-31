package app.openstory.catalog.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.ObservationState
import app.openstory.catalog.ui.state.hasIssueOrUnavailable
import app.openstory.catalog.ui.state.hasRetainedIssue
import app.openstory.catalog.ui.state.retainedObservation
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.Clock
import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadRecord
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
import kotlinx.coroutines.flow.flowOf
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

    private val downloadObservation = viewModelScope.retainedObservation(
        key = flowOf(DownloadsObservationKey.DOWNLOADS),
        initialKey = DownloadsObservationKey.DOWNLOADS,
        observe = { repository.observeAll() },
        mapFailure = { _, _ -> CatalogUiFailure("downloads.observe_failed", retryable = true) },
    )

    private val chapterObservation = viewModelScope.retainedObservation(
        key = flowOf(DownloadsObservationKey.CHAPTERS),
        initialKey = DownloadsObservationKey.CHAPTERS,
        observe = { chapters.observeAll() },
        mapFailure = { _, _ -> CatalogUiFailure("downloads.chapters.observe_failed", retryable = true) },
    )

    private val catalogObservation = viewModelScope.retainedObservation(
        key = flowOf(DownloadsObservationKey.CATALOG),
        initialKey = DownloadsObservationKey.CATALOG,
        observe = { catalog.observe() },
        mapFailure = { _, _ -> CatalogUiFailure("downloads.catalog.observe_failed", retryable = true) },
    )

    private val observations = combine(
        downloadObservation.state,
        chapterObservation.state,
        catalogObservation.state,
    ) { downloadsState, chaptersState, catalogState ->
        ObservationBundle(downloadsState, chaptersState, catalogState)
    }

    val state = combine(observations, commandState) { observation, command ->
        val content = when (val downloadsState = observation.downloads) {
            is ObservationState.Pending -> ContentState.Pending
            is ObservationState.Unavailable -> ContentState.Failed(downloadsState.failure)
            is ObservationState.Available -> ContentState.Ready(
                projectDownloads(
                    records = downloadsState.value,
                    groups = observation.chapters.availableValueOrNull(),
                    projections = observation.catalog.availableValueOrNull(),
                ),
            )
        }

        DownloadsUiState(
            content = content,
            pendingRemoval = command.pendingRemoval,
            observationIssue = observation.nonBlockingIssue(content),
            commandFailure = command.failure,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000L),
        DownloadsUiState(),
    )

    fun retryContent() {
        if (downloadObservation.state.value is ObservationState.Unavailable) {
            downloadObservation.retry()
        }
    }

    fun retryObservation() {
        when {
            downloadObservation.state.value.hasRetainedIssue() -> downloadObservation.retry()
            chapterObservation.state.value.hasIssueOrUnavailable() -> chapterObservation.retry()
            catalogObservation.state.value.hasIssueOrUnavailable() -> catalogObservation.retry()
        }
    }

    fun retry(releaseId: ChapterReleaseId) = command {
        service.queue(releaseId, clock.nowEpochMillis())
        scheduler.schedule(releaseId)
    }

    fun cancel(releaseId: ChapterReleaseId) = command {
        scheduler.cancel(releaseId)
        service.cancel(releaseId, clock.nowEpochMillis())
    }

    fun requestRemoval(releaseId: ChapterReleaseId) =
        commandState.update { it.copy(pendingRemoval = releaseId) }

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
            commandState.update {
                it.copy(failure = CatalogUiFailure("downloads.command_failed", retryable = false))
            }
        }
    }

    private data class CommandState(
        val pendingRemoval: ChapterReleaseId? = null,
        val failure: CatalogUiFailure? = null,
    )

    private data class ObservationBundle(
        val downloads: ObservationState<DownloadsObservationKey, List<DownloadRecord>>,
        val chapters: ObservationState<DownloadsObservationKey, List<CanonicalChapterGroup>>,
        val catalog: ObservationState<DownloadsObservationKey, List<CatalogStoryProjection>>,
    ) {
        fun nonBlockingIssue(content: ContentState<DownloadsContent>): CatalogUiFailure? {
            if (content is ContentState.Failed) return null
            return downloads.issueOrUnavailable()
                ?: chapters.issueOrUnavailable()
                ?: catalog.issueOrUnavailable()
        }
    }
}

private enum class DownloadsObservationKey {
    DOWNLOADS,
    CHAPTERS,
    CATALOG,
}

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

private fun projectDownloads(
    records: List<DownloadRecord>,
    groups: List<CanonicalChapterGroup>?,
    projections: List<CatalogStoryProjection>?,
): DownloadsContent {
    val metadata = groups.orEmpty()
        .flatMap { group -> group.releases.map { release -> release.id to (group.chapter to release) } }
        .toMap()
    val stories = projections.orEmpty().associateBy { it.storyId }
    val items = records
        .sortedByDescending { it.updatedAtEpochMillis }
        .map { record ->
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

    return DownloadsContent(
        active = items.filter { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING },
        completed = items.filter { it.state == DownloadState.COMPLETED },
        failed = items.filter { it.state == DownloadState.FAILED },
    )
}
