package app.openstory.catalog.ui.dashboard

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.catalog.ui.state.ContentState
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterAggregationOverride
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
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
import app.openstory.reader.progress.ReadingPosition
import app.openstory.reader.progress.ReadingProgress
import app.openstory.reader.progress.ReadingProgressRepository
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
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
    fun emptyLibraryBecomesReadyNoLibraryWithoutWaitingForEnrichment() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.library.value = emptyList()
        var scopedObservationCalls = 0
        fixtures.catalogSource = {
            scopedObservationCalls += 1
            neverFlow()
        }
        fixtures.progressSource = {
            scopedObservationCalls += 1
            neverFlow()
        }
        fixtures.chapterSource = {
            scopedObservationCalls += 1
            neverFlow()
        }
        fixtures.mappingSource = {
            scopedObservationCalls += 1
            neverFlow()
        }
        fixtures.downloadCountFlow = neverFlow()
        fixtures.readerSource = { awaitCancellation() }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        val content = viewModel.state.value.readyContent()
        assertEquals(HomeNoContentReason.NO_LIBRARY, content.noContentReason)
        assertEquals(0, scopedObservationCalls)
    }

    @Test
    fun nonEmptyLibraryRendersBaseShelvesBeforeOtherDependencies() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.catalogSource = { neverFlow() }
        fixtures.progressSource = { neverFlow() }
        fixtures.chapterSource = { neverFlow() }
        fixtures.mappingSource = { neverFlow() }
        fixtures.downloadCountFlow = neverFlow()
        fixtures.readerSource = { awaitCancellation() }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        val content = viewModel.state.value.readyContent()
        assertEquals("story-1", content.reading.single().title)
        assertNull(content.summary.downloadedCount)
        assertNull(content.noContentReason)
    }

    @Test
    fun progressArrivalAddsContinueReadingWithoutFullScreenPending() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        val progress = MutableStateFlow<List<ReadingProgress>?>(null)
        fixtures.progressSource = { progress.nonNullValues() }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals(emptyList(), viewModel.state.value.readyContent().continueReading)

        progress.value = listOf(readingProgress(fixtures.storyId, "1", 20L))
        runCurrent()

        val content = viewModel.state.value.readyContent()
        assertEquals(fixtures.storyId, content.continueReading.single().storyId)
        assertNotNull(content.continueReading.single().progressFraction)
    }

    @Test
    fun chapterAndMappingArrivalAddsUpdatesShelfBeforeReaderCapability() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.readerSource = { awaitCancellation() }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        fixtures.chapters.value = listOf(chapterGroup(fixtures.storyId, "1", "source-a", 10L))
        fixtures.mappings.value = listOf(contentMapping(fixtures.storyId, "source-a"))
        runCurrent()

        val update = viewModel.state.value.readyContent().latestUpdates.single()
        assertEquals(ChapterReleaseId("release-1"), update.releaseId)
        assertNull(update.readerTarget)
    }

    @Test
    fun readerCapabilityArrivalEnrichesHomeUpdatesWithoutFullScreenPending() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.chapters.value = listOf(chapterGroup(fixtures.storyId, "1", "source-a", 10L))
        fixtures.mappings.value = listOf(contentMapping(fixtures.storyId, "source-a"))
        val readerIds = CompletableDeferred<Set<PluginId>>()
        fixtures.readerSource = { readerIds.await() }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertNull(viewModel.state.value.readyContent().latestUpdates.single().readerTarget)

        readerIds.complete(setOf(PluginId("content.a")))
        runCurrent()

        assertNotNull(viewModel.state.value.readyContent().latestUpdates.single().readerTarget)
    }

    @Test
    fun libraryFirstFailureIsBlockingFailed() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.libraryFlow = flow { throw IllegalStateException("offline") }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        val content = assertIs<ContentState.Failed>(viewModel.state.value.content)
        assertEquals("home.library.observe_exception", content.failure.code)
        assertNull(viewModel.state.value.observationIssue)
    }

    @Test
    fun retryContentRestartsLibraryObservationAndReachesReady() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        var attempts = 0
        fixtures.libraryFlow = flow {
            attempts += 1
            if (attempts == 1) throw IllegalStateException("offline")
            emit(listOf(libraryEntry(fixtures.storyId, LibraryStatus.READING)))
        }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertIs<ContentState.Failed>(viewModel.state.value.content)

        viewModel.retryContent()
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(fixtures.storyId, viewModel.state.value.readyContent().reading.single().storyId)
    }

    @Test
    fun enrichmentFailureKeepsBaseHomeAndSurfacesIssue() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        fixtures.catalogSource = { flow { throw IllegalStateException("offline") } }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals("story-1", viewModel.state.value.readyContent().reading.single().title)
        assertEquals("home.catalog.observe_exception", viewModel.state.value.observationIssue?.code)
    }

    @Test
    fun storyIdChangeDoesNotReuseOldEnrichmentOrIssue() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        val firstStory = fixtures.storyId
        val secondStory = StoryId("story-2")
        fixtures.catalogSource = { storyIds ->
            when (storyIds.singleOrNull()) {
                firstStory -> flow {
                    emit(listOf(projection(firstStory, "Fixture Novel")))
                    throw IllegalStateException("offline")
                }
                secondStory -> neverFlow()
                else -> error("Unexpected key $storyIds")
            }
        }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals("Fixture Novel", viewModel.state.value.readyContent().reading.single().title)
        assertEquals("home.catalog.observe_exception", viewModel.state.value.observationIssue?.code)

        fixtures.library.value = listOf(libraryEntry(secondStory, LibraryStatus.READING))
        runCurrent()

        assertEquals("story-2", viewModel.state.value.readyContent().reading.single().title)
        assertNull(viewModel.state.value.observationIssue)
    }

    @Test
    fun retryObservationRestartsTheSurfacedDependency() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        var catalogAttempts = 0
        fixtures.catalogSource = {
            flow {
                catalogAttempts += 1
                if (catalogAttempts == 1) throw IllegalStateException("offline")
                emit(listOf(projection(fixtures.storyId, "Fixture Novel")))
            }
        }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals("home.catalog.observe_exception", viewModel.state.value.observationIssue?.code)

        viewModel.retryObservation()
        runCurrent()

        assertEquals(2, catalogAttempts)
        assertEquals("Fixture Novel", viewModel.state.value.readyContent().reading.single().title)
        assertNull(viewModel.state.value.observationIssue)
    }

    @Test
    fun recoveringOneObservationLeavesTheNextIssueSurfaced() = runTest(dispatcher.scheduler) {
        val fixtures = Fixtures()
        var catalogAttempts = 0
        fixtures.catalogSource = {
            flow {
                catalogAttempts += 1
                emit(listOf(projection(fixtures.storyId, "Fixture Novel")))
                if (catalogAttempts == 1) throw IllegalStateException("catalog offline")
            }
        }
        fixtures.progressSource = {
            flow {
                emit(emptyList())
                throw IllegalStateException("progress offline")
            }
        }
        val viewModel = fixtures.viewModel()
        backgroundScope.launch { viewModel.state.collect() }
        runCurrent()

        assertEquals("home.catalog.observe_exception", viewModel.state.value.observationIssue?.code)

        viewModel.retryObservation()
        runCurrent()

        assertEquals(2, catalogAttempts)
        assertEquals("home.progress.observe_exception", viewModel.state.value.observationIssue?.code)
    }
}

