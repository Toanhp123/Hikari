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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

    private fun viewModel(repository: DownloadRepository, scheduler: DownloadScheduler): DownloadViewModel =
        DownloadViewModel(
            repository,
            DownloadService(repository, EmptyBlobStore, DownloadContentSource {
                DownloadFetchResult.Failure("unused", false)
            }),
            scheduler,
        )
}

private class FakeUiDownloadRepository : DownloadRepository {
    private val flows = mutableMapOf<ChapterReleaseId, MutableStateFlow<DownloadRecord?>>()
    override suspend fun find(releaseId: ChapterReleaseId): DownloadRecord? = flows[releaseId]?.value
    override fun observe(releaseId: ChapterReleaseId): Flow<DownloadRecord?> =
        flows.getOrPut(releaseId) { MutableStateFlow(null) }
    override suspend fun save(record: DownloadRecord) {
        flows.getOrPut(record.key.releaseId) { MutableStateFlow(null) }.value = record
    }
}

private object EmptyBlobStore : ChapterBlobStore {
    override suspend fun read(key: ChapterBlobKey): ChapterBlob? = null
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
    override suspend fun delete(key: ChapterBlobKey) = Unit
}
