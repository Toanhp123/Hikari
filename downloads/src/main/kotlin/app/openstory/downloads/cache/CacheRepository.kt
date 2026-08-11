package app.openstory.downloads.cache

import app.openstory.downloads.blob.ChapterBlobKey

interface CacheRepository {
    suspend fun entries(): List<CacheEntry>

    suspend fun upsert(entry: CacheEntry)

    suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long)

    /** Removes only committed automatic-cache metadata and returns the keys actually removed. */
    suspend fun commitEviction(keys: List<ChapterBlobKey>): List<ChapterBlobKey>
}
