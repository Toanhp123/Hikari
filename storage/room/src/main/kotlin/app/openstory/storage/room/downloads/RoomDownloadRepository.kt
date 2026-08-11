package app.openstory.storage.room.downloads

import androidx.room.withTransaction
import app.openstory.common.id.ChapterReleaseId
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.DownloadRecord
import app.openstory.downloads.DownloadRepository
import app.openstory.downloads.DownloadState
import app.openstory.downloads.cache.CacheEntry
import app.openstory.downloads.cache.CacheRepository
import app.openstory.storage.room.OpenStoryDatabase

class RoomDownloadRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: DownloadDao,
) : CacheRepository, DownloadRepository {
    constructor(database: OpenStoryDatabase) : this(database, database.downloadDao())

    override suspend fun entries(): List<CacheEntry> = dao.storedEntries().map { it.toCacheEntry() }

    override suspend fun upsert(entry: CacheEntry) {
        dao.upsert(entry.toEntity())
    }

    override suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long) {
        dao.touch(
            key.namespace.name,
            key.releaseId.value,
            key.contentFingerprint,
            accessedAtEpochMillis,
        )
    }

    override suspend fun commitEviction(keys: List<ChapterBlobKey>): List<ChapterBlobKey> =
        database.withTransaction {
            keys.mapNotNull { key ->
                if (key.namespace != ChapterBlobNamespace.AUTOMATIC_CACHE) return@mapNotNull null
                val entity = dao.find(key.namespace.name, key.releaseId.value, key.contentFingerprint)
                    ?: return@mapNotNull null
                if (entity.namespace != ChapterBlobNamespace.AUTOMATIC_CACHE.name) return@mapNotNull null
                dao.delete(entity)
                key
            }
        }

    override suspend fun find(key: ChapterBlobKey): DownloadRecord? =
        dao.find(key.namespace.name, key.releaseId.value, key.contentFingerprint)?.toDownloadRecord()

    override suspend fun save(record: DownloadRecord) {
        dao.upsert(record.toEntity())
    }
}

private fun CacheEntry.toEntity() = ChapterStorageEntryEntity(
    namespace = key.namespace.name,
    chapterReleaseId = key.releaseId.value,
    contentFingerprint = key.contentFingerprint,
    checksum = checksum.value,
    sizeBytes = sizeBytes,
    lastAccessedAtEpochMillis = lastAccessedAtEpochMillis,
    pinned = pinned,
    current = current,
    downloadState = null,
    failureReason = null,
    attempt = 0,
    updatedAtEpochMillis = lastAccessedAtEpochMillis,
)

private fun ChapterStorageEntryEntity.toCacheEntry() = CacheEntry(
    key = ChapterBlobKey(
        namespace = ChapterBlobNamespace.valueOf(namespace),
        releaseId = ChapterReleaseId(chapterReleaseId),
        contentFingerprint = contentFingerprint,
    ),
    checksum = BlobChecksum(requireNotNull(checksum)),
    sizeBytes = sizeBytes,
    lastAccessedAtEpochMillis = lastAccessedAtEpochMillis,
    pinned = pinned,
    current = current,
)

private fun DownloadRecord.toEntity() = ChapterStorageEntryEntity(
    namespace = key.namespace.name,
    chapterReleaseId = key.releaseId.value,
    contentFingerprint = key.contentFingerprint,
    checksum = checksum?.value,
    sizeBytes = sizeBytes,
    lastAccessedAtEpochMillis = updatedAtEpochMillis,
    pinned = state == DownloadState.COMPLETED,
    current = false,
    downloadState = state.name,
    failureReason = failureReason,
    attempt = attempt,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun ChapterStorageEntryEntity.toDownloadRecord(): DownloadRecord? {
    val state = downloadState?.let(DownloadState::valueOf) ?: return null
    return DownloadRecord(
        key = ChapterBlobKey(ChapterBlobNamespace.valueOf(namespace), ChapterReleaseId(chapterReleaseId), contentFingerprint),
        state = state,
        checksum = checksum?.let(::BlobChecksum),
        sizeBytes = sizeBytes,
        failureReason = failureReason,
        attempt = attempt,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
