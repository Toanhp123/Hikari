package app.openstory.downloads.cache

import app.openstory.downloads.assets.ReaderAssetBlobId
import app.openstory.downloads.assets.ReaderAssetBlobStore
import app.openstory.downloads.assets.ReaderAssetMetadata
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobStore
import kotlinx.coroutines.CancellationException

internal class AutomaticCacheBlobMaintenance(
    private val documentBlobStore: ChapterBlobStore,
    private val readerAssetBlobStore: ReaderAssetBlobStore,
) {
    suspend fun hasActiveReadLease(metadata: ReaderAssetMetadata): Boolean = try {
        readerAssetBlobStore.hasActiveReadLease(ReaderAssetBlobId(metadata.blobId))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        true
    }

    suspend fun tryDeleteImageNowIfUnleased(metadata: ReaderAssetMetadata): Boolean = try {
        readerAssetBlobStore.tryDeleteNowIfUnleased(ReaderAssetBlobId(metadata.blobId))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    suspend fun deleteDocumentBestEffort(key: ChapterBlobKey): Boolean = try {
        documentBlobStore.deleteIfPresent(key)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    suspend fun deleteImageWhenUnleasedBestEffort(metadata: ReaderAssetMetadata) {
        deleteImageBlobWhenUnleasedBestEffort(ReaderAssetBlobId(metadata.blobId))
    }

    suspend fun deleteImageBlobWhenUnleasedBestEffort(blobId: ReaderAssetBlobId) {
        try {
            readerAssetBlobStore.deleteWhenUnleased(blobId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Detached generation cleanup is best effort; bounded reconciliation can retry orphans.
        }
    }
}
