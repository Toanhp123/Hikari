package app.openstory.downloads.reconcile

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

class StorageReconciliationService(
    private val repository: StorageReconciliationRepository,
    private val inventory: StorageReconciliationInventory,
    private val activeWriteWindowMillis: Long,
    private val now: () -> Long,
) {
    init {
        require(activeWriteWindowMillis >= 0) { "Active-write window must not be negative." }
    }

    suspend fun reconcile(): StorageReconciliationReport {
        val currentTime = now()
        val metadata = repository.storageEntries()
        val expectedKeys = metadata
            .filter(StorageMetadataEntry::representsStoredBlob)
            .mapTo(mutableSetOf(), StorageMetadataEntry::key)
        val snapshot = inventory.scan(
            expectedKeys = expectedKeys,
            staleBeforeEpochMillis = currentTime - activeWriteWindowMillis,
        )
        val plan = buildReconciliationPlan(
            metadata = metadata,
            inventory = snapshot,
            staleBeforeEpochMillis = currentTime - activeWriteWindowMillis,
        )
        repository.commit(plan.metadataRepairs, currentTime)
        inventory.delete(plan.artifactsToDelete)
        return StorageReconciliationReport(
            removedMetadataCount = plan.metadataRepairs.removedMetadata.size,
            failedDownloadCount = plan.metadataRepairs.failedDownloads.size,
            deletedArtifactCount = plan.artifactsToDelete.size,
        )
    }

    fun canStore(payloadBytes: Long): Boolean =
        payloadBytes >= 0 && inventory.canStore(payloadBytes)
}
