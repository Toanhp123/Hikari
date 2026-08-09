package app.openstory.story.ui

import androidx.lifecycle.ViewModel
import app.openstory.catalog.details.CatalogDetailsFailure
import app.openstory.catalog.details.CatalogDetailsResult
import app.openstory.catalog.details.CatalogDetailsService
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.model.CatalogEntry
import app.openstory.catalog.model.StoryCatalogSnapshot
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StoryDetailViewModel private constructor(
    private val dependencies: StoryDetailDependencies,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel() {
    constructor(
        request: StoryDetailRequest,
        catalogRepository: CatalogRepository,
        detailsService: CatalogDetailsService,
    ) : this(storyDetailDependencies(request, catalogRepository, detailsService))

    internal constructor(
        storyFlow: Flow<StoryCatalogSnapshot?>,
        enrichAction: suspend () -> AppResult<Unit>,
        scope: CoroutineScope,
    ) : this(StoryDetailDependencies(storyFlow, enrichAction), scope)

    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)

    val state = combine(storyFlow, loading, error) { story, busy, currentError ->
        StoryDetailScreenState(story?.toStoryDetailStory(), busy, currentError)
    }.stateIn(scope, SharingStarted.Eagerly, StoryDetailScreenState())

    init { refresh() }

    fun refresh() {
        if (loading.value) return
        loading.value = true
        scope.launch {
            try {
                when (val result = dependencies.enrichAction()) {
                    is AppResult.Success -> error.value = null
                    is AppResult.Failure -> error.value = result.error
                }
            } finally { loading.value = false }
        }
    }

    override fun onCleared() { scope.cancel(); super.onCleared() }

    private val storyFlow get() = dependencies.storyFlow
}

private data class StoryDetailDependencies(
    val storyFlow: Flow<StoryCatalogSnapshot?>,
    val enrichAction: suspend () -> AppResult<Unit>,
)

private fun storyDetailDependencies(
    request: StoryDetailRequest,
    repository: CatalogRepository,
    detailsService: CatalogDetailsService,
) = StoryDetailDependencies(
    storyFlow = repository.observeStory(request.storyId),
    enrichAction = {
        when (val result = detailsService.load(request.pluginId, request.sourceId)) {
            is CatalogDetailsResult.Success -> AppResult.Success(Unit)
            is CatalogDetailsResult.Failure -> AppResult.Failure(result.failure.toAppError())
        }
    },
)

private fun CatalogDetailsFailure.toAppError(): AppError = when (this) {
    is CatalogDetailsFailure.SourceUnavailable -> AppError.Plugin("catalog.source_unavailable", false)
    is CatalogDetailsFailure.SourceFailure -> AppError.Plugin(code, retryable)
    is CatalogDetailsFailure.SourceIdMismatch -> AppError.Validation("catalog.details_source_mismatch")
    is CatalogDetailsFailure.StoreFailure -> AppError.Storage(code, retryable)
}

data class StoryDetailRequest(val storyId: StoryId, val pluginId: PluginId, val sourceId: String)
data class StoryDetailScreenState(val story: StoryDetailStory? = null, val loading: Boolean = false, val error: AppError? = null)
data class StoryDetailStory(val storyId: StoryId, val preferredTitle: String, val contentType: ContentType, val aliases: Set<String>, val sources: List<StoryDetailSource>)
data class StoryDetailSource(
    val pluginId: PluginId, val sourceId: String, val sourceUrl: String?, val title: String,
    val aliases: Set<String>, val authors: Set<String>, val description: String?, val genres: Set<String>,
    val contentType: ContentType, val languageTags: Set<String>, val coverReference: String?,
    val score: Double?, val scoreScale: Double?, val popularityRank: Long?,
)

private fun StoryCatalogSnapshot.toStoryDetailStory() = StoryDetailStory(
    storyId = story.id,
    preferredTitle = entries.minByOrNull { it.pluginId.value }?.title ?: story.id.value,
    contentType = story.contentType,
    aliases = entries.flatMap { it.aliases }.toSet(),
    sources = entries.sortedWith(compareBy<CatalogEntry> { it.pluginId.value }.thenBy { it.sourceId }).map { it.toSource() },
)

private fun CatalogEntry.toSource() = StoryDetailSource(
    pluginId, sourceId, sourceUrl, title, aliases, authors, description, genres,
    contentType, languageTags, coverUrl, score?.value, score?.scale, popularityRank,
)
