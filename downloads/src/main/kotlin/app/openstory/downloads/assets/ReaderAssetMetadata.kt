package app.openstory.downloads.assets

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.reader.assets.ReaderAssetIdentityHash
import app.openstory.reader.assets.ReaderAssetIdentityMode
import app.openstory.reader.assets.ReaderAssetKeyHash
import app.openstory.reader.assets.ReaderAssetKeySchemaVersion
import app.openstory.reader.assets.ReaderAssetPersistenceMode
import app.openstory.reader.assets.ReaderAssetSourceNamespace
import app.openstory.reader.assets.ReaderContentVariant
import app.openstory.reader.assets.ReaderImageSetNamespace

data class ReaderAssetMetadata(
    val logicalAssetKeyHash: ReaderAssetKeyHash,
    val keySchemaVersion: ReaderAssetKeySchemaVersion,
    val storyId: StoryId,
    val canonicalChapterId: CanonicalChapterId,
    val chapterReleaseId: ChapterReleaseId,
    val sourceNamespace: ReaderAssetSourceNamespace,
    val securityScopeHash: String?,
    val contentVariant: ReaderContentVariant,
    val identityMode: ReaderAssetIdentityMode,
    val persistenceMode: ReaderAssetPersistenceMode,
    val imageSetNamespaceHash: ReaderImageSetNamespace,
    val pageIdentityHash: ReaderAssetIdentityHash,
    val pageOrdinal: Int,
    val blobId: String,
    val byteSize: Long,
    val localBlobChecksum: BlobChecksum,
    val sourceIntegrityHash: String?,
    val createdAtEpochMillis: Long,
    val lastAccessedAtEpochMillis: Long,
    val lastConsumedAtEpochMillis: Long?,
) {
    init {
        require(pageOrdinal >= 0)
        require(blobId.isNotBlank())
        require(byteSize >= 0L)
        require(securityScopeHash == null || SHA256.matches(securityScopeHash))
        require(sourceIntegrityHash == null || SHA256.matches(sourceIntegrityHash))
        require(createdAtEpochMillis >= 0L)
        require(lastAccessedAtEpochMillis >= 0L)
        require(lastConsumedAtEpochMillis == null || lastConsumedAtEpochMillis >= 0L)
        require(persistenceMode == ReaderAssetPersistenceMode.DURABLE_AUTOMATIC)
    }

    private companion object {
        val SHA256 = Regex("[0-9a-f]{64}")
    }
}
