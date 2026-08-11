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
        var retainedBytes = automaticEntries.sumOf(CacheEntry::sizeBytes)
        val keys = buildList {
            automaticEntries
                .asSequence()
                .filterNot { entry -> entry.isProtected(progressProtectedReleaseIds) }
                .sortedWith(compareBy(CacheEntry::lastAccessedAtEpochMillis).thenBy { it.key.releaseId.value })
                .forEach { entry ->
                    if (retainedBytes > quotaBytes) {
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