private class Fixtures {
    val storyId = StoryId("story-1")
    val library = MutableStateFlow(listOf(libraryEntry(storyId, LibraryStatus.READING)))
    var libraryFlow: Flow<List<LibraryEntry>> = library
    val catalog = MutableStateFlow(listOf(CatalogStoryProjection(storyId, "Fixture Novel", ContentType.WEB_NOVEL, null)))
    val progress = MutableStateFlow<List<ReadingProgress>>(emptyList())
    val chapters = MutableStateFlow<List<CanonicalChapterGroup>>(emptyList())
    val mappings = MutableStateFlow<List<ContentMapping>>(emptyList())
    val downloadCount = MutableStateFlow(0)
    var catalogSource: (Set<StoryId>) -> Flow<List<CatalogStoryProjection>> = { catalog }
    var progressSource: (Set<StoryId>) -> Flow<List<ReadingProgress>> = { progress }
    var chapterSource: (Set<StoryId>) -> Flow<List<CanonicalChapterGroup>> = { chapters }
    var mappingSource: (Set<StoryId>) -> Flow<List<ContentMapping>> = { mappings }
    var downloadCountFlow: Flow<Int> = downloadCount
    var readerSource: suspend () -> Set<PluginId> = { setOf(PluginId("content.fixture")) }

    fun viewModel() = HomeDashboardViewModel(
        library = LibraryService(FakeLibraryRepository(libraryFlow), Clock { 1L }, NoOpMappingScheduler),
        catalog = FakeCatalogRepository(catalogSource),
        progress = FakeProgressRepository(progressSource),
        chapters = FakeChapterRepository(chapterSource),
        mappings = FakeMappingRepository(mappingSource),
        downloads = FakeDownloadRepository(downloadCountFlow),
        readerSources = ReaderSourceAvailability { readerSource() },
    )
}

private class FakeLibraryRepository(private val flow: Flow<List<LibraryEntry>>) : LibraryRepository {
    override fun observe() = flow
    override suspend fun add(storyId: StoryId, status: LibraryStatus, addedAt: Long) = error("unused")
    override suspend fun remove(storyId: StoryId) = Unit
    override suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, updatedAt: Long) = error("unused")
}

private class FakeCatalogRepository(
    private val source: (Set<StoryId>) -> Flow<List<CatalogStoryProjection>>,
) : CatalogStoryProjectionRepository {
    override fun observe() = source(emptySet())
    override fun observeForStories(storyIds: Set<StoryId>) = source(storyIds)
}

