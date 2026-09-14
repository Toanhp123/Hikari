package app.openstory.library.runtime

import app.openstory.library.domain.LibraryPort
import app.openstory.library.domain.LibraryQuery
import app.openstory.library.domain.LibraryWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryQuerySession(
    private val port: LibraryPort,
    initialQuery: LibraryQuery,
) {
    private val queries = MutableStateFlow(initialQuery)

    val windows: Flow<LibraryWindow> = queries.flatMapLatest(port::observeWindow)

    fun update(query: LibraryQuery) {
        queries.value = query
    }
}
