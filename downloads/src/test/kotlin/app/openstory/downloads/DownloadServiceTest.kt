package app.openstory.downloads

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.reconcile.StorageWriteAdmission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class DownloadServiceTest {
    @Test
    fun `download moves queued running completed and stores verified bytes`() = runTest {
        val repository = FakeDownloadRepository()
        val blobs = FakeBlobStore()
        val service = DownloadService(repository, blobs, source = {
            DownloadFetchResult.Success("fingerprint", "chapter".encodeToByteArray(), BlobChecksum.sha256("chapter".encodeToByteArray()))
        })
        service.queue(key.releaseId, 1)

        assertEquals(DownloadRunResult.COMPLETED, service.run(key.releaseId, 2))
        assertEquals(listOf(DownloadState.QUEUED, DownloadState.RUNNING, DownloadState.COMPLETED), repository.saved.map { it.state })
        assertEquals("chapter", blobs.read(key)!!.bytes().decodeToString())
    }

    @Test
    fun `checksum mismatch fails and removes partial bytes`() = runTest {
        val repository = FakeDownloadRepository()
        val blobs = FakeBlobStore()
        val service = DownloadService(repository, blobs, source = {
            DownloadFetchResult.Success("fingerprint", "wrong".encodeToByteArray(), BlobChecksum.sha256("expected".encodeToByteArray()))
        })
        service.queue(key.releaseId, 1)

        assertEquals(DownloadRunResult.FAILURE, service.run(key.releaseId, 2))
        assertEquals(DownloadState.FAILED, repository.find(key.releaseId)!!.state)
        assertNull(blobs.read(key))
    }

    @Test
    fun `completed retry is idempotent`() = runTest {
        val repository = FakeDownloadRepository()
        val blobs = FakeBlobStore()
        var fetches = 0
        val service = DownloadService(repository, blobs, source = {
            fetches += 1
            DownloadFetchResult.Success("fingerprint", "chapter".encodeToByteArray(), BlobChecksum.sha256("chapter".encodeToByteArray()))
        })
        service.queue(key.releaseId, 1)
        service.run(key.releaseId, 2)

        assertEquals(DownloadRunResult.COMPLETED, service.run(key.releaseId, 3))
        assertEquals(1, fetches)
    }

    @Test
    fun `low storage refuses the blob write and leaves retryable failure`() = runTest {
        val repository = FakeDownloadRepository()
        val blobs = FakeBlobStore()
        val service = DownloadService(
            repository,
            blobs,
            {
                DownloadFetchResult.Success(
                    "fingerprint",
                    "chapter".encodeToByteArray(),
                    BlobChecksum.sha256("chapter".encodeToByteArray()),
                )
            },
            StorageWriteAdmission { false },
        )
        service.queue(key.releaseId, 1)

        assertEquals(DownloadRunResult.RETRY, service.run(key.releaseId, 2))
        assertEquals("download.low_storage", repository.find(key.releaseId)?.failureReason)
        assertNull(blobs.read(key))
    }

    private val key = ChapterBlobKey(
        ChapterBlobNamespace.EXPLICIT_DOWNLOAD,
        ChapterReleaseId("release:1"),
        "fingerprint",
    )
}

private class FakeDownloadRepository : DownloadRepository {
    val saved = mutableListOf<DownloadRecord>()
    override suspend fun find(releaseId: ChapterReleaseId): DownloadRecord? = saved.lastOrNull { it.key.releaseId == releaseId }
    override fun observe(releaseId: ChapterReleaseId) = kotlinx.coroutines.flow.flowOf(saved.lastOrNull { it.key.releaseId == releaseId })
    override suspend fun save(record: DownloadRecord) { saved += record }
}

private class FakeBlobStore : ChapterBlobStore {
    private val blobs = mutableMapOf<ChapterBlobKey, app.openstory.downloads.blob.ChapterBlob>()
    override suspend fun read(key: ChapterBlobKey) = blobs[key]
    override suspend fun write(key: ChapterBlobKey, blob: app.openstory.downloads.blob.ChapterBlob) { blobs[key] = blob }
    override suspend fun delete(key: ChapterBlobKey) { blobs.remove(key) }
}
