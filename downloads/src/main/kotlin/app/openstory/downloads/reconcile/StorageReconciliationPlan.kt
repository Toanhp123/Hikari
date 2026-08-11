package app.openstory.downloads.reconcile

import app.openstory.downloads.DownloadState
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace

@JvmInline
value class StorageArtifactId(val value: String) {
    init {
        require(value.isNotBlank()) { "Storage artifact ID must not be blank." }
        require('/' !in value && '\\' !in value) { "Storage artifact ID must be opaque." }
    }
}

data class StorageInventorySnapshot(
    val presentKeys: Set<ChapterBlobKey> = emptySet(),
    val orphanArtifacts: List<StorageArtifactId> = emptyList(),
    val interruptedWriteArtifacts: List<StorageArtifactId> = emptyList(),
)

data class StorageMetadataEntry(
    val key: ChapterBlobKey,
    val downloadState: DownloadState?,
    val updatedAtEpochMillis: Long,
) {
    val representsStoredBlob: Boolean
        get() = key.namespace == ChapterBlobNamespace.AUTOMATIC_CACHE ||
            downloadState == DownloadState.COMPLETED
}

data class StorageDownloadFailure(
    val key: ChapterBlobKey,
    val reason: String,
)

data class StorageMetadataRepairPlan(
    val removedMetadata: List<ChapterBlobKey> = emptyList(),
    val failedDownloads: List<StorageDownloadFailure> = emptyList(),
)

data class StorageReconciliationPlan(
    val metadataRepairs: StorageMetadataRepairPlan,
    val artifactsToDelete: List<StorageArtifactId>,
)

data class StorageReconciliationReport(
    val removedMetadataCount: Int,
    val failedDownloadCount: Int,
    val deletedArtifactCount: Int,
)

internal fun buildReconciliationPlan(
    metadata: List<StorageMetadataEntry>,
    inventory: StorageInventorySnapshot,
    staleBeforeEpochMillis: Long,
): StorageReconciliationPlan {
    val staleDownloads = metadata
        .filter {
            it.key.namespace == ChapterBlobNamespace.EXPLICIT_DOWNLOAD &&
                it.downloadState == DownloadState.RUNNING &&
                it.updatedAtEpochMillis < staleBeforeEpochMillis
        }
        .map { StorageDownloadFailure(it.key, INTERRUPTED_REASON) }
    val staleKeys = staleDownloads.mapTo(mutableSetOf(), StorageDownloadFailure::key)
    val missing = metadata.filter { it.representsStoredBlob && it.key !in inventory.presentKeys }
    val missingCache = missing
        .filter { it.key.namespace == ChapterBlobNamespace.AUTOMATIC_CACHE }
        .map(StorageMetadataEntry::key)
    val missingDownloads = missing
        .filter { it.key.namespace == ChapterBlobNamespace.EXPLICIT_DOWNLOAD && it.key !in staleKeys }
        .map { StorageDownloadFailure(it.key, INTEGRITY_MISSING_REASON) }
    return StorageReconciliationPlan(
        metadataRepairs = StorageMetadataRepairPlan(
            removedMetadata = missingCache.distinct(),
            failedDownloads = (staleDownloads + missingDownloads).distinctBy(StorageDownloadFailure::key),
        ),
        artifactsToDelete = (
            inventory.orphanArtifacts + inventory.interruptedWriteArtifacts
        ).distinct(),
    )
}

private const val INTEGRITY_MISSING_REASON = "download.integrity_missing"
private const val INTERRUPTED_REASON = "download.interrupted"
