package app.openstory.downloads.cache

import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CacheEvictionPolicyTest {
    @Test
    fun `evicts least recently accessed cache entries until usage meets quota`() {
        val oldest = entry("oldest", sizeBytes = 40, lastAccessed = 1)
        val middle = entry("middle", sizeBytes = 40, lastAccessed = 2)
        val newest = entry("newest", sizeBytes = 40, lastAccessed = 3)

        val plan = CacheEvictionPolicy.plan(
            entries = listOf(newest, oldest, middle),
            quotaBytes = 50,
            progressProtectedReleaseIds = emptySet(),
        )

        assertEquals(listOf(oldest.key, middle.key), plan.keys)
        assertEquals(40, plan.retainedBytes)
    }

    @Test
    fun `never evicts pinned current progress protected or explicit downloads`() {
        val pinned = entry("pinned", sizeBytes = 20, lastAccessed = 1, pinned = true)
        val current = entry("current", sizeBytes = 20, lastAccessed = 2, current = true)
        val progress = entry("progress", sizeBytes = 20, lastAccessed = 3)
        val explicit = entry(
            id = "explicit",
            sizeBytes = 20,
            lastAccessed = 4,
            namespace = ChapterBlobNamespace.EXPLICIT_DOWNLOAD,
        )
        val removable = entry("removable", sizeBytes = 20, lastAccessed = 5)

        val plan = CacheEvictionPolicy.plan(
            entries = listOf(pinned, current, progress, explicit, removable),
            quotaBytes = 0,
            progressProtectedReleaseIds = setOf(progress.key.releaseId),
        )

        assertEquals(listOf(removable.key), plan.keys)
        assertEquals(60, plan.retainedBytes)
        assertTrue(plan.protectedBytesExceedQuota)
    }

    @Test
    fun `does not evict when automatic cache already fits quota`() {
        val entry = entry("cached", sizeBytes = 20, lastAccessed = 1)

        val plan = CacheEvictionPolicy.plan(
            entries = listOf(entry),
            quotaBytes = 20,
            progressProtectedReleaseIds = emptySet(),
        )

        assertTrue(plan.keys.isEmpty())
        assertEquals(20, plan.retainedBytes)
    }

    private fun entry(
        id: String,
        sizeBytes: Long,
        lastAccessed: Long,
        namespace: ChapterBlobNamespace = ChapterBlobNamespace.AUTOMATIC_CACHE,
        pinned: Boolean = false,
        current: Boolean = false,
    ) = CacheEntry(
        key = ChapterBlobKey(namespace, ChapterReleaseId(id), "fingerprint-$id"),
        checksum = BlobChecksum.sha256(id.encodeToByteArray()),
        sizeBytes = sizeBytes,
        lastAccessedAtEpochMillis = lastAccessed,
        pinned = pinned,
        current = current,
    )
}
