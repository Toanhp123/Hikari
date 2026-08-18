package app.openstory.catalog.ui.mapping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.chapters.sync.InitialChapterSyncScheduler
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingCandidate
import app.openstory.library.mapping.ContentMappingSearchReport
import app.openstory.library.mapping.ContentMappingService
import app.openstory.library.matching.ContentMatchExplanation
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = MappingViewModel.Factory::class)
class MappingViewModel @AssistedInject constructor(
    @Assisted private val assistedArgs: MappingAssistedArgs,
    private val mappings: ContentMappingService,
    private val chapterSync: InitialChapterSyncScheduler,
) : ViewModel() {
    private val storyId = assistedArgs.storyId
    private val candidates = MutableStateFlow<List<PendingCandidate>>(emptyList())
    private val urlInput = MutableStateFlow("")
    private val busy = MutableStateFlow(false)
    private val failures = MutableStateFlow<List<String>>(emptyList())

    val state = combine(
        mappings.observe(storyId).catch {
            failures.value = listOf(OBSERVE_FAILURE)
            emit(emptyList())
        },
        candidates,
        urlInput,
        busy,
        failures,
    ) { currentMappings, pending, url, isBusy, currentFailures ->
        MappingUiState(
            loading = false,
            mappings = currentMappings.map(ContentMapping::toUiModel),
            candidates = pending.map(PendingCandidate::toUiModel),
            urlInput = url,
            busy = isBusy,
            failures = currentFailures,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = MappingUiState(),
    )

    fun updateUrl(value: String) {
        urlInput.value = value
        failures.value = emptyList()
    }

    fun search() {
        executeSearch(fromUrl = false) { mappings.searchForReview(storyId) }
    }

    fun resolveUrl() {
        val url = urlInput.value
        executeSearch(fromUrl = true) { mappings.resolveUrl(storyId, url) }
    }

    fun approve(pluginId: PluginId, sourceStoryId: String) {
        val pending = candidates.value.find(pluginId, sourceStoryId) ?: return
        viewModelScope.launch {
            runAction {
                if (pending.fromUrl) {
                    mappings.acceptUrl(storyId, pending.candidate)
                } else {
                    mappings.approve(storyId, pending.candidate)
                }
                chapterSync.schedule(storyId)
                candidates.value = candidates.value - pending
            }
        }
    }

    fun reject(pluginId: PluginId, sourceStoryId: String) {
        val pending = candidates.value.find(pluginId, sourceStoryId) ?: return
        viewModelScope.launch {
            runAction {
                mappings.reject(storyId, pending.candidate)
                candidates.value = candidates.value - pending
            }
        }
    }

    private fun executeSearch(
        fromUrl: Boolean,
        search: suspend () -> ContentMappingSearchReport,
    ) {
        if (busy.value) return
        busy.value = true
        failures.value = emptyList()
        viewModelScope.launch {
            try {
                val report = search()
                candidates.value = report.candidates.map { candidate -> PendingCandidate(candidate, fromUrl) }
                failures.value = report.failures.map { failure -> failure.code }.distinct()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures.value = listOf(SEARCH_FAILURE)
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun runAction(block: suspend () -> Unit) {
        failures.value = emptyList()
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failures.value = listOf(ACTION_FAILURE)
        }
    }

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

private fun PendingCandidate.toUiModel() = MappingCandidateUiModel(
    pluginId = candidate.pluginId,
    sourceStoryId = candidate.sourceStoryId,
    title = candidate.title,
    sourceUrl = candidate.sourceUrl,
    decision = candidate.match.decision,
    score = candidate.match.score,
    evidenceLabels = candidate.match.explanation.evidenceLabels(),
    fromUrl = fromUrl,
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
