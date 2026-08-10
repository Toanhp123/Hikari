package app.openstory.catalog.ui.library

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.common.Clock
import app.openstory.common.id.StoryId
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun metadataOnlyEntryRemainsVisibleWithoutContentMapping() = runTest(dispatcher.scheduler) {
        val repository = FakeLibraryRepository(
            listOf(entry("story-1", LibraryStatus.WANT_TO_READ, addedAt = 10L, updatedAt = 10L)),
        )
        val viewModel = viewModel(repository, projection("story-1", "Fixture Novel"))
        runCurrent()

        val item = viewModel.state.value.items.single()
        assertEquals(StoryId("story-1"), item.storyId)
        assertEquals("Fixture Novel", item.title)
        assertEquals(LibrarySourceState.NO_MAPPING, item.sourceState)
    }

    @Test
    fun missingCatalogProjectionStillKeepsMembershipVisible() = runTest(dispatcher.scheduler) {
        val repository = FakeLibraryRepository(
            listOf(entry("story-orphan", LibraryStatus.READING, addedAt = 10L, updatedAt = 20L)),
        )
        val viewModel = viewModel(repository)
        runCurrent()

        val item = viewModel.state.value.items.single()
        assertEquals(StoryId("story-orphan"), item.storyId)
        assertEquals("story-orphan", item.title)
        assertEquals(null, item.contentType)
    }

    @Test
    fun statusFilteringIsLocalAndImmediate() = runTest(dispatcher.scheduler) {
        val repository = FakeLibraryRepository(
            listOf(
                entry("story-a", LibraryStatus.READING, 1L, 1L),
                entry("story-b", LibraryStatus.COMPLETED, 2L, 2L),
            ),
        )
        val viewModel = viewModel(
            repository,
            projection("story-a", "Alpha"),
            projection("story-b", "Beta"),
        )
        runCurrent()

        viewModel.selectStatus(LibraryStatus.READING)
        runCurrent()

        assertEquals(LibraryStatus.READING, viewModel.state.value.selectedStatus)
        assertEquals(listOf(StoryId("story-a")), viewModel.state.value.items.map { it.storyId })
    }

    @Test
    fun sortingUsesStableStoryIdentityAsTieBreaker() = runTest(dispatcher.scheduler) {
        val repository = FakeLibraryRepository(
            listOf(
                entry("story-b", LibraryStatus.READING, 10L, 20L),
                entry("story-a", LibraryStatus.READING, 10L, 20L),
            ),
        )
        val viewModel = viewModel(
            repository,
            projection("story-a", "Same"),
            projection("story-b", "Same"),
        )
        runCurrent()

        viewModel.selectSort(LibrarySort.TITLE)
        runCurrent()

        assertEquals(
            listOf(StoryId("story-a"), StoryId("story-b")),
            viewModel.state.value.items.map { it.storyId },
        )
    }

    @Test
    fun viewModelDoesNotDependOnRoomOrPluginRuntime() {
        val dependencies = LibraryViewModel::class.java.declaredConstructors
            .flatMap { it.parameterTypes.map(Class<*>::getName) }

        assertTrue(dependencies.none { "storage.room" in it || "plugins.runtime" in it })
    }

    private fun viewModel(
        libraryRepository: FakeLibraryRepository,
        vararg projections: CatalogStoryProjection,
    ) = LibraryViewModel(
        library = LibraryService(libraryRepository, Clock { 100L }),
        catalog = FakeProjectionRepository(projections.toList()),
    )
}

private class FakeLibraryRepository(initial: List<LibraryEntry>) : LibraryRepository {
    private val entries = MutableStateFlow(initial)

    override fun observe(): Flow<List<LibraryEntry>> = entries
    override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long): LibraryEntry = error("unused")
    override suspend fun remove(storyId: StoryId) = Unit
    override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long): LibraryEntry? = error("unused")
}

private class FakeProjectionRepository(initial: List<CatalogStoryProjection>) : CatalogStoryProjectionRepository {
    private val projections = MutableStateFlow(initial)
    override fun observe(): Flow<List<CatalogStoryProjection>> = projections
}

private fun entry(
    id: String,
    status: LibraryStatus,
    addedAt: Long,
    updatedAt: Long,
) = LibraryEntry(StoryId(id), status, addedAt, updatedAt)

private fun projection(id: String, title: String) = CatalogStoryProjection(
    storyId = StoryId(id),
    title = title,
    contentType = ContentType.WEB_NOVEL,
    coverUrl = null,
)
