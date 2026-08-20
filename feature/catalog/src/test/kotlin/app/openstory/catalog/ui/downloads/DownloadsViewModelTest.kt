package app.openstory.catalog.ui.downloads

import app.openstory.catalog.model.ContentType
import app.openstory.catalog.projection.CatalogStoryProjection
import app.openstory.catalog.projection.CatalogStoryProjectionRepository
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ChapterRelease
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.CanonicalChapterGroup
import app.openstory.chapters.repository.ChapterRepository
import app.openstory.common.Clock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.DownloadContentSource
import app.openstory.downloads.DownloadFetchResult
import app.openstory.downloads.DownloadRecord
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadScheduler
import app.openstory.downloads.DownloadService
import app.openstory.downloads.DownloadState
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import java.math.BigDecimal
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
class DownloadsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `groups active completed and failed records while retaining missing metadata`() = runTest(dispatcher) {
        val records = MutableStateFlow(
            listOf(
                record("queued", DownloadState.QUEUED, 4L),
                record("running", DownloadState.RUNNING, 3L),
                record("completed", DownloadState.COMPLETED, 2L, sizeBytes = 2048L),
                record("failed", DownloadState.FAILED, 1L, failure = "network.timeout"),
                record("orphan", DownloadState.COMPLETED, 5L),
                record("cancelled", DownloadState.CANCELLED, 6L),
            ),
        )
        val viewModel = viewModel(records = records, releases = listOf(group("queued"), group("running"), group("completed"), group("failed")))

        viewModel.state.collectForTest(backgroundScope)
        advanceUntilIdle()

        assertEquals(listOf("queued", "running"), viewModel.state.value.active.map { it.releaseId.value })
        assertEquals(listOf("orphan", "completed"), viewModel.state.value.completed.map { it.releaseId.value })
        assertEquals(listOf("failed"), viewModel.state.value.failed.map { it.releaseId.value })
        assertEquals("orphan", viewModel.state.value.completed.first().chapterLabel)
        assertEquals("network.timeout", viewModel.state.value.failed.single().failureReason)
    }

    @Test
    fun `preserves latest records and exposes observation failure`() = runTest(dispatcher) {
        val records = kotlinx.coroutines.flow.flow {
            emit(listOf(record("completed", DownloadState.COMPLETED, 1L)))
            throw IllegalStateException("database unavailable")
        }
        val repository = object : DownloadRepository {
            override fun observeAll(): Flow<List<DownloadRecord>> = records
            override suspend fun find(releaseId: ChapterReleaseId) = null
            override fun observe(releaseId: ChapterReleaseId) = MutableStateFlow<DownloadRecord?>(null)
            override suspend fun save(record: DownloadRecord) = Unit
        }
        val viewModel = viewModel(repository = repository)

        viewModel.state.collectForTest(backgroundScope)
        advanceUntilIdle()

        assertEquals(listOf("completed"), viewModel.state.value.completed.map { it.releaseId.value })
        assertEquals("downloads.observe_failed", viewModel.state.value.failure)
    }

    @Test
    fun `retry queues before scheduling the exact failed release`() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val repository = FakeDownloadsRepository(MutableStateFlow(listOf(record("failed", DownloadState.FAILED, 1L)))) { events += "queue:${it.key.releaseId.value}" }
        val viewModel = viewModel(repository = repository, scheduler = scheduler(schedule = { events += "schedule:${it.value}" }))

        viewModel.retry(ChapterReleaseId("failed"))
        advanceUntilIdle()

        assertEquals(listOf("queue:failed", "schedule:failed"), events)
    }

    @Test
    fun `cancel stops scheduler before service cancellation and removal waits for confirmation`() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val repository = FakeDownloadsRepository(MutableStateFlow(listOf(record("running", DownloadState.RUNNING, 1L)))) { saved ->
            if (saved.state == DownloadState.CANCELLED) events += "service:${saved.key.releaseId.value}"
        }
        val viewModel = viewModel(repository = repository, scheduler = scheduler(cancel = { events += "scheduler:${it.value}" }))

        viewModel.state.collectForTest(backgroundScope)
        advanceUntilIdle()
        viewModel.requestRemoval(ChapterReleaseId("running"))
        advanceUntilIdle()
        assertEquals(ChapterReleaseId("running"), viewModel.state.value.pendingRemoval)
        viewModel.confirmRemoval()
        advanceUntilIdle()

        assertEquals(listOf("scheduler:running", "service:running"), events)
        assertEquals(null, viewModel.state.value.pendingRemoval)
    }

    private fun viewModel(
        records: MutableStateFlow<List<DownloadRecord>> = MutableStateFlow(emptyList()),
        releases: List<CanonicalChapterGroup> = emptyList(),
        repository: DownloadRepository = FakeDownloadsRepository(records),
        scheduler: DownloadScheduler = scheduler(),
    ) = DownloadsViewModel(
        repository = repository,
        service = DownloadService(repository, EmptyBlobStore, DownloadContentSource { DownloadFetchResult.Failure("unused", false) }),
        scheduler = scheduler,
        chapters = FakeChapterRepository(MutableStateFlow(releases)),
        catalog = object : CatalogStoryProjectionRepository {
            override fun observe() = MutableStateFlow(listOf(CatalogStoryProjection(StoryId("story"), "Fixture Story", ContentType.WEB_NOVEL, null)))
        },
        clock = Clock { 99L },
    )
}

