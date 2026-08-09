package app.openstory.story.ui

import androidx.lifecycle.ViewModel
import app.openstory.catalog.source.CatalogSource
import app.openstory.catalog.source.CatalogSourceRegistry
import app.openstory.catalog.source.CatalogSourceResult
import app.openstory.catalog.source.SourceDetails
import app.openstory.common.AppError
import app.openstory.common.AppResult
import app.openstory.database.repository.CatalogRepository
import app.openstory.database.repository.LocalStoryRepository
import app.openstory.model.CanonicalStory
import app.openstory.model.CatalogEntry
import app.openstory.model.ContentType
import app.openstory.model.PluginId
import app.openstory.model.StoryId
import app.openstory.story.domain.CatalogDetailsMapper
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StoryDetailViewModel private constructor(
    dependencies: StoryDetailDependencies,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel() {
    private val storyFlow = dependencies.storyFlow
    private val enrichAction = dependencies.enrichAction
    private val selectStoryId = dependencies.selectStoryId

    constructor(
        request: StoryDetailRequest,
        storyRepository: LocalStoryRepository,
        catalogRepository: CatalogRepository,
        sources: CatalogSourceRegistry,
        detailsMapper: CatalogDetailsMapper,
    ) : this(
        dependencies = storyDetailDependencies(request, storyRepository, catalogRepository, sources, detailsMapper),
    )

    internal constructor(
        request: StoryDetailRequest,
        storyRepository: LocalStoryRepository,
        catalogRepository: CatalogRepository,
        sources: CatalogSourceRegistry,
        detailsMapper: CatalogDetailsMapper,
        scope: CoroutineScope,
    ) : this(
        dependencies = storyDetailDependencies(request, storyRepository, catalogRepository, sources, detailsMapper),
        scope = scope,
    )

    internal constructor(
        storyFlow: Flow<CanonicalStory?>,
        enrichAction: suspend () -> AppResult<Unit>,
        scope: CoroutineScope,
    ) : this(
        dependencies = StoryDetailDependencies(
            storyFlow = storyFlow,
            enrichAction = { enrichAction().map { null } },
            selectStoryId = {},
        ),
        scope = scope,
    )

    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<AppError?>(null)

    val state = combine(
        storyFlow,
        loading,
        error,
    ) { story, busy, currentError ->
        StoryDetailScreenState(
            story = story?.toStoryDetailStory(),
            loading = busy,
            error = currentError,
        )
    }.stateIn(
        scope = scope,
        started = SharingStarted.Eagerly,
        initialValue = StoryDetailScreenState(),
    )

    init {
        refresh()
    }

    fun refresh() {
        if (loading.value) return

        loading.value = true
        scope.launch {
            try {
                when (val result = enrichAction()) {
                    is AppResult.Success -> {
                        result.value?.let(selectStoryId)
                        error.value = null
                    }
                    is AppResult.Failure -> error.value = result.error
                }
            } finally {
                loading.value = false
            }
        }
    }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}

private data class StoryDetailDependencies(
    val storyFlow: Flow<CanonicalStory?>,
    val enrichAction: suspend () -> AppResult<StoryId?>,
    val selectStoryId: (StoryId) -> Unit,
)

@OptIn(ExperimentalCoroutinesApi::class)
private fun storyDetailDependencies(
    request: StoryDetailRequest,
    storyRepository: LocalStoryRepository,
    catalogRepository: CatalogRepository,
    sources: CatalogSourceRegistry,
    detailsMapper: CatalogDetailsMapper,
): StoryDetailDependencies {
    val selectedStoryId = MutableStateFlow(request.storyId)
    return StoryDetailDependencies(
        storyFlow = selectedStoryId.flatMapLatest(storyRepository::observeStory),
        enrichAction = {
            enrichCatalogSource(
                request = request,
                repository = catalogRepository,
                sources = sources,
                detailsMapper = detailsMapper,
            ).map<StoryId?> { storyId -> storyId }
        },
        selectStoryId = { storyId -> selectedStoryId.value = storyId },
    )
}

data class StoryDetailRequest(
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceId: String,
)

data class StoryDetailScreenState(
    val story: StoryDetailStory? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
)

data class StoryDetailStory(
    val storyId: StoryId,
    val preferredTitle: String,
    val contentType: ContentType,
    val aliases: Set<String>,
    val sources: List<StoryDetailSource>,
)

data class StoryDetailSource(
    val pluginId: PluginId,
    val pluginVersion: String,
    val sourceId: String,
    val sourceUrl: String?,
    val title: String,
    val aliases: Set<String>,
    val authors: Set<String>,
    val description: String?,
    val genres: Set<String>,
    val contentType: ContentType,
    val languageTags: Set<String>,
    val coverReference: String?,
    val publicationStatus: String?,
    val score: Double?,
    val scoreScale: Double?,
    val popularityRank: Long?,
    val fetchedAtEpochMillis: Long,
)

private fun CanonicalStory.toStoryDetailStory(): StoryDetailStory = StoryDetailStory(
    storyId = id,
    preferredTitle = preferredTitle,
    contentType = contentType,
    aliases = aliases,
    sources = catalogEntries
        .sortedWith(
            compareBy<CatalogEntry> { entry -> entry.catalogPluginId.value }
                .thenBy(CatalogEntry::externalStoryId),
        )
        .map(CatalogEntry::toStoryDetailSource),
)

private fun CatalogEntry.toStoryDetailSource(): StoryDetailSource = StoryDetailSource(
    pluginId = catalogPluginId,
    pluginVersion = pluginVersion,
    sourceId = externalStoryId,
    sourceUrl = sourceUrl,
    title = title,
    aliases = aliases,
    authors = authors,
    description = description,
    genres = genres,
    contentType = contentType,
    languageTags = languageTags.map { tag -> tag.value }.toSet(),
    coverReference = coverReference,
    publicationStatus = publicationStatus,
    score = score,
    scoreScale = scoreScale,
    popularityRank = popularityRank,
    fetchedAtEpochMillis = fetchedAtEpochMillis,
)

@Suppress("SwallowedException", "TooGenericExceptionCaught")
private suspend fun enrichCatalogSource(
    request: StoryDetailRequest,
    repository: CatalogRepository,
    sources: CatalogSourceRegistry,
    detailsMapper: CatalogDetailsMapper,
): AppResult<StoryId> = try {
    val source = sources.source(request.pluginId) ?: return AppResult.Failure(
        AppError.Plugin(code = DETAILS_SOURCE_UNAVAILABLE_CODE, retryable = false),
    )
    when (val details = source.details(request.sourceId)) {
        is CatalogSourceResult.Success -> if (details.value.sourceId != request.sourceId) {
            AppResult.Failure(
                AppError.Validation(code = DETAILS_SOURCE_MISMATCH_CODE),
            )
        } else {
            persistDetails(
                source = source,
                details = details.value,
                repository = repository,
                mapper = detailsMapper,
            )
        }
        is CatalogSourceResult.Failure -> AppResult.Failure(
            AppError.Plugin(code = details.failure.code, retryable = details.failure.retryable),
        )
    }
} catch (failure: CancellationException) {
    throw failure
} catch (_: Exception) {
    AppResult.Failure(
        AppError.Plugin(
            code = DETAILS_FAILED_CODE,
            retryable = false,
        ),
    )
}

private suspend fun persistDetails(
    source: CatalogSource,
    details: SourceDetails,
    repository: CatalogRepository,
    mapper: CatalogDetailsMapper,
): AppResult<StoryId> {
    val mapped = mapper.map(source, details)
    return repository.upsertSourceMetadata(
        pluginId = mapped.pluginId,
        pluginVersion = mapped.pluginVersion,
        metadata = mapped.metadata,
    ).map { saved -> saved.storyId }
}

private const val DETAILS_FAILED_CODE = "catalog.details_failed"
private const val DETAILS_SOURCE_MISMATCH_CODE = "catalog.details_source_mismatch"
private const val DETAILS_SOURCE_UNAVAILABLE_CODE = "catalog.source_unavailable"
