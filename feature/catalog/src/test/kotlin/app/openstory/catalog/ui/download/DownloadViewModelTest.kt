package app.openstory.catalog.ui.download

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadContentSource
import app.openstory.downloads.DownloadFetchResult
import app.openstory.downloads.DownloadRecord
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadScheduler
import app.openstory.downloads.DownloadService
import app.openstory.downloads.DownloadState
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.blob.ChapterBlobNamespace
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `one range and filtered commands queue each unique release`() = runTest(dispatcher) {
        val repository = FakeUiDownloadRepository()
        val scheduled = mutableListOf<ChapterReleaseId>()
        val viewModel = viewModel(repository, DownloadScheduler(scheduled::add))
        val first = ChapterReleaseId("release:1")
        val second = ChapterReleaseId("release:2")

        viewModel.download(first)
        viewModel.downloadRange(listOf(first, second, second))
        viewModel.downloadFiltered(listOf(second))
        runCurrent()

        assertEquals(listOf(first, first, second, second), scheduled)
        assertEquals(DownloadState.QUEUED, repository.find(first)?.state)
        assertEquals(DownloadState.QUEUED, repository.find(second)?.state)
    }

    @Test
    fun `status observation uses one aggregate stream and no per release collectors`() = runTest(dispatcher) {
        val repository = FakeUiDownloadRepository()
        val viewModel = viewModel(repository, DownloadScheduler {})
        val releaseId = ChapterReleaseId("release:1")

        repository.save(
            DownloadRecord(
                ChapterBlobKey(ChapterBlobNamespace.EXPLICIT_DOWNLOAD, releaseId, "pending"),
                DownloadState.QUEUED,
                updatedAtEpochMillis = 1L,
            ),
        )

        val statuses = viewModel.statuses.first()

        assertEquals(1, repository.observeAllCalls)
        assertEquals(0, repository.observeCalls)
        assertEquals(DownloadState.QUEUED, statuses[releaseId])
    }

    @Test
    fun `destructive removal waits for confirmation`() = runTest(dispatcher) {
        val repository = FakeUiDownloadRepository()
        val releaseId = ChapterReleaseId("release:1")
        val viewModel = viewModel(repository, DownloadScheduler {})
        viewModel.download(releaseId)
        runCurrent()

        viewModel.requestRemoval(releaseId)
        assertEquals(releaseId, viewModel.state.value.pendingRemoval)
        viewModel.confirmRemoval()
        runCurrent()

        assertEquals(null, viewModel.state.value.pendingRemoval)
        assertEquals(DownloadState.CANCELLED, repository.find(releaseId)?.state)
    }

    @Test
    fun `cancel stops scheduled work before recording cancelled state`() = runTest(dispatcher) {
        val repository = FakeUiDownloadRepository()
        val releaseId = ChapterReleaseId("release:1")
        val cancelled = mutableListOf<ChapterReleaseId>()
        val scheduler = object : DownloadScheduler {
            override fun schedule(releaseId: ChapterReleaseId) = Unit
            override fun cancel(releaseId: ChapterReleaseId) { cancelled += releaseId }
        }
        val viewModel = viewModel(repository, scheduler)
        viewModel.download(releaseId)
        runCurrent()

        viewModel.cancel(releaseId)
        runCurrent()

        assertEquals(listOf(releaseId), cancelled)
        assertEquals(DownloadState.CANCELLED, repository.find(releaseId)?.state)
    }

    private fun viewModel(repository: DownloadRepository, scheduler: DownloadScheduler): DownloadViewModel =
        DownloadViewModel(
            DownloadService(repository, EmptyBlobStore, DownloadContentSource {
                DownloadFetchResult.Failure("unused", false)
            }),
            scheduler,
        )
}

private class FakeUiDownloadRepository : DownloadRepository {
    private val flows = mutableMapOf<ChapterReleaseId, MutableStateFlow<DownloadRecord?>>()
    private val all = MutableStateFlow<List<DownloadRecord>>(emptyList())
    var observeAllCalls = 0
    var observeCalls = 0
    override fun observeAll(): Flow<List<DownloadRecord>> = all.also { observeAllCalls++ }
    override suspend fun find(releaseId: ChapterReleaseId): DownloadRecord? = flows[releaseId]?.value
    override fun observe(releaseId: ChapterReleaseId): Flow<DownloadRecord?> {
        observeCalls++
        return flows.getOrPut(releaseId) { MutableStateFlow(null) }
    }
    override suspend fun save(record: DownloadRecord) {
        flows.getOrPut(record.key.releaseId) { MutableStateFlow(null) }.value = record
        all.value = all.value.filterNot { it.key.releaseId == record.key.releaseId } + record
    }
}

private object EmptyBlobStore : ChapterBlobStore {
    override suspend fun read(key: ChapterBlobKey): ChapterBlob? = null
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
    override suspend fun delete(key: ChapterBlobKey) = Unit
}