private class FakeProgressRepository(
    private val source: (Set<StoryId>) -> Flow<List<ReadingProgress>>,
) : ReadingProgressRepository {
    override fun observeAll() = source(emptySet())
    override fun observeForStories(storyIds: Set<StoryId>) = source(storyIds)
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId) = error("unused")
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId) = error("unused")
    override suspend fun save(progress: ReadingProgress) = Unit
}

private class FakeChapterRepository(
    private val source: (Set<StoryId>) -> Flow<List<CanonicalChapterGroup>>,
) : ChapterRepository {
    override fun observeAll() = source(emptySet())
    override fun observeForStories(storyIds: Set<StoryId>) = source(storyIds)
    override fun observe(storyId: StoryId) = source(setOf(storyId))
    override suspend fun snapshot(storyId: StoryId) = ChapterGraphSnapshot(emptyList(), emptyList(), emptyList())
    override suspend fun commit(mutation: ChapterMutation) = ChapterCommitResult.Success
    override suspend fun saveOverride(storyId: StoryId, override: ChapterAggregationOverride) = Unit
    override suspend fun syncState(storyId: StoryId, pluginId: PluginId, sourceStoryId: String): ChapterSyncState? = null
}

private class FakeMappingRepository(
    private val source: (Set<StoryId>) -> Flow<List<ContentMapping>>,
) : ContentMappingRepository {
    override fun observe(storyId: StoryId) = source(setOf(storyId))
    override fun observeAll() = source(emptySet())
    override fun observeForStories(storyIds: Set<StoryId>) = source(storyIds)
    override suspend fun compareAndWrite(mapping: ContentMapping, replaceableOrigins: Set<ContentMappingOrigin>): ContentMappingWriteResult = error("unused")
    override suspend fun reject(rejection: ContentMappingRejection) = Unit
    override suspend fun isRejected(storyId: StoryId, pluginId: PluginId, sourceStoryId: String, policyVersion: Int) = false
}

private class FakeDownloadRepository(private val countFlow: Flow<Int>) : DownloadRepository {
    override fun observeAll(): Flow<List<DownloadRecord>> = MutableStateFlow(emptyList())
    override fun observeCompletedCount(): Flow<Int> = countFlow
    override suspend fun find(releaseId: ChapterReleaseId): DownloadRecord? = null
    override fun observe(releaseId: ChapterReleaseId): Flow<DownloadRecord?> = flowOfNull()
    override suspend fun save(record: DownloadRecord) = Unit
}

private fun <T> flowOfNull(): Flow<T?> = MutableStateFlow(null)

private fun HomeDashboardUiState.readyContent(): HomeDashboardContent =
    assertIs<ContentState.Ready<HomeDashboardContent>>(content).value

private fun <T> neverFlow(): Flow<T> = flow { awaitCancellation() }

private fun <T : Any> Flow<T?>.nonNullValues(): Flow<T> = flow {
    collect { value -> value?.let { emit(it) } }
}

private fun libraryEntry(storyId: StoryId, status: LibraryStatus) =
    LibraryEntry(storyId, status, addedAt = 1L, updatedAt = 2L)

private fun projection(storyId: StoryId, title: String) = CatalogStoryProjection(
    storyId = storyId,
    title = title,
    contentType = ContentType.WEB_NOVEL,
    coverUrl = null,
)

private fun readingProgress(storyId: StoryId, chapter: String, updatedAt: Long) = ReadingProgress(
    storyId = storyId,
    canonicalChapterId = CanonicalChapterId("chapter-$chapter"),
    releaseId = ChapterReleaseId("release-$chapter"),
    contentFingerprint = "fingerprint-$chapter",
    position = ReadingPosition("block", 0, 0.5f),
    completedAtEpochMillis = null,
    updatedAtEpochMillis = updatedAt,
)

private fun contentMapping(storyId: StoryId, sourceStoryId: String) = ContentMapping(
    storyId = storyId,
    pluginId = PluginId("content.a"),
    sourceStoryId = sourceStoryId,
    origin = ContentMappingOrigin.AUTOMATED,
    policyVersion = 1,
    updatedAt = 1L,
)

private fun chapterGroup(
    storyId: StoryId,
    number: String,
    sourceStoryId: String,
    publishedAt: Long,
): CanonicalChapterGroup {
    val chapterId = CanonicalChapterId("chapter-$number")
    val releaseId = ChapterReleaseId("release-$number")
    val label = ParsedChapterLabel(ChapterKind.NUMBERED, null, BigDecimal(number), null, null)
    val release = ChapterRelease(
        releaseId,
        storyId,
        PluginId("content.a"),
        sourceStoryId,
        "source-release-$number",
        "Chapter $number",
        label,
        "en",
        publishedAt,
        chapterId,
    )
    return CanonicalChapterGroup(
        CanonicalChapter(chapterId, storyId, label, "Chapter $number", false, setOf(releaseId)),
        listOf(release),
    )
}

private object NoOpMappingScheduler : LibraryMappingScheduler {
    override fun schedule(storyId: StoryId) = Unit
}
