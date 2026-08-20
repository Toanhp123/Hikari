package app.openstory.downloads.cache

import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace

data class CacheQuotaSnapshot(
    val usageBytes: Long,
    val entriesByLru: List<CacheEntry>,
)

interface CacheRepository {
    suspend fun entries(): List<CacheEntry>

    suspend fun quotaSnapshot(quotaBytes: Long): CacheQuotaSnapshot {
        require(quotaBytes >= 0) { "Cache quota must not be negative." }
        val automaticEntries = entries().filter { entry ->
            entry.key.namespace == ChapterBlobNamespace.AUTOMATIC_CACHE
        }
        val usage = automaticEntries.sumOf(CacheEntry::sizeBytes)
        val candidates = if (usage > quotaBytes) {
            automaticEntries.sortedWith(
                compareBy(CacheEntry::lastAccessedAtEpochMillis).thenBy { it.key.releaseId.value },
            )
        } else {
            emptyList()
        }
        return CacheQuotaSnapshot(usage, candidates)
    }

    suspend fun upsert(entry: CacheEntry)

    suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long)

    /** Removes only committed automatic-cache metadata and returns the keys actually removed. */
    suspend fun commitEviction(keys: List<ChapterBlobKey>): List<ChapterBlobKey>
}
