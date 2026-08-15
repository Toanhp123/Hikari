package app.openstory.catalog.ui.dashboard

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterGraphSnapshot
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.chapters.repository.ChapterSyncState
import app.openstory.common.Clock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.DownloadRecord
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadState
import app.openstory.library.LibraryEntry
import app.openstory.library.LibraryMappingScheduler
import app.openstory.library.LibraryRepository
import app.openstory.library.LibraryService
import app.openstory.library.LibraryStatus
import app.openstory.library.mapping.ContentMapping
import app.openstory.library.mapping.ContentMappingOrigin
import app.openstory.library.mapping.ContentMappingRejection
import app.openstory.library.mapping.ContentMappingRepository
import app.openstory.library.mapping.ContentMappingWriteResult
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class HomeDashboardViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun combinesPersonalDataStreamsIntoDashboard() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertFalse(viewModel.state.value.loading)
        assertEquals("Fixture Novel", viewModel.state.value.reading.single().title)
        assertEquals(1, viewModel.state.value.summary.libraryCount)
    }

    @Test
    fun observationFailureKeepsLatestContentAndExposesNonBlockingFailure() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.catalogFlow = flow {
            emit(fixtures.catalog.value)
            throw IllegalStateException("offline")
        }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals("Fixture Novel", viewModel.state.value.reading.single().title)
        assertNotNull(viewModel.state.value.failure)
        assertFalse(viewModel.state.value.loading)
    }
}

private class Fixtures {
    val storyId = StoryId("story-1")
    val library = MutableStateFlow(listOf(LibraryEntry(storyId, LibraryStatus.READING, 1L, 2L)))
    val catalog = MutableStateFlow(listOf(CatalogStoryProjection(storyId, "Fixture Novel", ContentType.WEB_NOVEL, null)))
    var catalogFlow: Flow<List<CatalogStoryProjection>> = catalog
    val progress = MutableStateFlow<List<ReadingProgress>>(emptyList())
    val chapters = MutableStateFlow<List<CanonicalChapterGroup>>(emptyList())
    val mappings = MutableStateFlow<List<ContentMapping>>(emptyList())
    val downloads = MutableStateFlow<List<DownloadRecord>>(emptyList())

    fun viewModel() = HomeDashboardViewModel(
        library = LibraryService(FakeLibraryRepository(library), Clock { 1L }, NoOpMappingScheduler),
        catalog = object : CatalogStoryProjectionRepository { override fun observe() = catalogFlow },
        progress = FakeProgressRepository(progress),
        chapters = FakeChapterRepository(chapters),
        mappings = FakeMappingRepository(mappings),
        downloads = FakeDownloadRepository(downloads),
    )
}

private class FakeLibraryRepository(private val flow: Flow<List<LibraryEntry>>) : LibraryRepository {
    override fun observe() = flow
    override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long) = error("unused")
    override suspend fun remove(storyId: StoryId) = Unit
    override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long) = error("unused")
}

private class FakeProgressRepository(private val flow: Flow<List<ReadingProgress>>) : ReadingProgressRepository {
    override fun observeAll() = flow
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId) = error("unused")
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId) = error("unused")
    override suspend fun save(progress: ReadingProgress) = Unit
}

private class FakeChapterRepository(private val flow: Flow<List<CanonicalChapterGroup>>) : ChapterRepository {
    override fun observeAll() = flow
    override fun observe(storyId: StoryId) = flow
    override suspend fun snapshot(storyId: StoryId) = ChapterGraphSnapshot(emptyList(), emptyList(), emptyList())
    override suspend fun commit(mutation: ChapterMutation) = ChapterCommitResult.Success
    override suspend fun saveOverride(storyId: StoryId, override: ChapterAggregationOverride) = Unit
    override suspend fun syncState(storyId: StoryId, pluginId: PluginId, sourceStoryId: String): ChapterSyncState? = null
}

private class FakeMappingRepository(private val flow: Flow<List<ContentMapping>>) : ContentMappingRepository {
    override fun observe(storyId: StoryId) = flow
    override fun observeAll() = flow
    override suspend fun compareAndWrite(mapping: ContentMapping, replaceableOrigins: Set<ContentMappingOrigin>): ContentMappingWriteResult = error("unused")
    override suspend fun reject(rejection: ContentMappingRejection) = Unit
    override suspend fun isRejected(storyId: StoryId, pluginId: PluginId, sourceStoryId: String, policyVersion: Int) = false
}

private class FakeDownloadRepository(private val flow: Flow<List<DownloadRecord>>) : DownloadRepository {
    override fun observeAll() = flow
    override fun observeCompletedCount(): Flow<Int> = flow.map { records ->
        records.count { it.state == DownloadState.COMPLETED }
    }
    override suspend fun find(releaseId: ChapterReleaseId): DownloadRecord? = null
    override fun observe(releaseId: ChapterReleaseId): Flow<DownloadRecord?> = flowOfNull()
    override suspend fun save(record: DownloadRecord) = Unit
}

private fun <T> flowOfNull(): Flow<T?> = MutableStateFlow(null)

private object NoOpMappingScheduler : LibraryMappingScheduler {
    override fun schedule(storyId: StoryId) = Unit
}
