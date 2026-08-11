package app.openstory.downloads.reader

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.CacheEntry
import app.openstory.downloads.cache.CacheRepository
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class DownloadAwareReaderDocumentStoreTest {
    @Test
    fun `explicit download wins over automatic cache`() = runTest {
        val blobs = FakeBlobs()
        val store = DownloadAwareReaderDocumentStore(blobs, FakeCacheRepository(), { 10 })
        blobs.write(key(ChapterBlobNamespace.AUTOMATIC_CACHE), ReaderDocumentBlobCodec.encode(document("cache")))
        blobs.write(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD), ReaderDocumentBlobCodec.encode(document("download")))

        assertEquals("download", store.read(releaseId, fingerprint)?.title)
    }

    @Test
    fun `corrupt explicit bytes are quarantined before falling back to cache`() = runTest {
        val blobs = FakeBlobs()
        val store = DownloadAwareReaderDocumentStore(blobs, FakeCacheRepository(), { 10 })
        blobs.write(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD), ChapterBlob.fromBytes("not-json".encodeToByteArray()))
        blobs.write(key(ChapterBlobNamespace.AUTOMATIC_CACHE), ReaderDocumentBlobCodec.encode(document("cache")))

        assertEquals("cache", store.read(releaseId, fingerprint)?.title)
        assertNull(blobs.read(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD)))
    }

    @Test
    fun `sanitized network write becomes automatic cache`() = runTest {
        val blobs = FakeBlobs()
        val repository = FakeCacheRepository()
        val store = DownloadAwareReaderDocumentStore(blobs, repository, { 42 })

        store.write(releaseId, fingerprint, document("network"))

        assertEquals("network", store.read(releaseId, fingerprint)?.title)
        assertEquals(ChapterBlobNamespace.AUTOMATIC_CACHE, repository.entries().single().key.namespace)
        assertEquals(42, repository.entries().single().lastAccessedAtEpochMillis)
    }

    private fun document(title: String) = ReaderDocument(title, listOf(ReaderBlock.Paragraph("p1", "Text")), fingerprint)
    private fun key(namespace: ChapterBlobNamespace) = ChapterBlobKey(namespace, releaseId, fingerprint)

    private companion object {
        val releaseId = ChapterReleaseId("release:1")
        const val fingerprint = "fingerprint"
    }
}

private class FakeBlobs : ChapterBlobStore {
    private val values = mutableMapOf<ChapterBlobKey, ChapterBlob>()
    override suspend fun read(key: ChapterBlobKey) = values[key]
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) { values[key] = blob }
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
