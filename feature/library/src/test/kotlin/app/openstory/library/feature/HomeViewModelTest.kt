package app.openstory.library.feature

import androidx.lifecycle.SavedStateHandle
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.library.domain.LibraryEntry
import app.openstory.library.domain.LibraryFilter
import app.openstory.library.domain.LibraryPresentationSnapshot
import app.openstory.library.domain.LibraryQuery
import app.openstory.library.domain.LibraryWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun trueEmptyAndFilteredEmptyRemainDistinct() = runTest(dispatcher) {
        val runtime = FakeHomeLibraryRuntime()
        val viewModel = HomeViewModel(runtime)

        viewModel.resume()
        advanceUntilIdle()
        runtime.main.emit(emptyList())
        advanceUntilIdle()
        assertEquals(HomeContentState.LibraryEmpty, viewModel.state.value.content)

        runtime.presence.emit(listOf(entry("saved", CatalogMediaType.MANGA, 20)))
        viewModel.selectFilter(LibraryFilter.LIGHT_NOVEL)
        advanceUntilIdle()
        runtime.main.emit(emptyList())
        advanceUntilIdle()

        assertEquals(HomeContentState.NoMatches, viewModel.state.value.content)
    }

    @Test
    fun retainedHomeQuiescesAndResumesLatestBoundedQuery() = runTest(dispatcher) {
        val runtime = FakeHomeLibraryRuntime()
        val viewModel = HomeViewModel(runtime)

        viewModel.resume()
        advanceUntilIdle()
        assertEquals(1, runtime.main.collectors.value)

        viewModel.updateInputQuery("alchemy")
        viewModel.selectFilter(LibraryFilter.LIGHT_NOVEL)
        advanceUntilIdle()
        assertEquals(
            LibraryQuery("alchemy", LibraryFilter.LIGHT_NOVEL, after = null, limit = 60),
            runtime.main.latestQuery,
        )

        viewModel.quiesce()
        advanceUntilIdle()
        assertEquals(0, runtime.main.collectors.value)

        viewModel.resume()
        advanceUntilIdle()
        assertEquals(1, runtime.main.collectors.value)
        assertEquals("alchemy", viewModel.state.value.inputQuery)
        assertEquals(LibraryFilter.LIGHT_NOVEL, viewModel.state.value.filter)
        assertEquals(runtime.main.latestQuery, runtime.main.queries.last())
    }

    @Test
    fun contentKeepsStorageOrderAndMapsLibrarySemantics() = runTest(dispatcher) {
        val runtime = FakeHomeLibraryRuntime()
        val viewModel = HomeViewModel(runtime)
        val newest = entry("newest", CatalogMediaType.MANGA, 30)
        val older = entry("older", CatalogMediaType.LIGHT_NOVEL, 10)

        viewModel.resume()
        advanceUntilIdle()
        runtime.main.emit(listOf(newest, older))
        advanceUntilIdle()

        val content = viewModel.state.value.content as HomeContentState.Content
        assertEquals(listOf("newest", "older"), content.stories.map { it.title })
        assertEquals(listOf(30L, 10L), content.stories.map { it.savedAtEpochMs })
        assertTrue(content.stories.first().originMediaContext == CatalogMediaType.MANGA)
    }

    @Test
    fun restoredQueryAndFilterSeedTheFirstBoundedObservation() = runTest(dispatcher) {
        val runtime = FakeHomeLibraryRuntime()
        val viewModel = HomeViewModel(
            runtime = runtime,
            savedState = SavedStateHandle(
                mapOf(
                    "home.query" to "restored",
                    "home.filter" to LibraryFilter.MANGA.name,
                ),
            ),
        )

        assertEquals("restored", viewModel.state.value.inputQuery)
        assertEquals(LibraryFilter.MANGA, viewModel.state.value.filter)
        assertEquals(
            LibraryQuery("restored", LibraryFilter.MANGA, after = null, limit = 60),
            runtime.main.latestQuery,
        )
    }

    @Test
    fun malformedRestoredQueryIsSanitizedBeforeCreatingTheStorageSession() = runTest(dispatcher) {
        val runtime = FakeHomeLibraryRuntime()
        val restored = "x".repeat(300) + "\u0000"

        val viewModel = HomeViewModel(
            runtime = runtime,
            savedState = SavedStateHandle(mapOf("home.query" to restored)),
        )

        assertEquals("x".repeat(256), viewModel.state.value.inputQuery)
        assertEquals("x".repeat(256), runtime.main.latestQuery.text)
    }

    @Test
    fun localQueryFailureIsNotPublishedAsEmptyAndCanBeRetried() = runTest(dispatcher) {
        val runtime = object : HomeLibraryRuntime {
            var mainAttempts = 0

            override fun createQuerySession(
                initialQuery: LibraryQuery,
            ): HomeQuerySession = if (initialQuery.limit == 1) {
                object : HomeQuerySession {
                    override val windows: Flow<LibraryWindow> = flow { emit(LibraryWindow(emptyList(), null)) }
                    override fun update(query: LibraryQuery) = Unit
                }
            } else {
                object : HomeQuerySession {
                    override val windows: Flow<LibraryWindow> = flow {
                        mainAttempts += 1
                        if (mainAttempts == 1) error("database unavailable")
                        emit(LibraryWindow(emptyList(), null))
                    }

                    override fun update(query: LibraryQuery) = Unit
                }
            }
        }
        val viewModel = HomeViewModel(runtime)

        viewModel.resume()
        advanceUntilIdle()
        assertEquals(HomeContentState.Failure, viewModel.state.value.content)

        viewModel.retry()
        advanceUntilIdle()
        assertEquals(HomeContentState.LibraryEmpty, viewModel.state.value.content)
    }

    @Test
    fun changingTheQueryAfterFailureStartsTheNewLocalObservation() = runTest(dispatcher) {
        val runtime = object : HomeLibraryRuntime {
            var mainAttempts = 0

            override fun createQuerySession(
                initialQuery: LibraryQuery,
            ): HomeQuerySession = if (initialQuery.limit == 1) {
                object : HomeQuerySession {
                    override val windows: Flow<LibraryWindow> = flow { emit(LibraryWindow(emptyList(), null)) }
                    override fun update(query: LibraryQuery) = Unit
                }
            } else {
                object : HomeQuerySession {
                    override val windows: Flow<LibraryWindow> = flow {
                        mainAttempts += 1
                        if (mainAttempts == 1) error("database unavailable")
                        emit(LibraryWindow(emptyList(), null))
                    }

                    override fun update(query: LibraryQuery) = Unit
                }
            }
        }
        val viewModel = HomeViewModel(runtime)

        viewModel.resume()
        advanceUntilIdle()
        viewModel.updateInputQuery("new query")
        advanceUntilIdle()

        assertEquals(HomeContentState.LibraryEmpty, viewModel.state.value.content)
        assertEquals(2, runtime.mainAttempts)
    }

    private class FakeHomeLibraryRuntime : HomeLibraryRuntime {
        val main = FakeHomeQuerySession()
        val presence = FakeHomeQuerySession()
        private var sessionCount = 0

        override fun createQuerySession(initialQuery: LibraryQuery): HomeQuerySession {
            val session = if (sessionCount++ == 0) main else presence
            session.update(initialQuery)
            return session
        }
    }

    private class FakeHomeQuerySession : HomeQuerySession {
        private val mutableWindows = MutableSharedFlow<LibraryWindow>(replay = 1).also {
            it.tryEmit(LibraryWindow(emptyList(), null))
        }
        val collectors = MutableStateFlow(0)
        val queries = mutableListOf<LibraryQuery>()
        val latestQuery: LibraryQuery
            get() = queries.last()

        override val windows: Flow<LibraryWindow> = kotlinx.coroutines.flow.flow {
            collectors.value += 1
            try {
                mutableWindows.collect(::emit)
            } finally {
                collectors.value -= 1
            }
        }

        override fun update(query: LibraryQuery) {
            queries += query
        }

        fun emit(items: List<LibraryEntry>) {
            check(mutableWindows.tryEmit(LibraryWindow(items, null)))
        }
    }

    private companion object {
        val SOURCE = CatalogSourceKey("home-test")

        fun entry(id: String, media: CatalogMediaType, savedAt: Long): LibraryEntry {
            val ref = StorySourceRef(
                storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE, id)),
                catalogSourceKey = SOURCE,
                sourceStoryId = id,
            )
            return LibraryEntry(
                ref = ref,
                originMediaContext = media,
                savedAtEpochMs = savedAt,
                snapshot = LibraryPresentationSnapshot(
                    title = id,
                    artwork = null,
                    supportingText = media.name,
                ),
            )
        }
    }
}
