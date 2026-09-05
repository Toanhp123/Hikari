package app.openstory.storage.room.readerassets

import androidx.room.withTransaction
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.common.id.StoryId
import app.openstory.downloads.assets.ReaderAssetMetadata
import app.openstory.downloads.assets.ReaderAssetMetadataRepository
import app.openstory.downloads.blob.BlobChecksum
import app.openstory.reader.assets.ReaderAssetIdentityHash
import app.openstory.reader.assets.ReaderAssetIdentityMode
import app.openstory.reader.assets.ReaderAssetKeyHash
import app.openstory.reader.assets.ReaderAssetKeySchemaVersion
import app.openstory.reader.assets.ReaderAssetPersistenceMode
import app.openstory.reader.assets.ReaderAssetSourceNamespace
import app.openstory.reader.assets.ReaderContentVariant
import app.openstory.reader.assets.ReaderImageSetNamespace
import app.openstory.storage.room.OpenStoryDatabase

class RoomReaderAssetMetadataRepository(
    private val database: OpenStoryDatabase,
) : ReaderAssetMetadataRepository {
    private val dao = database.readerAssetDao()

    override suspend fun upsert(metadata: ReaderAssetMetadata) {
        dao.upsert(metadata.toEntity())
    }

    override suspend fun find(
        keys: Set<ReaderAssetKeyHash>,
    ): Map<ReaderAssetKeyHash, ReaderAssetMetadata> {
        if (keys.isEmpty()) return emptyMap()
        return readerAssetKeyHashChunks(keys)
            .flatMap { hashes -> dao.find(hashes) }
            .associate { entity ->
                val metadata = entity.toModel()
                metadata.logicalAssetKeyHash to metadata
            }
    }

    override suspend fun all(): List<ReaderAssetMetadata> =
        dao.all().map(ReaderAssetEntryEntity::toModel)

    override suspend fun usageBytes(): Long = dao.usageBytes()

    override suspend fun detach(key: ReaderAssetKeyHash): ReaderAssetMetadata? =
        database.withTransaction {
            dao.findOne(key.value)?.also { dao.deleteOne(key.value) }?.toModel()
        }

    override suspend fun detachAll(): List<ReaderAssetMetadata> = database.withTransaction {
        dao.all().also { dao.deleteAll() }.map(ReaderAssetEntryEntity::toModel)
    }

    override suspend fun detachSource(
        sourceNamespace: ReaderAssetSourceNamespace,
    ): List<ReaderAssetMetadata> = database.withTransaction {
        dao.findBySource(sourceNamespace.value)
            .also { dao.deleteBySource(sourceNamespace.value) }
            .map(ReaderAssetEntryEntity::toModel)
    }

    override suspend fun detachAccount(
        sourceNamespace: ReaderAssetSourceNamespace,
        securityScopeHash: String,
    ): List<ReaderAssetMetadata> = database.withTransaction {
        dao.findByAccount(sourceNamespace.value, securityScopeHash)
            .also { dao.deleteByAccount(sourceNamespace.value, securityScopeHash) }
            .map(ReaderAssetEntryEntity::toModel)
    }

    override suspend fun detachAllAccountsForSource(
        sourceNamespace: ReaderAssetSourceNamespace,
    ): List<ReaderAssetMetadata> = database.withTransaction {
        dao.findAllAccountsForSource(sourceNamespace.value)
            .also { dao.deleteAllAccountsForSource(sourceNamespace.value) }
            .map(ReaderAssetEntryEntity::toModel)
    }

    override suspend fun updateLastAccessed(key: ReaderAssetKeyHash, epochMillis: Long) {
        require(epochMillis >= 0L)
        dao.updateLastAccessed(key.value, epochMillis)
    }

    override suspend fun updateLastConsumed(key: ReaderAssetKeyHash, epochMillis: Long) {
        require(epochMillis >= 0L)
        dao.updateLastConsumed(key.value, epochMillis)
    }
}

private fun ReaderAssetMetadata.toEntity() = ReaderAssetEntryEntity(
    logicalAssetKeyHash = logicalAssetKeyHash.value,
    keySchemaVersion = keySchemaVersion.value,
    storyId = storyId.value,
    canonicalChapterId = canonicalChapterId.value,
    chapterReleaseId = chapterReleaseId.value,
    sourceNamespace = sourceNamespace.value,
    securityScopeHash = securityScopeHash,
    contentVariant = contentVariant.name,
    identityMode = identityMode.name,
    persistenceMode = persistenceMode.name,
    imageSetNamespaceHash = imageSetNamespaceHash.value,
    pageIdentityHash = pageIdentityHash.value,
    pageOrdinal = pageOrdinal,
    blobId = blobId,
    byteSize = byteSize,
    localBlobChecksum = localBlobChecksum.value,
    sourceIntegrityHash = sourceIntegrityHash,
    createdAtEpochMillis = createdAtEpochMillis,
    lastAccessedAtEpochMillis = lastAccessedAtEpochMillis,
    lastConsumedAtEpochMillis = lastConsumedAtEpochMillis,
)

internal fun readerAssetKeyHashChunks(keys: Set<ReaderAssetKeyHash>): List<List<String>> =
    keys.map(ReaderAssetKeyHash::value).chunked(READER_ASSET_IN_QUERY_CHUNK_SIZE)

private const val READER_ASSET_IN_QUERY_CHUNK_SIZE = 900

private fun ReaderAssetEntryEntity.toModel() = ReaderAssetMetadata(
    logicalAssetKeyHash = ReaderAssetKeyHash(logicalAssetKeyHash),
    keySchemaVersion = ReaderAssetKeySchemaVersion(keySchemaVersion),
    storyId = StoryId(storyId),
    canonicalChapterId = CanonicalChapterId(canonicalChapterId),
    chapterReleaseId = ChapterReleaseId(chapterReleaseId),
    sourceNamespace = ReaderAssetSourceNamespace.fromPluginId(PluginId(sourceNamespace)),
    securityScopeHash = securityScopeHash,
    contentVariant = ReaderContentVariant.valueOf(contentVariant),
    identityMode = ReaderAssetIdentityMode.valueOf(identityMode),
    persistenceMode = ReaderAssetPersistenceMode.valueOf(persistenceMode),
    imageSetNamespaceHash = ReaderImageSetNamespace(imageSetNamespaceHash),
    pageIdentityHash = ReaderAssetIdentityHash(pageIdentityHash),
    pageOrdinal = pageOrdinal,
    blobId = blobId,
    byteSize = byteSize,
    localBlobChecksum = BlobChecksum(localBlobChecksum),
    sourceIntegrityHash = sourceIntegrityHash,
    createdAtEpochMillis = createdAtEpochMillis,
    lastAccessedAtEpochMillis = lastAccessedAtEpochMillis,
    lastConsumedAtEpochMillis = lastConsumedAtEpochMillis,
)
