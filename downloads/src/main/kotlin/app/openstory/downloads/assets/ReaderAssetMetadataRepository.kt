package app.openstory.downloads.assets

import app.openstory.reader.assets.ReaderAssetKeyHash
import app.openstory.reader.assets.ReaderAssetSourceNamespace

interface ReaderAssetMetadataRepository {
    suspend fun upsert(metadata: ReaderAssetMetadata)
    suspend fun find(keys: Set<ReaderAssetKeyHash>): Map<ReaderAssetKeyHash, ReaderAssetMetadata>
    suspend fun all(): List<ReaderAssetMetadata>
    suspend fun usageBytes(): Long
    suspend fun detach(key: ReaderAssetKeyHash): ReaderAssetMetadata?
    suspend fun detachAll(): List<ReaderAssetMetadata>
    suspend fun detachSource(sourceNamespace: ReaderAssetSourceNamespace): List<ReaderAssetMetadata>
    suspend fun detachAccount(
        sourceNamespace: ReaderAssetSourceNamespace,
        securityScopeHash: String,
    ): List<ReaderAssetMetadata>
    suspend fun detachAllAccountsForSource(
        sourceNamespace: ReaderAssetSourceNamespace,
    ): List<ReaderAssetMetadata>
    suspend fun updateLastAccessed(key: ReaderAssetKeyHash, epochMillis: Long)
    suspend fun updateLastConsumed(key: ReaderAssetKeyHash, epochMillis: Long)
}
