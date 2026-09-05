package app.openstory.cache

import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.assets.ReaderAssetBlobId
import app.openstory.downloads.assets.ReaderAssetBlobReadLease
import app.openstory.downloads.assets.ReaderAssetBlobStore
import app.openstory.downloads.assets.ReaderAssetBlobWriteResult
import app.openstory.downloads.assets.ReaderAssetMetadata
import app.openstory.downloads.assets.ReaderAssetMetadataRepository
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.downloads.blob.ChapterBlob
import app.openstory.downloads.blob.ChapterBlobKey
import app.openstory.downloads.blob.ChapterBlobNamespace
import app.openstory.downloads.blob.ChapterBlobStore
import app.openstory.downloads.cache.AutomaticCacheBudgetCoordinator
import app.openstory.downloads.cache.CacheEntry
import app.openstory.downloads.cache.CacheRepository
import app.openstory.reader.assets.ReaderAssetIdentityHash
import app.openstory.reader.assets.ReaderAssetIdentityMode
import app.openstory.reader.assets.ReaderAssetKeyHash
import app.openstory.reader.assets.ReaderAssetKeySchemaVersion
import app.openstory.reader.assets.ReaderAssetPersistenceMode
import app.openstory.reader.assets.ReaderAssetSourceNamespace
import app.openstory.reader.assets.ReaderContentVariant
import app.openstory.reader.assets.ReaderImageSetNamespace
import kotlinx.coroutines.CoroutineScope

internal class AutomaticCacheTestFixture(
    initialQuotaBytes: Long,
    reconciliationScope: CoroutineScope,
) {
    val documents = InMemoryCacheRepository()
    val assets = InMemoryReaderAssetMetadataRepository()
    val coordinator = AutomaticCacheBudgetCoordinator(
        cacheRepository = documents,
        documentBlobStore = NoopChapterBlobStore,
        readerAssetMetadataRepository = assets,
        readerAssetBlobStore = NoopReaderAssetBlobStore,
        initialQuotaBytes = initialQuotaBytes,
        reconciliationScope = reconciliationScope,
    )

    suspend fun addDocument(releaseId: ChapterReleaseId, bytes: Long, accessedAt: Long = 0L) {
        documents.upsert(
            CacheEntry(
                key = ChapterBlobKey(
                    ChapterBlobNamespace.AUTOMATIC_CACHE,
                    releaseId,
                    "fingerprint:${releaseId.value}",
                ),
                checksum = BlobChecksum.sha256(releaseId.value.encodeToByteArray()),
                sizeBytes = bytes,
                lastAccessedAtEpochMillis = accessedAt,
            ),
        )
    }

    suspend fun addAsset(id: Int, bytes: Long) {
        assets.upsert(
            ReaderAssetMetadata(
                logicalAssetKeyHash = ReaderAssetKeyHash(hash(id)),
                keySchemaVersion = ReaderAssetKeySchemaVersion(1),
                storyId = StoryId("story"),
                canonicalChapterId = CanonicalChapterId("chapter"),
                chapterReleaseId = ChapterReleaseId("release:image"),
                sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(PluginId("source")),
                securityScopeHash = null,
                contentVariant = ReaderContentVariant.ORIGINAL,
                identityMode = ReaderAssetIdentityMode.TRUSTED_STABLE,
                persistenceMode = ReaderAssetPersistenceMode.DURABLE_AUTOMATIC,
                imageSetNamespaceHash = ReaderImageSetNamespace(hash(id + 100)),
                pageIdentityHash = ReaderAssetIdentityHash(hash(id + 200)),
                pageOrdinal = id,
                blobId = hash(id + 300),
                byteSize = bytes,
                localBlobChecksum = BlobChecksum.sha256(byteArrayOf(id.toByte())),
                sourceIntegrityHash = null,
                createdAtEpochMillis = 0L,
                lastAccessedAtEpochMillis = 0L,
                lastConsumedAtEpochMillis = null,
            ),
        )
    }
}

internal class InMemoryCacheRepository : CacheRepository {
    private val values = linkedMapOf<ChapterBlobKey, CacheEntry>()

    override suspend fun entries(): List<CacheEntry> = values.values.toList()
    override suspend fun upsert(entry: CacheEntry) {
        values[entry.key] = entry
    }
    override suspend fun touch(key: ChapterBlobKey, accessedAtEpochMillis: Long) = Unit
    override suspend fun commitEviction(keys: List<ChapterBlobKey>): List<ChapterBlobKey> =
        keys.mapNotNull { key -> values.remove(key)?.key }
}

internal class InMemoryReaderAssetMetadataRepository : ReaderAssetMetadataRepository {
    private val values = linkedMapOf<ReaderAssetKeyHash, ReaderAssetMetadata>()

    override suspend fun upsert(metadata: ReaderAssetMetadata) {
        values[metadata.logicalAssetKeyHash] = metadata
    }
    override suspend fun find(keys: Set<ReaderAssetKeyHash>) = values.filterKeys { it in keys }
    override suspend fun all() = values.values.toList()
    override suspend fun usageBytes() = values.values.sumOf(ReaderAssetMetadata::byteSize)
    override suspend fun detach(key: ReaderAssetKeyHash): ReaderAssetMetadata? = values.remove(key)
    override suspend fun detachAll(): List<ReaderAssetMetadata> = values.values.toList().also { values.clear() }
    override suspend fun detachSource(sourceNamespace: ReaderAssetSourceNamespace) =
        detachMatching { it.sourceNamespace == sourceNamespace }
    override suspend fun detachAccount(sourceNamespace: ReaderAssetSourceNamespace, securityScopeHash: String) =
        detachMatching { it.sourceNamespace == sourceNamespace && it.securityScopeHash == securityScopeHash }
    override suspend fun detachAllAccountsForSource(sourceNamespace: ReaderAssetSourceNamespace) =
        detachMatching { it.sourceNamespace == sourceNamespace && it.securityScopeHash != null }
    override suspend fun updateLastAccessed(key: ReaderAssetKeyHash, epochMillis: Long) = Unit
    override suspend fun updateLastConsumed(key: ReaderAssetKeyHash, epochMillis: Long) = Unit

    private fun detachMatching(predicate: (ReaderAssetMetadata) -> Boolean): List<ReaderAssetMetadata> =
        values.values.filter(predicate).also { rows ->
            rows.forEach { values.remove(it.logicalAssetKeyHash) }
        }
}

private object NoopChapterBlobStore : ChapterBlobStore {
    override suspend fun read(key: ChapterBlobKey): ChapterBlob? = null
    override suspend fun write(key: ChapterBlobKey, blob: ChapterBlob) = Unit
    override suspend fun delete(key: ChapterBlobKey) = Unit
}

private object NoopReaderAssetBlobStore : ReaderAssetBlobStore {
    override suspend fun writeAtomic(id: ReaderAssetBlobId, bytes: ByteArray): ReaderAssetBlobWriteResult =
        error("not used")
    override suspend fun open(id: ReaderAssetBlobId): ReaderAssetBlobReadLease? = null
    override suspend fun exists(id: ReaderAssetBlobId) = false
    override suspend fun hasActiveReadLease(id: ReaderAssetBlobId): Boolean = false
    override suspend fun tryDeleteNowIfUnleased(id: ReaderAssetBlobId) = true
    override suspend fun deleteWhenUnleased(id: ReaderAssetBlobId) = Unit
}

private fun hash(seed: Int): String = seed.toString(16).padStart(64, '0').takeLast(64)
