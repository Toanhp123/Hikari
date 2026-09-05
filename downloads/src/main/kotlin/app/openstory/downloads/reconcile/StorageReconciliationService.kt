package app.openstory.downloads.reconcile

import app.openstory.downloads.assets.ReaderAssetBlobId
import app.openstory.downloads.blob.ChapterBlobKey

interface StorageReconciliationRepository {
    suspend fun storageEntries(): List<StorageMetadataEntry>

    suspend fun commit(plan: StorageMetadataRepairPlan, updatedAtEpochMillis: Long)
}

fun interface StorageWriteAdmission {
    fun canStore(payloadBytes: Long): Boolean

    companion object {
        val ALLOW_ALL = StorageWriteAdmission { payloadBytes -> payloadBytes >= 0 }
    }
}

interface StorageReconciliationInventory : StorageWriteAdmission {
    suspend fun scan(
        expectedKeys: Set<ChapterBlobKey>,
        staleBeforeEpochMillis: Long,
    ): StorageInventorySnapshot

    suspend fun delete(artifacts: List<StorageArtifactId>)
}

interface ReaderAssetReconciliationStore {
    suspend fun reconciliationEntries(): List<ReaderAssetReconciliationEntry>
    suspend fun activeGenerationBlobIds(): Set<ReaderAssetBlobId>
    suspend fun detachMissingGeneration(expected: ReaderAssetReconciliationEntry): Boolean
}

interface ReaderAssetReconciliationInventory {
    suspend fun scanReaderAssets(
        expectedBlobIds: Set<ReaderAssetBlobId>,
        staleBeforeEpochMillis: Long,
        limit: Int,
    ): ReaderAssetStorageInventorySnapshot

    suspend fun deleteReaderAssetArtifacts(artifacts: List<StorageArtifactId>)
}

class StorageReconciliationService(
    private val repository: StorageReconciliationRepository,
    private val inventory: StorageReconciliationInventory,
    private val activeWriteWindowMillis: Long,
    private val now: () -> Long,
    private val readerAssets: ReaderAssetReconciliationStore? = null,
    private val readerAssetInventory: ReaderAssetReconciliationInventory? = null,
    private val readerAssetScanLimit: Int = DEFAULT_READER_ASSET_SCAN_LIMIT,
) {
    init {
        require(activeWriteWindowMillis >= 0) { "Active-write window must not be negative." }
        require(readerAssetScanLimit > 0) { "Reader asset reconciliation limit must be positive." }
        require((readerAssets == null) == (readerAssetInventory == null)) {
            "Reader asset reconciliation store and inventory must be configured together."
        }
    }

    suspend fun reconcile(): StorageReconciliationReport {
        val currentTime = now()
        val staleBefore = currentTime - activeWriteWindowMillis
        val metadata = repository.storageEntries()
        val expectedKeys = metadata
            .filter(StorageMetadataEntry::representsStoredBlob)
            .mapTo(mutableSetOf(), StorageMetadataEntry::key)
        val snapshot = inventory.scan(
            expectedKeys = expectedKeys,
            staleBeforeEpochMillis = staleBefore,
        )
        val plan = buildReconciliationPlan(
            metadata = metadata,
            inventory = snapshot,
            staleBeforeEpochMillis = staleBefore,
        )

        val initialReaderEntries = readerAssets?.reconciliationEntries().orEmpty()
        val initialReaderSnapshot = scanReaderAssets(initialReaderEntries, staleBefore)
        val removedReaderMetadata = if (initialReaderSnapshot?.scanComplete == true) {
            val readerAssetStore = checkNotNull(readerAssets)
            var removedCount = 0
            for (entry in initialReaderEntries) {
                if (removedCount >= readerAssetScanLimit) break
                if (entry.blobId in initialReaderSnapshot.presentBlobIds) continue
                if (readerAssetStore.detachMissingGeneration(entry)) {
                    removedCount += 1
                }
            }
            removedCount
        } else {
            0
        }

        repository.commit(plan.metadataRepairs, currentTime)

        val currentMetadata = repository.storageEntries()
        val currentExpectedKeys = currentMetadata
            .filter(StorageMetadataEntry::representsStoredBlob)
            .mapTo(mutableSetOf(), StorageMetadataEntry::key)
        val revalidated = inventory.scan(
            expectedKeys = currentExpectedKeys,
            staleBeforeEpochMillis = staleBefore,
        )
        val artifactsToDelete = (
            revalidated.orphanArtifacts + revalidated.interruptedWriteArtifacts
        ).distinct()
        inventory.delete(artifactsToDelete)

        val revalidatedReaderEntries = readerAssets?.reconciliationEntries().orEmpty()
        val revalidatedReader = scanReaderAssets(revalidatedReaderEntries, staleBefore)
        val readerArtifactsToDelete = revalidatedReader
            ?.let { it.orphanArtifacts + it.interruptedWriteArtifacts }
            .orEmpty()
            .distinct()
            .take(readerAssetScanLimit)
        if (readerArtifactsToDelete.isNotEmpty()) {
            readerAssetInventory?.deleteReaderAssetArtifacts(readerArtifactsToDelete)
        }

        return StorageReconciliationReport(
            removedMetadataCount = plan.metadataRepairs.removedMetadata.size,
            failedDownloadCount = plan.metadataRepairs.failedDownloads.size,
            deletedArtifactCount = artifactsToDelete.size,
            removedReaderAssetMetadataCount = removedReaderMetadata,
            deletedReaderAssetArtifactCount = readerArtifactsToDelete.size,
        )
    }

    private suspend fun scanReaderAssets(
        entries: List<ReaderAssetReconciliationEntry>,
        staleBeforeEpochMillis: Long,
    ): ReaderAssetStorageInventorySnapshot? {
        val expectedBlobIds = entries.mapTo(linkedSetOf(), ReaderAssetReconciliationEntry::blobId)
        readerAssets?.activeGenerationBlobIds()?.let(expectedBlobIds::addAll)
        return readerAssetInventory?.scanReaderAssets(
            expectedBlobIds = expectedBlobIds,
            staleBeforeEpochMillis = staleBeforeEpochMillis,
            limit = readerAssetScanLimit,
        )
    }

    fun canStore(payloadBytes: Long): Boolean =
        payloadBytes >= 0 && inventory.canStore(payloadBytes)

    private companion object {
        const val DEFAULT_READER_ASSET_SCAN_LIMIT = 64
    }
}
