package app.openstory.downloads.cache

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlobNamespace

object CacheEvictionPolicy {
    fun plan(
        entries: List<CacheEntry>,
        quotaBytes: Long,
        progressProtectedReleaseIds: Set<ChapterReleaseId>,
    ): CacheEvictionPlan {
        require(quotaBytes >= 0) { "Cache quota must not be negative." }
        val automaticEntries = entries.filter { it.key.namespace == ChapterBlobNamespace.AUTOMATIC_CACHE }
        return planOrdered(
            entriesByLru = automaticEntries.sortedWith(
                compareBy(CacheEntry::lastAccessedAtEpochMillis).thenBy { it.key.releaseId.value },
            ),
            usageBytes = automaticEntries.sumOf(CacheEntry::sizeBytes),
            quotaBytes = quotaBytes,
            progressProtectedReleaseIds = progressProtectedReleaseIds,
        )
    }

    internal fun planOrdered(
        entriesByLru: List<CacheEntry>,
        usageBytes: Long,
        quotaBytes: Long,
        progressProtectedReleaseIds: Set<ChapterReleaseId>,
    ): CacheEvictionPlan {
        require(usageBytes >= 0) { "Cache usage must not be negative." }
        require(quotaBytes >= 0) { "Cache quota must not be negative." }
        var retainedBytes = usageBytes
        val keys = buildList {
            entriesByLru.forEach { entry ->
                if (retainedBytes > quotaBytes && !entry.isProtected(progressProtectedReleaseIds)) {
                    add(entry.key)
                    retainedBytes -= entry.sizeBytes
                }
            }
        }
        return CacheEvictionPlan(keys, retainedBytes, quotaBytes)
    }
}

private fun CacheEntry.isProtected(progressProtectedReleaseIds: Set<ChapterReleaseId>): Boolean =
    pinned || current || key.releaseId in progressProtectedReleaseIds
