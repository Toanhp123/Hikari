package app.openstory.catalog.ui.updates

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.activity.LibraryActivityProjector
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
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
import app.openstory.reader.content.ReaderSourceAvailability
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `projects library activity into date groups with story and reader identities`() = runTest(dispatcher) {
        val storyId = StoryId("story")
        val activity = app.openstory.catalog.ui.activity.LibraryActivityItem(
            storyId = storyId,
            title = "Fixture Story",
            coverUrl = null,
            chapterId = app.openstory.common.id.CanonicalChapterId("chapter"),
            releaseId = app.openstory.common.id.ChapterReleaseId("release"),
            chapterLabel = "Chapter 1",
            sourceLabel = "content.fixture",
            languageTag = "en",
            publishedAtEpochMillis = 1_754_236_800_000L,
            readerTarget = app.openstory.catalog.ui.components.ReaderTarget(storyId, app.openstory.common.id.CanonicalChapterId("chapter"), app.openstory.common.id.ChapterReleaseId("release")),
        )
        val viewModel = viewModel(FakeActivityProjector(listOf(activity)))

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        val item = viewModel.state.value.groups.single().items.single()
        assertEquals("Aug 3, 2025", viewModel.state.value.groups.single().label)
        assertEquals(storyId, item.storyId)
        assertEquals(activity.readerTarget, item.readerTarget)
    }

    @Test
    fun `preserves latest updates and exposes observation failure`() = runTest(dispatcher) {
        val storyId = StoryId("story")
        val target = app.openstory.catalog.ui.components.ReaderTarget(
            storyId,
            app.openstory.common.id.CanonicalChapterId("chapter"),
            app.openstory.common.id.ChapterReleaseId("release"),
        )
        val activity = app.openstory.catalog.ui.activity.LibraryActivityItem(
            storyId, "Fixture Story", null, target.chapterId, target.releaseId, "Chapter 1",
            "content.fixture", "en", 1_754_236_800_000L, target,
        )
        val failingCatalog = object : CatalogStoryProjectionRepository {
            override fun observe(): Flow<List<CatalogStoryProjection>> = kotlinx.coroutines.flow.flow {
                emit(emptyList())
                throw IllegalStateException("database unavailable")
            }
        }
        val viewModel = UpdatesViewModel(
            library = LibraryService(FakeLibraryRepository, { 1L }, LibraryMappingScheduler {}),
            catalog = failingCatalog,
            chapters = FakeUpdatesChapterRepository,
            mappings = FakeMappingRepository,
            readerSources = ReaderSourceAvailability { setOf(PluginId("content.fixture")) },
            projector = FakeActivityProjector(listOf(activity)),
        )

        backgroundScope.launch { viewModel.state.collect {} }
        advanceUntilIdle()

        assertEquals("updates.observe_failed", viewModel.state.value.failure)
        assertEquals("release", viewModel.state.value.groups.single().items.single().releaseId.value)
    }

    private fun viewModel(projector: LibraryActivityProjector) = UpdatesViewModel(
        library = LibraryService(FakeLibraryRepository, { 1L }, LibraryMappingScheduler {}),
        catalog = object : CatalogStoryProjectionRepository {
            override fun observe() = MutableStateFlow(listOf(CatalogStoryProjection(StoryId("story"), "Fixture Story", ContentType.WEB_NOVEL, null)))
        },
        chapters = FakeUpdatesChapterRepository,
        mappings = FakeMappingRepository,
        readerSources = ReaderSourceAvailability { setOf(PluginId("content.fixture")) },
        projector = projector,
    )
}

private class FakeActivityProjector(private val items: List<app.openstory.catalog.ui.activity.LibraryActivityItem>) : LibraryActivityProjector() {
    override fun project(
        library: List<LibraryEntry>,
        catalog: List<CatalogStoryProjection>,
        chapters: List<CanonicalChapterGroup>,
        mappings: List<ContentMapping>,
        readerPluginIds: Set<PluginId>,
    ) = items
}

private object FakeLibraryRepository : LibraryRepository {
    override fun observe(): Flow<List<LibraryEntry>> = MutableStateFlow(listOf(LibraryEntry(StoryId("story"), LibraryStatus.READING, 1L, 1L)))
    override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long) = error("unused")
    override suspend fun remove(storyId: StoryId) = Unit
    override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long) = null
}

private object FakeUpdatesChapterRepository : ChapterRepository {
    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = MutableStateFlow(emptyList())
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> = MutableStateFlow(emptyList())
    override suspend fun snapshot(storyId: StoryId) = error("unused")
    override suspend fun commit(mutation: app.openstory.chapters.repository.ChapterMutation) = error("unused")
    override suspend fun saveOverride(storyId: StoryId, override: app.openstory.chapters.model.ChapterAggregationOverride) = error("unused")
    override suspend fun syncState(storyId: StoryId, pluginId: PluginId, sourceStoryId: String) = null
}

private object FakeMappingRepository : ContentMappingRepository {
    override fun observe(storyId: StoryId): Flow<List<ContentMapping>> = observeAll()
    override fun observeAll(): Flow<List<ContentMapping>> = MutableStateFlow(listOf(ContentMapping(StoryId("story"), PluginId("content.fixture"), "source", ContentMappingOrigin.AUTOMATED, 1, 1L)))
    override suspend fun compareAndWrite(mapping: ContentMapping, replaceableOrigins: Set<ContentMappingOrigin>) = ContentMappingWriteResult.Written(mapping, true)
    override suspend fun reject(rejection: ContentMappingRejection) = Unit
    override suspend fun isRejected(storyId: StoryId, pluginId: PluginId, sourceStoryId: String, policyVersion: Int) = false
}
