package app.openstory.downloads.reader

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.CacheEntry
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.reconcile.StorageWriteAdmission
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class DownloadAwareReaderDocumentStoreTest {
    @Test
    fun `explicit download wins over automatic cache`() = runTest {
        val blobs = FakeBlobs()
        val store = DownloadAwareReaderDocumentStore(blobs, FakeCacheRepository(), FakeDownloads(), { 10 })
        blobs.write(key(ChapterBlobNamespace.AUTOMATIC_CACHE), ReaderDocumentBlobCodec.encode(document("cache")))
        blobs.write(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD), ReaderDocumentBlobCodec.encode(document("download")))

        assertEquals("download", store.read(releaseId, fingerprint)?.title)
    }

    @Test
    fun `corrupt explicit bytes are quarantined before falling back to cache`() = runTest {
        val blobs = FakeBlobs()
        val store = DownloadAwareReaderDocumentStore(blobs, FakeCacheRepository(), FakeDownloads(), { 10 })
        blobs.write(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD), ChapterBlob.fromBytes("not-json".encodeToByteArray()))
        blobs.write(key(ChapterBlobNamespace.AUTOMATIC_CACHE), ReaderDocumentBlobCodec.encode(document("cache")))

        assertEquals("cache", store.read(releaseId, fingerprint)?.title)
        assertNull(blobs.read(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD)))
    }

    @Test
    fun `sanitized network write becomes automatic cache`() = runTest {
        val blobs = FakeBlobs()
        val repository = FakeCacheRepository()
        val store = DownloadAwareReaderDocumentStore(blobs, repository, FakeDownloads(), { 42 })

        store.write(releaseId, fingerprint, document("network"))

        assertEquals("network", store.read(releaseId, fingerprint)?.title)
        assertEquals(ChapterBlobNamespace.AUTOMATIC_CACHE, repository.entries().single().key.namespace)
        assertEquals(42, repository.entries().single().lastAccessedAtEpochMillis)
    }

    @Test
    fun `low storage skips automatic cache without failing the reader write`() = runTest {
        val blobs = FakeBlobs()
        val repository = FakeCacheRepository()
        val store = DownloadAwareReaderDocumentStore(
            blobs,
            repository,
            FakeDownloads(),
            { 42 },
            StorageWriteAdmission { false },
        )

        store.write(releaseId, fingerprint, document("network"))

        assertTrue(repository.entries().isEmpty())
        assertNull(blobs.read(key(ChapterBlobNamespace.AUTOMATIC_CACHE)))
    }

    @Test
    fun `cache write failure does not fail the network reader result`() = runTest {
        val blobs = FakeBlobs(failWrites = true)
        val repository = FakeCacheRepository()
        val store = DownloadAwareReaderDocumentStore(blobs, repository, FakeDownloads(), { 42 })

        store.write(releaseId, fingerprint, document("network"))

        assertTrue(repository.entries().isEmpty())
    }

    @Test
    fun `touch failure does not discard a valid local document`() = runTest {
        val blobs = FakeBlobs()
        val store = DownloadAwareReaderDocumentStore(blobs, ThrowingTouchCacheRepository(), FakeDownloads(), { 10 })
        blobs.write(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD), ReaderDocumentBlobCodec.encode(document("download")))

        assertEquals("download", store.read(releaseId, fingerprint)?.title)
        assertTrue(blobs.read(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD)) != null)
    }

    @Test
    fun `current completed download resolves without a progress fingerprint`() = runTest {
        val blobs = FakeBlobs()
        val downloads = FakeDownloads(completedKey = key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD))
        val store = DownloadAwareReaderDocumentStore(blobs, FakeCacheRepository(), downloads, { 10 })
        blobs.write(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD), ReaderDocumentBlobCodec.encode(document("download")))

        assertEquals("download", store.readCurrent(releaseId)?.title)
    }

    @Test
    fun `network cache write enforces configured quota`() = runTest {
        val blobs = FakeBlobs()
        val repository = FakeCacheRepository()
        val oldKey = ChapterBlobKey(
            ChapterBlobNamespace.AUTOMATIC_CACHE,
            ChapterReleaseId("release:old"),
            "old",
        )
        val oldBlob = ReaderDocumentBlobCodec.encode(document("old"))
        val networkDocument = document("network")
        val networkBlob = ReaderDocumentBlobCodec.encode(networkDocument)
        blobs.write(oldKey, oldBlob)
        repository.upsert(CacheEntry(oldKey, oldBlob.checksum, oldBlob.bytes().size.toLong(), 1))
        val store = DownloadAwareReaderDocumentStore(
            blobs,
            repository,
            FakeDownloads(),
            { 42 },
            cacheQuotaBytes = networkBlob.bytes().size.toLong(),
        )

        store.write(releaseId, fingerprint, networkDocument)

        assertNull(blobs.read(oldKey))
        assertEquals("network", store.read(releaseId, fingerprint)?.title)
    }

    private fun document(title: String) = ReaderDocument(title, listOf(ReaderBlock.Paragraph("p1", "Text")), fingerprint)
    private fun key(namespace: ChapterBlobNamespace) = ChapterBlobKey(namespace, releaseId, fingerprint)

    private companion object {
        val releaseId = ChapterReleaseId("release:1")
        const val fingerprint = "fingerprint"
    }
}

private class FakeBlobs(
    private val failWrites: Boolean = false,
) : ChapterBlobStore {
    private val values = mutableMapOf<ChapterBlobKey, ChapterBlob>()
    override suspend fun read(key: ChapterBlobKey) = values[key]
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) {
        if (failWrites) error("disk full")
        values[key] = blob
    }
    override suspend fun delete(key: ChapterBlobKey) { values.remove(key) }
}

private class FakeCacheRepository : CacheRepository {
    private val values = linkedMapOf<ChapterBlobKey, CacheEntry>()
    override suspend fun entries() = values.values.toList()
    override suspend fun upsert(entry: CacheEntry) { values[entry.key] = entry }
    override suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long) {
        values[key]?.let { values[key] = it.copy(lastAccessedAtEpochMillis = accessedAtEpochMillis) }
    }
    override suspend fun commitEviction(keys: List<ChapterBlobKey>): List<ChapterBlobKey> = keys.filter { values.remove(it) != null }
}

private class ThrowingTouchCacheRepository : CacheRepository by FakeCacheRepository() {
    override suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long) {
        error("metadata unavailable")
    }
}

private class FakeDownloads(
    private val completedKey: ChapterBlobKey? = null,
) : app.openstory.downloads.DownloadRepository {
    override suspend fun find(releaseId: ChapterReleaseId) = completedKey?.let {
        app.openstory.downloads.DownloadRecord(
            key = it,
            state = app.openstory.downloads.DownloadState.COMPLETED,
            updatedAtEpochMillis = 1,
        )
    }
    override fun observe(releaseId: ChapterReleaseId) = kotlinx.coroutines.flow.flowOf(null)
    override suspend fun save(record: app.openstory.downloads.DownloadRecord) = Unit
}
