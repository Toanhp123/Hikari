package app.openstory.catalog.ui.mapping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.ui.state.CatalogUiFailure
import app.openstory.catalog.ui.state.ContentState
import app.openstory.catalog.ui.state.ObservationState
import app.openstory.catalog.ui.state.forExpectedKey
import app.openstory.catalog.ui.state.retainedObservation
import app.openstory.chapters.sync.InitialChapterSyncScheduler
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingCandidate
import app.openstory.library.mapping.ContentMappingSearchReport
import app.openstory.library.mapping.ContentMappingService
import app.openstory.library.mapping.ContentMappingWriteResult
import app.openstory.library.matching.ContentMatchExplanation
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = MappingViewModel.Factory::class)
class MappingViewModel @AssistedInject constructor(
    @Assisted private val assistedArgs: MappingAssistedArgs,
    private val mappings: ContentMappingService,
    private val chapterSync: InitialChapterSyncScheduler,
) : ViewModel() {
    private val storyId = assistedArgs.storyId
    private val commandState = MutableStateFlow(MappingCommandState())
    private val mutableEvents = MutableSharedFlow<MappingEvent>()
    private var searchAttemptSequence = 0L
    private var activeSearchAttempt: MappingSearchAttempt? = null
    private var searchJob: Job? = null

    private val mappingObservation = viewModelScope.retainedObservation(
        key = flowOf(storyId),
        initialKey = storyId,
        observe = { currentStoryId -> mappings.observe(currentStoryId) },
        mapFailure = { _, _ -> CatalogUiFailure(OBSERVE_FAILURE, retryable = true) },
    )

    val events = mutableEvents.asSharedFlow()

    val state = combine(
        mappingObservation.state,
        commandState,
    ) { observation, command ->
        val expectedObservation = observation.forExpectedKey(storyId)
        val currentMappings = (expectedObservation as? ObservationState.Available)?.value
        val mappingsByPlugin = currentMappings.orEmpty().associateBy(ContentMapping::pluginId)
        val content = when (expectedObservation) {
            is ObservationState.Pending -> ContentState.Pending
            is ObservationState.Unavailable -> ContentState.Failed(expectedObservation.failure)
            is ObservationState.Available -> ContentState.Ready(
                expectedObservation.value.map(ContentMapping::toUiModel),
            )
        }
        MappingUiState(
            content = content,
            candidates = if (currentMappings == null) {
                emptyList()
            } else {
                command.candidates
                    .filterNot { pendingCandidate ->
                        mappingsByPlugin[pendingCandidate.candidate.pluginId]
                            ?.sourceStoryId == pendingCandidate.candidate.sourceStoryId
                    }
                    .map { pendingCandidate ->
                        pendingCandidate.toUiModel(mappingsByPlugin[pendingCandidate.candidate.pluginId])
                    }
            },
            urlInput = command.urlInput,
            busy = command.busy,
            observationIssue = (expectedObservation as? ObservationState.Available)?.issue,
            searchFailures = command.searchFailures,
            actionFailure = command.actionFailure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MappingUiState(),
    )

    fun retryObservation() {
        mappingObservation.retry()
    }

    fun updateUrl(value: String) {
        val activeUrlSuperseded = (activeSearchAttempt?.origin as? MappingSearchOrigin.Url)
            ?.let { origin -> origin.value != value } == true
        if (activeUrlSuperseded) {
            activeSearchAttempt = null
            searchJob?.cancel()
            searchJob = null
        }
        commandState.update { current ->
            val urlOutputSuperseded = (current.searchOrigin as? MappingSearchOrigin.Url)
                ?.let { origin -> origin.value != value } == true
            current.copy(
                urlInput = value,
                busy = if (activeUrlSuperseded) false else current.busy,
                candidates = if (urlOutputSuperseded) emptyList() else current.candidates,
                searchFailures = if (urlOutputSuperseded) emptyList() else current.searchFailures,
                searchOrigin = if (urlOutputSuperseded) null else current.searchOrigin,
            )
        }
    }

    fun search() {
        executeSearch(MappingSearchOrigin.Discovery) { mappings.searchForReview(storyId) }
    }

    fun resolveUrl() {
        val url = commandState.value.urlInput
        executeSearch(MappingSearchOrigin.Url(url)) { mappings.resolveUrl(storyId, url) }
    }

    fun approve(pluginId: PluginId, sourceStoryId: String) {
        val command = commandState.value
        val currentMappings = currentMappingsOrNull()
        val pending = command.candidates.find(pluginId, sourceStoryId)
        if (command.busy || currentMappings == null || pending == null) return
        val replacing = currentMappings.any { mapping ->
            mapping.pluginId == pluginId && mapping.sourceStoryId != sourceStoryId
        }
        commandState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runAction {
                val result = if (pending.fromUrl) {
                    mappings.acceptUrl(storyId, pending.candidate)
                } else {
                    mappings.approve(storyId, pending.candidate)
                }
                when (result) {
                    is ContentMappingWriteResult.Written -> {
                        commandState.update { current ->
                            current.copy(
                                candidates = current.candidates.filterNot { candidate ->
                                    candidate.candidate.pluginId == pending.candidate.pluginId
                                },
                            )
                        }
                        if (result.changed) {
                            chapterSync.schedule(storyId)
                            mutableEvents.emit(
                                if (replacing) MappingEvent.SOURCE_REPLACED else MappingEvent.SOURCE_LINKED,
                            )
                        } else {
                            mutableEvents.emit(MappingEvent.SOURCE_ALREADY_LINKED)
                        }
                    }
                    is ContentMappingWriteResult.Protected -> {
                        commandState.update {
                            it.copy(actionFailure = CatalogUiFailure(ACTION_FAILURE, retryable = false))
                        }
                    }
                }
            }
        }
    }

    fun reject(pluginId: PluginId, sourceStoryId: String) {
        if (commandState.value.busy || currentMappingsOrNull() == null) return
        val pending = commandState.value.candidates.find(pluginId, sourceStoryId) ?: return
        commandState.update { it.copy(busy = true) }
        viewModelScope.launch {
            runAction {
                mappings.reject(storyId, pending.candidate)
                commandState.update { it.copy(candidates = it.candidates - pending) }
            }
        }
    }

    private fun executeSearch(
        origin: MappingSearchOrigin,
        search: suspend () -> ContentMappingSearchReport,
    ) {
        if (commandState.value.busy || currentMappingsOrNull() == null) return
        val attempt = MappingSearchAttempt(origin, ++searchAttemptSequence)
        activeSearchAttempt = attempt
        commandState.update {
            it.copy(
                busy = true,
                candidates = emptyList(),
                searchFailures = emptyList(),
                searchOrigin = null,
            )
        }
        searchJob = viewModelScope.launch {
            try {
                val report = search()
                if (activeSearchAttempt == attempt && origin.isCurrent(commandState.value.urlInput)) {
                    commandState.update { current ->
                        current.copy(
                            candidates = report.candidates.map { candidate ->
                                PendingCandidate(candidate, fromUrl = origin is MappingSearchOrigin.Url)
                            },
                            searchFailures = report.failures
                                .map { failure -> CatalogUiFailure(failure.code, failure.retryable) }
                                .distinctBy(CatalogUiFailure::code),
                            searchOrigin = origin,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (activeSearchAttempt == attempt && origin.isCurrent(commandState.value.urlInput)) {
                    commandState.update { current ->
                        current.copy(
                            searchFailures = listOf(
                                CatalogUiFailure(SEARCH_FAILURE, retryable = true),
                            ),
                            searchOrigin = origin,
                        )
                    }
                }
            } finally {
                if (activeSearchAttempt == attempt) {
                    activeSearchAttempt = null
                    searchJob = null
                    commandState.update { it.copy(busy = false) }
                }
            }
        }
    }

    private suspend fun runAction(block: suspend () -> Unit) {
        commandState.update { it.copy(actionFailure = null) }
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            commandState.update {
                it.copy(actionFailure = CatalogUiFailure(ACTION_FAILURE, retryable = true))
            }
        } finally {
            commandState.update { it.copy(busy = false) }
        }
    }

    private fun currentMappingsOrNull(): List<ContentMapping>? =
        when (val observation = mappingObservation.state.value.forExpectedKey(storyId)) {
            is ObservationState.Available -> observation.value
            is ObservationState.Pending,
            is ObservationState.Unavailable -> null
        }

    private data class MappingCommandState(
        val candidates: List<PendingCandidate> = emptyList(),
        val urlInput: String = "",
        val busy: Boolean = false,
        val searchFailures: List<CatalogUiFailure> = emptyList(),
        val actionFailure: CatalogUiFailure? = null,
        val searchOrigin: MappingSearchOrigin? = null,
    )

    @AssistedFactory
    interface Factory {
        fun create(assistedArgs: MappingAssistedArgs): MappingViewModel
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val OBSERVE_FAILURE = "library.mapping.observe_failed"
        const val SEARCH_FAILURE = "library.mapping.search_failed"
        const val ACTION_FAILURE = "library.mapping.action_failed"
    }
}

data class MappingAssistedArgs(val storyId: StoryId)

enum class MappingEvent {
    SOURCE_LINKED,
    SOURCE_REPLACED,
    SOURCE_ALREADY_LINKED,
}

private data class MappingSearchAttempt(
    val origin: MappingSearchOrigin,
    val sequence: Long,
)

private sealed interface MappingSearchOrigin {
    data object Discovery : MappingSearchOrigin

    data class Url(val value: String) : MappingSearchOrigin
}

private fun MappingSearchOrigin.isCurrent(urlInput: String): Boolean =
    when (this) {
        MappingSearchOrigin.Discovery -> true
        is MappingSearchOrigin.Url -> value == urlInput
    }

private data class PendingCandidate(
    val candidate: ContentMappingCandidate,
    val fromUrl: Boolean,
)

private fun List<PendingCandidate>.find(
    pluginId: PluginId,
    sourceStoryId: String,
): PendingCandidate? = firstOrNull { pending ->
    pending.candidate.pluginId == pluginId && pending.candidate.sourceStoryId == sourceStoryId
}

private fun ContentMapping.toUiModel() = MappingItemUiModel(
    pluginId = pluginId,
    sourceStoryId = sourceStoryId,
    origin = origin,
)

private fun PendingCandidate.toUiModel(currentMapping: ContentMapping?) = MappingCandidateUiModel(
    pluginId = candidate.pluginId,
    sourceStoryId = candidate.sourceStoryId,
    title = candidate.title,
    sourceUrl = candidate.sourceUrl,
    decision = candidate.match.decision,
    score = candidate.match.score,
    evidenceLabels = candidate.match.explanation.evidenceLabels(),
    fromUrl = fromUrl,
    replacesSourceStoryId = currentMapping
        ?.sourceStoryId
        ?.takeIf { sourceStoryId -> sourceStoryId != candidate.sourceStoryId },
)

private fun ContentMatchExplanation.evidenceLabels(): List<String> = buildList {
    val currentAuthorSimilarity = authorSimilarity
    if (directEvidence) add("Direct mapping")
    add("Title ${titleSimilarity.asPercent()}")
    when {
        authorConflict -> add("Author conflict")
        currentAuthorSimilarity == null -> add("Author evidence missing")
        else -> add("Authors ${currentAuthorSimilarity.asPercent()}")
    }
    when {
        contentTypeConflict -> add("Content type conflict")
        contentTypeMatch == true -> add("Content type match")
        else -> add("Content type evidence missing")
    }
}

private const val PERCENT_MULTIPLIER = 100.0

private fun Double.asPercent(): String = String.format(Locale.ROOT, "%.0f%%", this * PERCENT_MULTIPLIER)
