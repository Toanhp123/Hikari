package app.openstory.library.runtime

import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.library.domain.LibraryEntry
import app.openstory.library.domain.LibraryFilter
import app.openstory.library.domain.LibraryMutationResult
import app.openstory.library.domain.LibraryPort
import app.openstory.library.domain.LibraryPresentationSnapshot
import app.openstory.library.domain.LibraryQuery
import app.openstory.library.domain.LibraryWindow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryQuerySessionTest {
    @Test
    fun staleQueryIsCancelledAndCannotPublishOverNewerInput() = runTest {
        val oldQuery = LibraryQuery("old", LibraryFilter.ALL, null, 10)
        val newQuery = LibraryQuery("new", LibraryFilter.ALL, null, 10)
        val oldWindow = LibraryWindow(emptyList(), null)
        val newWindow = LibraryWindow(emptyList(), null)
        val port = object : EmptyLibraryPort() {
            override fun observeWindow(query: LibraryQuery): Flow<LibraryWindow> = flow {
                if (query == oldQuery) delay(1_000)
                emit(if (query == oldQuery) oldWindow else newWindow)
            }
        }
        val session = LibraryQuerySession(port, oldQuery)
        val received = mutableListOf<LibraryWindow>()
        val collection = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            session.windows.collect(received::add)
        }

        session.update(newQuery)
        advanceUntilIdle()

        assertEquals(1, received.size)
        assertSame(newWindow, received.single())
        collection.cancel()
    }

    private open class EmptyLibraryPort : LibraryPort {
        override fun observeMembership(ref: StorySourceRef): Flow<LibraryEntry?> = flow { emit(null) }
        override fun observeWindow(query: LibraryQuery): Flow<LibraryWindow> = flow { emit(LibraryWindow(emptyList(), null)) }
        override suspend fun add(entry: LibraryEntry) = LibraryMutationResult.NO_OP
        override suspend fun remove(ref: StorySourceRef) = LibraryMutationResult.NO_OP
        override suspend fun enrichSnapshot(ref: StorySourceRef, snapshot: LibraryPresentationSnapshot) =
            LibraryMutationResult.NO_OP
    }
}
