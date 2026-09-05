package app.openstory.storage.room.readerassets

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reader_asset_entries",
    indices = [
        Index("chapter_release_id"),
        Index(value = ["story_id", "canonical_chapter_id"]),
        Index(value = ["last_consumed_at_epoch_millis", "last_accessed_at_epoch_millis"]),
        Index(value = ["source_namespace", "security_scope_hash"]),
        Index("blob_id", unique = true),
    ],
)
internal data class ReaderAssetEntryEntity(
    @PrimaryKey
    @ColumnInfo(name = "logical_asset_key_hash") val logicalAssetKeyHash: String,
    @ColumnInfo(name = "key_schema_version") val keySchemaVersion: Int,
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "canonical_chapter_id") val canonicalChapterId: String,
    @ColumnInfo(name = "chapter_release_id") val chapterReleaseId: String,
    @ColumnInfo(name = "source_namespace") val sourceNamespace: String,
    @ColumnInfo(name = "security_scope_hash") val securityScopeHash: String?,
    @ColumnInfo(name = "content_variant") val contentVariant: String,
    @ColumnInfo(name = "identity_mode") val identityMode: String,
    @ColumnInfo(name = "persistence_mode") val persistenceMode: String,
    @ColumnInfo(name = "image_set_namespace_hash") val imageSetNamespaceHash: String,
    @ColumnInfo(name = "page_identity_hash") val pageIdentityHash: String,
    @ColumnInfo(name = "page_ordinal") val pageOrdinal: Int,
    @ColumnInfo(name = "blob_id") val blobId: String,
    @ColumnInfo(name = "byte_size") val byteSize: Long,
    @ColumnInfo(name = "local_blob_checksum") val localBlobChecksum: String,
    @ColumnInfo(name = "source_integrity_hash") val sourceIntegrityHash: String?,
    @ColumnInfo(name = "created_at_epoch_millis") val createdAtEpochMillis: Long,
    @ColumnInfo(name = "last_accessed_at_epoch_millis") val lastAccessedAtEpochMillis: Long,
    @ColumnInfo(name = "last_consumed_at_epoch_millis") val lastConsumedAtEpochMillis: Long?,
)
