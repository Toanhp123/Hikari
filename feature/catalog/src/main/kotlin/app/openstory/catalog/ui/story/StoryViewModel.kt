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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = StoryViewModel.Factory::class)
class StoryViewModel @AssistedInject constructor(
    @Assisted private val assistedArgs: StoryAssistedArgs,
    private val repository: CatalogRepository,
    private val details: CatalogDetailsService,
) : ViewModel() {
    private val storyId = assistedArgs.storyId
    private val selectedSource = MutableStateFlow<StorySourceIdentity?>(null)
    private val refreshing = MutableStateFlow(false)
    private val failure = MutableStateFlow<StoryRefreshFailure?>(null)

    val state = combine(
        repository.observeStory(storyId).catch {
            failure.value = StoryRefreshFailure(OBSERVE_EXCEPTION_CODE, retryable = true)
            emit(null)
        },
        selectedSource,
        refreshing,
        failure,
    ) { snapshot, selected, busy, currentFailure ->
        val sources = snapshot?.entries.orEmpty().sortedWith(sourceOrder)
        StoryUiState(
            storyId = storyId,
            story = snapshot?.toUiModel(sources),
            selectedSource = selected?.takeIf { identity -> sources.any { it.matches(identity) } }
                ?: sources.firstOrNull()?.identity(),
            refreshing = busy,
            failure = currentFailure,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = StoryUiState(storyId),
    )

    fun selectSource(pluginId: PluginId, sourceId: String) {
        selectedSource.value = StorySourceIdentity(pluginId, sourceId)
        failure.value = null
    }

    fun retry() {
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

    private companion object {
        const val SOURCE_UNAVAILABLE_CODE = "catalog.story.source_unavailable"
        const val OBSERVE_EXCEPTION_CODE = "catalog.story.observe_exception"
        const val REFRESH_EXCEPTION_CODE = "catalog.story.refresh_exception"
        val sourceOrder = compareBy<CatalogEntry> { it.pluginId.value }.thenBy { it.sourceId }
    }
}

data class StoryAssistedArgs(val storyId: StoryId)

private fun List<CatalogEntry>.selectedEntry(identity: StorySourceIdentity?): CatalogEntry? =
    firstOrNull { identity != null && it.matches(identity) } ?: firstOrNull()

private fun CatalogEntry.matches(identity: StorySourceIdentity): Boolean =
    pluginId == identity.pluginId && sourceId == identity.sourceId

private fun CatalogEntry.identity() = StorySourceIdentity(pluginId, sourceId)

private fun StoryCatalogSnapshot.toUiModel(sortedSources: List<CatalogEntry>) = StoryUiModel(
    storyId = story.id,
    preferredTitle = sortedSources.firstOrNull()?.title ?: story.id.value,
    contentType = story.contentType,
    aliases = sortedSources.flatMap { it.aliases }.toSet(),
    sources = sortedSources,
)

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
