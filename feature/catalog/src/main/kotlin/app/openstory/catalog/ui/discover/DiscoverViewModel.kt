package app.openstory.catalog.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.openstory.catalog.home.CatalogRefreshResult
import app.openstory.catalog.home.CatalogRefreshService
import app.openstory.catalog.model.CatalogHomeSnapshot
import app.openstory.catalog.model.ContentType
import app.openstory.catalog.repository.CatalogRepository
import app.openstory.common.id.PluginId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    repository: CatalogRepository,
    refreshService: CatalogRefreshService,
    projection: DiscoverProjectionPipeline,
) : ViewModel() {
    private val observationFailure = MutableStateFlow<DiscoverUiFailure?>(null)
    private val refreshFailure = MutableStateFlow<DiscoverUiFailure?>(null)
    private val projection = projection
    private val homes = repository.observeHomes()
        .preserveLatestOnFailure(HOME_OBSERVE_EXCEPTION_CODE, emptyList())
        .shareIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            replay = 1,
        )
    private val dependencies = DiscoverDependencies(
        homes = homes,
        content = homes
            .map(projection::prepare)
            .preserveLatestOnFailure(
                RANKING_OBSERVE_EXCEPTION_CODE,
                DiscoverPreparedContent(emptyList()),
            ),
        refresh = {
            val cachedHomes = homes.first()
            refreshService.refresh().toReport(cachedHomes)
        },
    )
    private val selectedContentType = MutableStateFlow(ContentType.MANGA)
    private val initialLoading = MutableStateFlow(true)
    private val selectedCatalogId = MutableStateFlow<PluginId?>(null)
    private val selectedSourceId = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val refreshReport = MutableStateFlow<DiscoverRefreshReport?>(null)
    private var bootstrapAttempted = false

    init {
        bootstrapEmptyCache()
    }

    private val legacySelectionState = combine(
        selectedCatalogId,
        selectedSourceId,
    ) { pluginId, sourceId ->
        LegacyDiscoverSelection(pluginId, sourceId)
    }

    private val presentationSelectionState = combine(
        legacySelectionState,
        selectedContentType,
        initialLoading,
    ) { legacySelection, contentType, loading ->
        DiscoverPresentationSelection(
            legacy = legacySelection,
            contentType = contentType,
            loading = loading,
        )
    }

    private val contentState = combine(
        dependencies.content,
        presentationSelectionState,
        refreshing,
        refreshReport,
    ) { content, selection, busy, report ->
        projection.project(
            content = content,
            selectedContentType = selection.contentType,
            loading = selection.loading && content.homes.isEmpty(),
            refreshing = busy,
            refreshReport = report,
            legacySelectedCatalogId = selection.legacy.pluginId,
            legacySelectedSourceId = selection.legacy.sourceId,
        )
    }

    val state = combine(contentState, observationFailure, refreshFailure) { content, observation, refresh ->
        content.copy(
            observationFailure = observation,
            refreshFailure = refresh,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DiscoverUiState(),
    )

    private fun bootstrapEmptyCache() {
        viewModelScope.launch {
            if (bootstrapAttempted) return@launch
            bootstrapAttempted = true
            val cachedHomes = dependencies.homes.first()
            if (cachedHomes.isEmpty() && observationFailure.value == null) {
                performRefresh()
            }
            initialLoading.value = false
        }
    }

    fun refresh() {
        viewModelScope.launch { performRefresh() }
    }

    private suspend fun performRefresh() {
        if (refreshing.value) return
        refreshing.value = true
        try {
            refreshReport.value = dependencies.refresh()
            refreshFailure.value = null
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            refreshFailure.value = DiscoverUiFailure(REFRESH_EXCEPTION_CODE, retryable = true)
        } finally {
            refreshing.value = false
        }
    }

    fun selectContentType(contentType: ContentType) {
        if (contentType != ContentType.MANGA) return
        selectedContentType.value = contentType
    }

    // Transitional callbacks keep the pre-redesign DiscoverScreen functional until Task 7
    // replaces source/category selection with the media selector.
    fun selectCatalog(pluginId: PluginId) {
        selectedCatalogId.value = pluginId
        selectedSourceId.value = null
    }

    fun selectCategory(category: DiscoverQuickCategory) {
        selectedCatalogId.value = category.pluginId
        selectedSourceId.value = category.sourceId
    }

    fun selectCombined() {
        selectedCatalogId.value = null
        selectedSourceId.value = null
    }

    private data class DiscoverDependencies(
        val homes: Flow<List<CatalogHomeSnapshot>>,
        val content: Flow<DiscoverPreparedContent>,
        val refresh: suspend () -> DiscoverRefreshReport,
    )

    private data class LegacyDiscoverSelection(
        val pluginId: PluginId?,
        val sourceId: String?,
    )

    private data class DiscoverPresentationSelection(
        val legacy: LegacyDiscoverSelection,
        val contentType: ContentType,
        val loading: Boolean,
    )

    private fun <T> Flow<T>.preserveLatestOnFailure(code: String, initial: T): Flow<T> = flow {
        var latest = initial
        try {
            this@preserveLatestOnFailure.collect { value ->
                latest = value
                emit(value)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            observationFailure.value = DiscoverUiFailure(code, retryable = true)
            emit(latest)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val HOME_OBSERVE_EXCEPTION_CODE = "catalog.home.observe_exception"
        const val RANKING_OBSERVE_EXCEPTION_CODE = "catalog.home.ranking_exception"
        const val REFRESH_EXCEPTION_CODE = "catalog.home.refresh_exception"
    }
}

private fun List<CatalogRefreshResult>.toReport(
    homes: List<CatalogHomeSnapshot>,
): DiscoverRefreshReport {
    val refreshedAt = homes.associate { it.pluginId to it.refreshedAtEpochMillis }
    return fold(DiscoverRefreshReport(refreshedAtEpochMillis = refreshedAt)) { report, result ->
        when (result) {
            is CatalogRefreshResult.Success -> report.copy(
                succeeded = report.succeeded + result.pluginId,
                refreshedAtEpochMillis = report.refreshedAtEpochMillis +
                    (result.pluginId to result.refreshedAtEpochMillis),
            )
            is CatalogRefreshResult.SourceFailure -> report.copy(
                failed = report.failed + (result.pluginId to result.failure.code),
            )
            is CatalogRefreshResult.StoreFailure -> report.copy(
                failed = report.failed + (result.pluginId to result.failure.code),
            )
        }
    }
}
