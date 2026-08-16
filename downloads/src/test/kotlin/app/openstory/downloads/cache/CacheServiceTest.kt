package app.openstory.downloads.cache

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CacheServiceTest {
    @Test
    fun `under quota uses scalar usage without materializing cache entries`() = runTest {
        val repository = CountingCacheRepository(usageBytes = 10)
        val service = CacheService(repository, NoopBlobStore)

        val plan = service.enforceQuota(20, emptySet())

        assertTrue(plan.keys.isEmpty())
        assertEquals(10, plan.retainedBytes)
        assertEquals(1, repository.snapshotCalls)
        assertEquals(0, repository.entriesCalls)
        assertEquals(0, repository.commitCalls)
    }

    @Test
    fun `default quota snapshot materializes fallback entries once`() = runTest {
        val oldest = entry("old", 8, 1)
        val newest = entry("new", 8, 2)
        val repository = EntriesOnlyCacheRepository(listOf(oldest, newest))

        val snapshot = repository.quotaSnapshot(8)

        assertEquals(1, repository.entriesCalls)
        assertEquals(16, snapshot.usageBytes)
        assertEquals(listOf(oldest, newest), snapshot.entriesByLru)
    }

    @Test
    fun `over quota loads ordered candidates only when needed`() = runTest {
        val oldest = entry("old", 8, 1)
        val newest = entry("new", 8, 2)
        val repository = CountingCacheRepository(
            usageBytes = 16,
            lru = listOf(oldest, newest),
        )
        val service = CacheService(repository, NoopBlobStore)

        val plan = service.enforceQuota(8, emptySet())

        assertEquals(listOf(oldest.key), plan.keys)
        assertEquals(1, repository.snapshotCalls)
        assertEquals(0, repository.entriesCalls)
    }

    private fun entry(id: String, size: Long, accessedAt: Long) = CacheEntry(
        key = ChapterBlobKey(
            ChapterBlobNamespace.AUTOMATIC_CACHE,
            ChapterReleaseId(id),
            "fingerprint-$id",
        ),
        checksum = app.openstory.downloads.blob.BlobChecksum.sha256(id.encodeToByteArray()),
        sizeBytes = size,
        lastAccessedAtEpochMillis = accessedAt,
    )
}

private class CountingCacheRepository(
    private val usageBytes: Long,
    private val lru: List<CacheEntry> = emptyList(),
) : CacheRepository {
    var snapshotCalls = 0
    var entriesCalls = 0
    var commitCalls = 0

    override suspend fun entries(): List<CacheEntry> {
        entriesCalls++
        return lru
    }

    override suspend fun quotaSnapshot(quotaBytes: Long): CacheQuotaSnapshot {
        snapshotCalls++
        val entries = if (usageBytes > quotaBytes) lru else emptyList()
        return CacheQuotaSnapshot(usageBytes, entries)
    }

    override suspend fun upsert(entry: CacheEntry) = Unit
    override suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long) = Unit
    override suspend fun commitEviction(keys: List<ChapterBlobKey>): List<ChapterBlobKey> {
        commitCalls++
        return keys
    }
}

private class EntriesOnlyCacheRepository(
    private val values: List<CacheEntry>,
) : CacheRepository {
    var entriesCalls = 0

    override suspend fun entries(): List<CacheEntry> {
        entriesCalls++
        return values
    }

    override suspend fun upsert(entry: CacheEntry) = Unit
    override suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long) = Unit
    override suspend fun commitEviction(keys: List<ChapterBlobKey>): List<ChapterBlobKey> = keys
}

private object NoopBlobStore : ChapterBlobStore {
    override suspend fun read(key: ChapterBlobKey): ChapterBlob? = null
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
    override suspend fun delete(key: ChapterBlobKey) = Unit
}
