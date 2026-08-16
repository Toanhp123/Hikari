package app.openstory.downloads.cache

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore

class CacheService(
    private val repository: CacheRepository,
    private val blobStore: ChapterBlobStore,
) {
    suspend fun store(
        key: ChapterBlobKey,
        blob: ChapterBlob,
        accessedAtEpochMillis: Long,
        pinned: Boolean = false,
        current: Boolean = false,
    ) {
        require(key.namespace == ChapterBlobNamespace.AUTOMATIC_CACHE) {
            "CacheService only stores automatic-cache blobs."
        }
        blobStore.write(key, blob)
        repository.upsert(
            CacheEntry(
                key = key,
                checksum = blob.checksum,
                sizeBytes = blob.sizeBytes.toLong(),
                lastAccessedAtEpochMillis = accessedAtEpochMillis,
                pinned = pinned,
                current = current,
            ),
        )
    }

    suspend fun enforceQuota(
        quotaBytes: Long,
        progressProtectedReleaseIds: Set<ChapterReleaseId>,
    ): CacheEvictionPlan {
        val plan = CacheEvictionPolicy.plan(repository.entries(), quotaBytes, progressProtectedReleaseIds)
        repository.commitEviction(plan.keys).forEach { blobStore.delete(it) }
        return plan
    }
}
