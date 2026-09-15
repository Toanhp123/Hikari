package app.openstory.library.feature

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import app.openstory.library.domain.LibraryFilter
import app.openstory.library.domain.LibraryQuery
import app.openstory.library.domain.LibraryWindow
import app.openstory.library.runtime.LibraryQuerySession
import app.openstory.library.runtime.LibraryRuntime
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal interface HomeQuerySession {
    val windows: Flow<LibraryWindow>
    fun update(query: LibraryQuery)
}

internal interface HomeLibraryRuntime : AutoCloseable {
    fun createQuerySession(initialQuery: LibraryQuery): HomeQuerySession
    override fun close() = Unit
}

internal class HomeViewModel(
    private val runtime: HomeLibraryRuntime,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        HomeUiState(
            inputQuery = sanitizeQuery(savedState[QUERY_STATE_KEY] ?: ""),
            filter = savedState.get<String>(FILTER_STATE_KEY)
                ?.let { saved -> LibraryFilter.entries.firstOrNull { it.name == saved } }
                ?: LibraryFilter.ALL,
        ),
    )
    val state: StateFlow<HomeUiState> = mutableState.asStateFlow()

    private val querySession = runtime.createQuerySession(currentQuery())
    private val presenceSession = runtime.createQuerySession(PRESENCE_QUERY)
    private var queryJob: Job? = null
    private var presenceJob: Job? = null
    private var libraryHasEntries: Boolean? = null
    private var quiescent = true

    fun updateInputQuery(inputQuery: String) {
        val boundedInput = sanitizeQuery(inputQuery)
        if (boundedInput == mutableState.value.inputQuery) return
        resetPendingContent()
        savedState[QUERY_STATE_KEY] = boundedInput
        mutableState.update { current -> current.copy(inputQuery = boundedInput, content = HomeContentState.Loading) }
        querySession.update(currentQuery())
        restartFailedQueryIfActive()
    }

    fun selectFilter(filter: LibraryFilter) {
        if (filter == mutableState.value.filter) return
        resetPendingContent()
        savedState[FILTER_STATE_KEY] = filter.name
        mutableState.update { current -> current.copy(filter = filter, content = HomeContentState.Loading) }
        querySession.update(currentQuery())
        restartFailedQueryIfActive()
    }

    fun resume() {
        if (queryJob?.isActive == true) return
        quiescent = false
        querySession.update(currentQuery())
        startQueryObservation()
    }

    private fun startQueryObservation() {
        queryJob = viewModelScope.launch {
            try {
                querySession.windows.collect { window -> publishWindow(window) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") failure: Exception) {
                publish(HomeContentState.Failure)
            }
        }
    }

    fun retry() {
        if (mutableState.value.content != HomeContentState.Failure) return
        mutableState.update { current -> current.copy(content = HomeContentState.Loading) }
        quiesce()
        resume()
    }

    fun quiesce() {
        quiescent = true
        queryJob?.cancel()
        queryJob = null
        presenceJob?.cancel()
        presenceJob = null
    }

    override fun onCleared() {
        runtime.close()
    }

    private fun currentQuery(): LibraryQuery {
        val current = mutableState.value
        return LibraryQuery(
            text = current.inputQuery,
            filter = current.filter,
            after = null,
        )
    }

    private fun publishWindow(window: LibraryWindow) {
        val query = currentQuery()
        when {
            window.items.isNotEmpty() -> {
                stopPresenceObservation()
                publish(HomeContentState.Content(window.items.map { it.toPosterUi() }))
            }
            query.normalizedText.isEmpty() && query.filter == LibraryFilter.ALL -> {
                stopPresenceObservation()
                publish(HomeContentState.LibraryEmpty)
            }
            else -> observeLibraryPresence()
        }
    }

    private fun restartFailedQueryIfActive() {
        if (!quiescent && queryJob?.isActive != true) startQueryObservation()
    }

    private fun observeLibraryPresence() {
        if (presenceJob?.isActive == true) {
            libraryHasEntries?.let { publish(if (it) HomeContentState.NoMatches else HomeContentState.LibraryEmpty) }
            return
        }
        presenceSession.update(PRESENCE_QUERY)
        presenceJob = viewModelScope.launch {
            try {
                presenceSession.windows.collect { window ->
                    libraryHasEntries = window.items.isNotEmpty()
                    val content = if (window.items.isNotEmpty()) {
                        HomeContentState.NoMatches
                    } else {
                        HomeContentState.LibraryEmpty
                    }
                    publish(content)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (@Suppress("SwallowedException", "TooGenericExceptionCaught") failure: Exception) {
                publish(HomeContentState.Failure)
            }
        }
    }

    private fun resetPendingContent() {
        stopPresenceObservation()
    }

    private fun stopPresenceObservation() {
        presenceJob?.cancel()
        presenceJob = null
        libraryHasEntries = null
    }

    private fun publish(content: HomeContentState) {
        mutableState.update { current -> current.copy(content = content) }
    }

    companion object {
        fun factory(createRuntime: () -> HomeLibraryRuntime): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    return HomeViewModel(createRuntime()) as T
                }

                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                    require(modelClass.isAssignableFrom(HomeViewModel::class.java))
                    return HomeViewModel(createRuntime(), extras.createSavedStateHandle()) as T
                }
            }
    }
}

internal class LibraryHomeRuntime(
    private val runtime: LibraryRuntime,
    private val ownsRuntime: Boolean = true,
) : HomeLibraryRuntime {
    override fun createQuerySession(initialQuery: LibraryQuery): HomeQuerySession =
        runtime.createQuerySession(initialQuery).asHomeSession()

    override fun close() {
        if (ownsRuntime) runtime.close()
    }
}

private fun LibraryQuerySession.asHomeSession(): HomeQuerySession = object : HomeQuerySession {
    override val windows: Flow<LibraryWindow> = this@asHomeSession.windows
    override fun update(query: LibraryQuery) = this@asHomeSession.update(query)
}

private val PRESENCE_QUERY = LibraryQuery(
    text = "",
    filter = LibraryFilter.ALL,
    after = null,
    limit = 1,
)

private const val MAX_HOME_QUERY_CHARS = 256
private const val QUERY_STATE_KEY = "home.query"
private const val FILTER_STATE_KEY = "home.filter"

private fun sanitizeQuery(query: String): String =
    query.filterNot(Char::isISOControl).take(MAX_HOME_QUERY_CHARS)
