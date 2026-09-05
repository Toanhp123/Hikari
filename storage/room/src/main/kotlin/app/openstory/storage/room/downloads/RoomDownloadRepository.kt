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
import app.openstory.downloads.cache.CacheQuotaSnapshot
import app.openstory.downloads.cache.CacheRepository
import app.openstory.downloads.reconcile.StorageDownloadFailure
import app.openstory.downloads.reconcile.StorageMetadataEntry
import app.openstory.downloads.reconcile.StorageMetadataRepairPlan
import app.openstory.downloads.reconcile.StorageReconciliationRepository
import app.openstory.downloads.reader.ReaderCacheMetadata
import app.openstory.downloads.reader.ReaderCacheMetadataSource
import app.openstory.storage.room.OpenStoryDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomDownloadRepository internal constructor(
    private val database: OpenStoryDatabase,
    private val dao: DownloadDao,
) : CacheRepository, DownloadRepository, StorageReconciliationRepository, ReaderCacheMetadataSource {
    constructor(database: OpenStoryDatabase) : this(database, database.downloadDao())

    override fun observeAll(): Flow<List<DownloadRecord>> =
        dao.observeAllDownloads().map { entries -> entries.mapNotNull(ChapterStorageEntryEntity::toDownloadRecord) }

    override fun observeCompletedCount(): Flow<Int> = dao.observeCompletedDownloadCount()

    override suspend fun entries(): List<CacheEntry> = dao.storedEntries().map { it.toCacheEntry() }

    override suspend fun automaticUsageBytes(): Long = dao.automaticCacheUsageBytes()

    override suspend fun quotaSnapshot(quotaBytes: Long): CacheQuotaSnapshot =
        database.withTransaction {
            require(quotaBytes >= 0) { "Cache quota must not be negative." }
            val usage = dao.automaticCacheUsageBytes()
            CacheQuotaSnapshot(
                usageBytes = usage,
                entriesByLru = if (usage > quotaBytes) {
                    dao.automaticCacheEntriesByLru().map { entity -> entity.toCacheEntry() }
                } else {
                    emptyList()
                },
            )
        }

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
                val deleted = dao.deleteAutomaticCache(key.releaseId.value, key.contentFingerprint)
                key.takeIf { deleted > 0 }
            }
        }

    override suspend fun detachAutomatic(key: ChapterBlobKey): CacheEntry? =
        database.withTransaction {
            if (key.namespace != ChapterBlobNamespace.AUTOMATIC_CACHE) return@withTransaction null
            val entity = dao.find(key.namespace.name, key.releaseId.value, key.contentFingerprint)
                ?.takeIf { it.namespace == ChapterBlobNamespace.AUTOMATIC_CACHE.name }
                ?: return@withTransaction null
            dao.delete(entity)
            entity.toCacheEntry()
        }

    override suspend fun detachAllAutomatic(): List<CacheEntry> = database.withTransaction {
        dao.automaticCacheEntriesByLru()
            .also { dao.deleteAllAutomaticCache() }
            .map(ChapterStorageEntryEntity::toCacheEntry)
    }

    override suspend fun entriesFor(releaseIds: Set<ChapterReleaseId>): List<ReaderCacheMetadata> {
        if (releaseIds.isEmpty()) return emptyList()
        return dao.readerEntries(releaseIds.map(ChapterReleaseId::value).sorted())
            .map(ChapterStorageEntryEntity::toReaderCacheMetadata)
    }

    override suspend fun find(releaseId: ChapterReleaseId): DownloadRecord? =
        dao.findDownload(releaseId.value)?.toDownloadRecord()

    override fun observe(releaseId: ChapterReleaseId): Flow<DownloadRecord?> =
        dao.observeDownload(releaseId.value).map { it?.toDownloadRecord() }

    override suspend fun save(record: DownloadRecord) {
        database.withTransaction {
            dao.deleteDownload(record.key.releaseId.value)
            dao.upsert(record.toEntity())
        }
    }

    override suspend fun completeUnlessCancelled(record: DownloadRecord): Boolean =
        database.withTransaction {
            val current = dao.findDownload(record.key.releaseId.value)?.toDownloadRecord()
            if (current?.state == DownloadState.CANCELLED) {
                false
            } else {
                dao.deleteDownload(record.key.releaseId.value)
                dao.upsert(record.toEntity())
                true
            }
        }

    override suspend fun storageEntries(): List<StorageMetadataEntry> =
        dao.allEntries().map(ChapterStorageEntryEntity::toStorageMetadataEntry)

    override suspend fun commit(
        plan: StorageMetadataRepairPlan,
        updatedAtEpochMillis: Long,
    ) {
        database.withTransaction {
            plan.removedMetadata.forEach { key -> removeAutomaticMetadata(key) }
            plan.failedDownloads.forEach { failure ->
                failExplicitDownload(failure, updatedAtEpochMillis)
            }
        }
    }

    private suspend fun removeAutomaticMetadata(key: ChapterBlobKey) {
        if (key.namespace != ChapterBlobNamespace.AUTOMATIC_CACHE) return
        dao.find(key.namespace.name, key.releaseId.value, key.contentFingerprint)
            ?.takeIf { it.namespace == ChapterBlobNamespace.AUTOMATIC_CACHE.name }
            ?.let { dao.delete(it) }
    }

    private suspend fun failExplicitDownload(
        failure: StorageDownloadFailure,
        updatedAtEpochMillis: Long,
    ) {
        if (failure.key.namespace != ChapterBlobNamespace.EXPLICIT_DOWNLOAD) return
        val entity = dao.find(
            failure.key.namespace.name,
            failure.key.releaseId.value,
            failure.key.contentFingerprint,
        ) ?: return
        dao.upsert(
            entity.copy(
                checksum = null,
                sizeBytes = 0,
                pinned = false,
                downloadState = DownloadState.FAILED.name,
                failureReason = failure.reason,
                updatedAtEpochMillis = updatedAtEpochMillis,
            ),
        )
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
        key = ChapterBlobKey(
            ChapterBlobNamespace.valueOf(namespace),
            ChapterReleaseId(chapterReleaseId),
            contentFingerprint,
        ),
        state = state,
        checksum = checksum?.let(::BlobChecksum),
        sizeBytes = sizeBytes,
        failureReason = failureReason,
        attempt = attempt,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}

private fun ChapterStorageEntryEntity.toStorageMetadataEntry() = StorageMetadataEntry(
    key = ChapterBlobKey(
        ChapterBlobNamespace.valueOf(namespace),
        ChapterReleaseId(chapterReleaseId),
        contentFingerprint,
    ),
    downloadState = downloadState?.let(DownloadState::valueOf),
    updatedAtEpochMillis = updatedAtEpochMillis,
)

private fun ChapterStorageEntryEntity.toReaderCacheMetadata() = ReaderCacheMetadata(
    releaseId = ChapterReleaseId(chapterReleaseId),
    fingerprint = contentFingerprint,
    namespace = ChapterBlobNamespace.valueOf(namespace),
    checksumPresent = checksum != null,
    downloadState = downloadState?.let(DownloadState::valueOf),
    lastAccessedAtEpochMillis = lastAccessedAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
)
