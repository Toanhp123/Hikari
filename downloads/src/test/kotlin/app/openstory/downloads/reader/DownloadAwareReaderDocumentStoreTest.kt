package app.openstory.downloads.reader

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.CacheEntry
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.downloads.cache.AutomaticCacheInvalidationScope
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.reconcile.StorageWriteAdmission
import app.openstory.reader.content.ReaderDocumentReadResult
import app.openstory.reader.document.ReaderBlock
import app.openstory.reader.document.ReaderDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class DownloadAwareReaderDocumentStoreTest {
    @Test
    fun `missing exact locator returns typed Missing`() = runTest {
        val store = documentStore(
            FakeBlobs(),
            FakeCacheRepository(),
            FakeDownloads(),
            { 10L },
        )

        assertIs<ReaderDocumentReadResult.Missing>(store.readResult(releaseId, fingerprint))
    }

    @Test
    fun `only corrupt exact copy returns typed fingerprint or decode mismatch`() = runTest {
        val blobs = FakeBlobs()
        val store = documentStore(blobs, FakeCacheRepository(), FakeDownloads(), { 10L })
        blobs.write(
            key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD),
            ChapterBlob.fromBytes("broken".encodeToByteArray()),
        )

        assertIs<ReaderDocumentReadResult.FingerprintOrDecodeMismatch>(
            store.readResult(releaseId, fingerprint),
        )
    }

    @Test
    fun `valid cache survives corrupt explicit copy`() = runTest {
        val blobs = FakeBlobs()
        val store = documentStore(blobs, FakeCacheRepository(), FakeDownloads(), { 10L })
        val explicitKey = key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD)
        val cacheKey = key(ChapterBlobNamespace.AUTOMATIC_CACHE)
        blobs.write(explicitKey, ChapterBlob.fromBytes("broken".encodeToByteArray()))
        blobs.write(cacheKey, ReaderDocumentBlobCodec.encode(document("cache")))

        val result = assertIs<ReaderDocumentReadResult.Hit>(store.readResult(releaseId, fingerprint))

        assertEquals("cache", result.document.title)
        assertNull(blobs.read(explicitKey))
        assertNotNull(blobs.read(cacheKey))
    }

    @Test
    fun `cleanup failure does not erase confirmed corruption`() = runTest {
        val blobs = FakeBlobs(failDeletes = true)
        val store = documentStore(blobs, FakeCacheRepository(), FakeDownloads(), { 10L })
        blobs.write(
            key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD),
            ChapterBlob.fromBytes("broken".encodeToByteArray()),
        )

        assertIs<ReaderDocumentReadResult.FingerprintOrDecodeMismatch>(
            store.readResult(releaseId, fingerprint),
        )
    }

    @Test
    fun `explicit download wins over automatic cache`() = runTest {
        val blobs = FakeBlobs()
        val store = documentStore(blobs, FakeCacheRepository(), FakeDownloads(), { 10 })
        blobs.write(key(ChapterBlobNamespace.AUTOMATIC_CACHE), ReaderDocumentBlobCodec.encode(document("cache")))
        blobs.write(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD), ReaderDocumentBlobCodec.encode(document("download")))

        assertEquals("download", store.read(releaseId, fingerprint)?.title)
    }

    @Test
    fun `corrupt explicit bytes are quarantined before falling back to cache`() = runTest {
        val blobs = FakeBlobs()
        val store = documentStore(blobs, FakeCacheRepository(), FakeDownloads(), { 10 })
        blobs.write(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD), ChapterBlob.fromBytes("not-json".encodeToByteArray()))
        blobs.write(key(ChapterBlobNamespace.AUTOMATIC_CACHE), ReaderDocumentBlobCodec.encode(document("cache")))

        assertEquals("cache", store.read(releaseId, fingerprint)?.title)
        assertNull(blobs.read(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD)))
    }

    @Test
    fun `sanitized network write becomes automatic cache`() = runTest {
        val blobs = FakeBlobs()
        val repository = FakeCacheRepository()
        val store = documentStore(blobs, repository, FakeDownloads(), { 42 })

        store.write(releaseId, fingerprint, document("network"))

        assertEquals("network", store.read(releaseId, fingerprint)?.title)
        assertEquals(ChapterBlobNamespace.AUTOMATIC_CACHE, repository.entries().single().key.namespace)
        assertEquals(42, repository.entries().single().lastAccessedAtEpochMillis)
    }

    @Test
    fun `low storage skips automatic cache without failing the reader write`() = runTest {
        val blobs = FakeBlobs()
        val repository = FakeCacheRepository()
        val store = documentStore(
            blobs,
            repository,
            FakeDownloads(),
            { 42 },
            writeAdmission = StorageWriteAdmission { false },
        )

        store.write(releaseId, fingerprint, document("network"))

        assertTrue(repository.entries().isEmpty())
        assertNull(blobs.read(key(ChapterBlobNamespace.AUTOMATIC_CACHE)))
    }

    @Test
    fun `cache write failure does not fail the network reader result`() = runTest {
        val blobs = FakeBlobs(failWrites = true)
        val repository = FakeCacheRepository()
        val store = documentStore(blobs, repository, FakeDownloads(), { 42 })

        store.write(releaseId, fingerprint, document("network"))

        assertTrue(repository.entries().isEmpty())
    }

    @Test
    fun `touch failure does not discard a valid local document`() = runTest {
        val blobs = FakeBlobs()
        val store = documentStore(blobs, ThrowingTouchCacheRepository(), FakeDownloads(), { 10 })
        blobs.write(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD), ReaderDocumentBlobCodec.encode(document("download")))

        assertEquals("download", store.read(releaseId, fingerprint)?.title)
        assertTrue(blobs.read(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD)) != null)
    }

    @Test
    fun `current completed download resolves without a progress fingerprint`() = runTest {
        val blobs = FakeBlobs()
        val downloads = FakeDownloads(completedKey = key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD))
        val store = documentStore(blobs, FakeCacheRepository(), downloads, { 10 })
        blobs.write(key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD), ReaderDocumentBlobCodec.encode(document("download")))

        assertEquals("download", store.readCurrent(releaseId)?.title)
    }

    @Test
    fun `network cache write uses shared coordinator quota`() = runTest {
        val blobs = FakeBlobs()
        val repository = FakeCacheRepository()
        val networkDocument = document("network")
        val networkBlob = ReaderDocumentBlobCodec.encode(networkDocument)
        val store = documentStore(
            blobs,
            repository,
            FakeDownloads(),
            { 42 },
            coordinator = documentBudget(
                blobs,
                repository,
                networkBlob.bytes().size.toLong(),
            ),
        )

        store.write(releaseId, fingerprint, networkDocument)

        assertEquals("network", store.read(releaseId, fingerprint)?.title)
    }

    @Test
    fun `pre-fetch write intent revoked by clear cannot publish but a later intent can`() = runTest {
        val blobs = FakeBlobs()
        val repository = FakeCacheRepository()
        val coordinator = documentBudget(blobs, repository, 1_000)
        val store = documentStore(
            blobs,
            repository,
            FakeDownloads(),
            { 42 },
            coordinator = coordinator,
        )
        val staleIntent = assertNotNull(store.captureAutomaticWriteIntent())

        coordinator.clearAutomatic(AutomaticCacheInvalidationScope.AllAutomatic)
        store.writeWithIntent(releaseId, fingerprint, document("stale"), staleIntent)

        assertTrue(repository.entries().isEmpty())
        assertNull(blobs.read(key(ChapterBlobNamespace.AUTOMATIC_CACHE)))

        val freshIntent = assertNotNull(store.captureAutomaticWriteIntent())
        store.writeWithIntent(releaseId, fingerprint, document("fresh"), freshIntent)

        assertEquals("fresh", store.read(releaseId, fingerprint)?.title)
        assertEquals(1, repository.entries().size)
    }

    private fun document(title: String) = ReaderDocument(title, listOf(ReaderBlock.Paragraph("p1", "Text")), fingerprint)
    private fun key(namespace: ChapterBlobNamespace) = ChapterBlobKey(namespace, releaseId, fingerprint)

    private fun documentStore(
        blobs: ChapterBlobStore,
        repository: CacheRepository,
        downloads: app.openstory.downloads.DownloadRepository,
        now: () -> Long,
        writeAdmission: StorageWriteAdmission = StorageWriteAdmission.ALLOW_ALL,
        coordinator: AutomaticCacheBudgetCoordinator = documentBudget(blobs, repository, 256L * 1024 * 1024),
    ) = DownloadAwareReaderDocumentStore(
        blobs = blobs,
        cacheRepository = repository,
        downloads = downloads,
        now = now,
        automaticCacheBudgetCoordinator = coordinator,
        writeAdmission = writeAdmission,
    )

    private fun documentBudget(
        blobs: ChapterBlobStore,
        repository: CacheRepository,
        quotaBytes: Long,
    ) = AutomaticCacheBudgetCoordinator.documentsOnly(
        cacheRepository = repository,
        documentBlobStore = blobs,
        initialQuotaBytes = quotaBytes,
    )

    private companion object {
        val releaseId = ChapterReleaseId("release:1")
        const val fingerprint = "fingerprint"
    }
}