private fun kotlinx.coroutines.flow.StateFlow<*>.collectForTest(scope: kotlinx.coroutines.CoroutineScope) =
    scope.launch { collect {} }

private class FakeDownloadsRepository(
    private val records: MutableStateFlow<List<DownloadRecord>>,
    private val onSave: (DownloadRecord) -> Unit = {},
) : DownloadRepository {
    override fun observeAll(): Flow<List<DownloadRecord>> = records
    override suspend fun find(releaseId: ChapterReleaseId) = records.value.find { it.key.releaseId == releaseId }
    override fun observe(releaseId: ChapterReleaseId) = MutableStateFlow(records.value.find { it.key.releaseId == releaseId })
    override suspend fun save(record: DownloadRecord) {
        records.value = records.value.filterNot { it.key.releaseId == record.key.releaseId } + record
        onSave(record)
    }
}

private class FakeChapterRepository(private val groups: MutableStateFlow<List<CanonicalChapterGroup>>) : ChapterRepository {
    override fun observeAll(): Flow<List<CanonicalChapterGroup>> = groups
    override fun observe(storyId: StoryId): Flow<List<CanonicalChapterGroup>> = groups
    override suspend fun snapshot(storyId: StoryId) = error("unused")
    override suspend fun commit(mutation: app.openstory.chapters.repository.ChapterMutation) = error("unused")
    override suspend fun saveOverride(storyId: StoryId, override: app.openstory.chapters.model.ChapterAggregationOverride) = error("unused")
    override suspend fun syncState(storyId: StoryId, pluginId: PluginId, sourceStoryId: String) = null
}

private object EmptyBlobStore : ChapterBlobStore {
    override suspend fun read(key: ChapterBlobKey): ChapterBlob? = null
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
    override suspend fun delete(key: ChapterBlobKey) = Unit
}

private fun scheduler(
    schedule: (ChapterReleaseId) -> Unit = {},
    cancel: (ChapterReleaseId) -> Unit = {},
) = object : DownloadScheduler {
    override fun schedule(releaseId: ChapterReleaseId) = schedule(releaseId)
    override fun cancel(releaseId: ChapterReleaseId) = cancel(releaseId)
}

private fun record(
    id: String,
    state: DownloadState,
    updatedAt: Long,
    sizeBytes: Long = 0L,
    failure: String? = null,
) = DownloadRecord(
    ChapterBlobKey(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, ChapterReleaseId(id), "fixture"),
    state,
    sizeBytes = sizeBytes,
    failureReason = failure,
    updatedAtEpochMillis = updatedAt,
)

private fun group(id: String): CanonicalChapterGroup {
    val storyId = StoryId("story")
    val chapterId = CanonicalChapterId("chapter-$id")
    val releaseId = ChapterReleaseId(id)
    val parsed = ParsedChapterLabel(ChapterKind.NUMBERED, null, BigDecimal.ONE, null, null)
    return CanonicalChapterGroup(
        CanonicalChapter(chapterId, storyId, parsed, "Chapter $id", false, setOf(releaseId)),
        listOf(ChapterRelease(releaseId, storyId, PluginId("content.fixture"), "source-story", "source-$id", "Chapter $id", parsed, "en", 10L, chapterId)),
    )
}
