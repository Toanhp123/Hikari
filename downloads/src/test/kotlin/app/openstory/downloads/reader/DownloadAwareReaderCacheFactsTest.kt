package app.openstory.downloads.reader

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.DownloadRecord
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadState
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.CacheEntry
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.reader.routing.ReaderLocalCacheFact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DownloadAwareReaderCacheFactsTest {
    @Test
    fun resumeFingerprintSelectsOnlyExactStoredLocator() = runTest {
        val release = ChapterReleaseId("release")
        val store = store(
            metadata = listOf(
                row(release, "expected", ChapterBlobNamespace.AUTOMATIC_CACHE, checksum = true),
                row(release, "other", ChapterBlobNamespace.EXPLICIT_DOWNLOAD, checksum = true, state = DownloadState.COMPLETED),
            ),
        )

        val exact = store.inspect(setOf(release), mapOf(release to "expected"))
        val missing = store.inspect(setOf(release), mapOf(release to "absent"))

        assertEquals(ReaderLocalCacheFact.Exact("expected"), exact.getValue(release))
        assertEquals(ReaderLocalCacheFact.Miss, missing.getValue(release))
    }

    @Test
    fun newestExplicitMustBeCompletedAndOlderExplicitIsNeverResurrected() = runTest {
        val release = ChapterReleaseId("release")
        val store = store(
            metadata = listOf(
                row(release, "old-completed", ChapterBlobNamespace.EXPLICIT_DOWNLOAD, checksum = true, state = DownloadState.COMPLETED, updated = 10),
                row(release, "new-running", ChapterBlobNamespace.EXPLICIT_DOWNLOAD, checksum = true, state = DownloadState.RUNNING, updated = 20),
                row(release, "auto", ChapterBlobNamespace.AUTOMATIC_CACHE, checksum = true, accessed = 30),
            ),
        )

        assertEquals(
            ReaderLocalCacheFact.Unverified("auto"),
            store.inspect(setOf(release), emptyMap()).getValue(release),
        )
    }

    @Test
    fun newestCompletedExplicitWinsWithoutResumeFingerprint() = runTest {
        val release = ChapterReleaseId("release")
        val store = store(
            metadata = listOf(
                row(release, "older", ChapterBlobNamespace.EXPLICIT_DOWNLOAD, checksum = true, state = DownloadState.COMPLETED, updated = 10),
                row(release, "newer", ChapterBlobNamespace.EXPLICIT_DOWNLOAD, checksum = true, state = DownloadState.COMPLETED, updated = 20),
                row(release, "auto", ChapterBlobNamespace.AUTOMATIC_CACHE, checksum = true, accessed = 100),
            ),
        )
        assertEquals(
            ReaderLocalCacheFact.Unverified("newer"),
            store.inspect(setOf(release), emptyMap()).getValue(release),
        )
    }

    @Test
    fun automaticCacheUsesAccessDescendingThenFingerprintAscendingTieBreak() = runTest {
        val release = ChapterReleaseId("release")
        val store = store(
            metadata = listOf(
                row(release, "z", ChapterBlobNamespace.AUTOMATIC_CACHE, checksum = true, accessed = 100),
                row(release, "a", ChapterBlobNamespace.AUTOMATIC_CACHE, checksum = true, accessed = 100),
                row(release, "newest", ChapterBlobNamespace.AUTOMATIC_CACHE, checksum = true, accessed = 101),
            ),
        )
        assertEquals(
            ReaderLocalCacheFact.Unverified("newest"),
            store.inspect(setOf(release), emptyMap()).getValue(release),
        )

        val tie = store(
            metadata = listOf(
                row(release, "z", ChapterBlobNamespace.AUTOMATIC_CACHE, checksum = true, accessed = 100),
                row(release, "a", ChapterBlobNamespace.AUTOMATIC_CACHE, checksum = true, accessed = 100),
            ),
        )
        assertEquals(
            ReaderLocalCacheFact.Unverified("a"),
            tie.inspect(setOf(release), emptyMap()).getValue(release),
        )
    }

    @Test
    fun rowsWithoutStoredChecksumAreIgnoredAndSuccessfulEmptyInspectionIsMiss() = runTest {
        val release = ChapterReleaseId("release")
        val store = store(
            metadata = listOf(
                row(release, "metadata-only", ChapterBlobNamespace.AUTOMATIC_CACHE, checksum = false, accessed = 100),
            ),
        )
        assertEquals(ReaderLocalCacheFact.Miss, store.inspect(setOf(release), emptyMap()).getValue(release))
    }

    @Test
    fun inspectIsMetadataOnlyAndNeverReadsChapterBlobBytes() = runTest {
        val release = ChapterReleaseId("release")
        val store = DownloadAwareReaderDocumentStore(
            blobs = object : ChapterBlobStore {
                override suspend fun read(key: ChapterBlobKey): ChapterBlob? =
                    error("Reader cache inspection must not read or decode chapter blobs")

                override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
                override suspend fun delete(key: ChapterBlobKey) = Unit
            },
            cacheRepository = NoopCacheRepository,
            downloads = NoopDownloads,
            now = { 0L },
            automaticCacheBudgetCoordinator = AutomaticCacheBudgetCoordinator.documentsOnly(
                NoopCacheRepository,
                NoopBlobStore,
            ),
            metadataSource = ReaderCacheMetadataSource {
                listOf(
                    row(
                        release = release,
                        fingerprint = "stored",
                        namespace = ChapterBlobNamespace.AUTOMATIC_CACHE,
                        checksum = true,
                    ),
                )
            },
        )

        assertEquals(
            ReaderLocalCacheFact.Unverified("stored"),
            store.inspect(setOf(release), emptyMap()).getValue(release),
        )
    }

    @Test
    fun metadataFailureReturnsUnknownForEveryRequestedRelease() = runTest {
        val a = ChapterReleaseId("a")
        val b = ChapterReleaseId("b")
        val store = store(source = ReaderCacheMetadataSource { error("database unavailable") })
        val result = store.inspect(setOf(a, b), emptyMap())
        assertIs<ReaderLocalCacheFact.Unknown>(result.getValue(a))
        assertIs<ReaderLocalCacheFact.Unknown>(result.getValue(b))
    }

    private fun store(
        metadata: List<ReaderCacheMetadata> = emptyList(),
        source: ReaderCacheMetadataSource = ReaderCacheMetadataSource { metadata },
    ) = DownloadAwareReaderDocumentStore(
        blobs = NoopBlobStore,
        cacheRepository = NoopCacheRepository,
        downloads = NoopDownloads,
        now = { 0L },
        automaticCacheBudgetCoordinator = AutomaticCacheBudgetCoordinator.documentsOnly(
            NoopCacheRepository,
            NoopBlobStore,
        ),
        metadataSource = source,
    )

    private fun row(
        release: ChapterReleaseId,
        fingerprint: String,
        namespace: ChapterBlobNamespace,
        checksum: Boolean,
        state: DownloadState? = null,
        accessed: Long = 0L,
        updated: Long = 0L,
    ) = ReaderCacheMetadata(
        releaseId = release,
        fingerprint = fingerprint,
        namespace = namespace,
        checksumPresent = checksum,
        downloadState = state,
        lastAccessedAtEpochMillis = accessed,
        updatedAtEpochMillis = updated,
    )
}

private object NoopBlobStore : ChapterBlobStore {
    override suspend fun read(key: ChapterBlobKey): ChapterBlob? = null
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
    override suspend fun delete(key: ChapterBlobKey) = Unit
}

private object NoopCacheRepository : CacheRepository {
    override suspend fun entries(): List<CacheEntry> = emptyList()
    override suspend fun upsert(entry: CacheEntry) = Unit
    override suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long) = Unit
    override suspend fun commitEviction(keys: List<ChapterBlobKey>): List<ChapterBlobKey> = emptyList()
}

private object NoopDownloads : DownloadRepository {
    private val values = MutableStateFlow<List<DownloadRecord>>(emptyList())
    override fun observeAll(): Flow<List<DownloadRecord>> = values
    override suspend fun find(releaseId: ChapterReleaseId): DownloadRecord? = null
    override fun observe(releaseId: ChapterReleaseId): Flow<DownloadRecord?> = MutableStateFlow(null)
    override suspend fun save(record: DownloadRecord) = Unit
}