private class FakeBlobs(
    private val failWrites: Boolean = false,
    private val failDeletes: Boolean = false,
) : ChapterBlobStore {
    private val values = mutableMapOf<ChapterBlobKey, ChapterBlob>()
    override suspend fun read(key: ChapterBlobKey) = values[key]
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) {
        if (failWrites) error("disk full")
        values[key] = blob
    }
    override suspend fun delete(key: ChapterBlobKey) {
        if (failDeletes) error("delete failed")
        values.remove(key)
    }
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
    private val all = MutableStateFlow(
        completedKey?.let { key ->
            listOf(
                app.openstory.downloads.DownloadRecord(
                    key = key,
                    state = app.openstory.downloads.DownloadState.COMPLETED,
                    updatedAtEpochMillis = 1,
                ),
            )
        }.orEmpty(),
    )
    override fun observeAll() = all
    override suspend fun find(releaseId: ChapterReleaseId) = completedKey?.let {
        app.openstory.downloads.DownloadRecord(
            key = it,
            state = app.openstory.downloads.DownloadState.COMPLETED,
            updatedAtEpochMillis = 1,
        )
    }
    override fun observe(releaseId: ChapterReleaseId) =
        all.map { records -> records.lastOrNull { it.key.releaseId == releaseId } }
    override suspend fun save(record: app.openstory.downloads.DownloadRecord) {
        all.value = all.value.filterNot { it.key.releaseId == record.key.releaseId } + record
    }
}
