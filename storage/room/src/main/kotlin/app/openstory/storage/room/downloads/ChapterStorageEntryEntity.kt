package app.openstory.storage.room.downloads

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "chapter_storage_entries",
    primaryKeys = ["namespace", "chapter_release_id", "content_fingerprint"],
    indices = [
        Index("chapter_release_id"),
        Index("last_accessed_at_epoch_millis"),
        Index("download_state"),
    ],
)
internal data class ChapterStorageEntryEntity(
    val namespace: String,
    @ColumnInfo(name = "chapter_release_id") val chapterReleaseId: String,
    @ColumnInfo(name = "content_fingerprint") val contentFingerprint: String,
    val checksum: String?,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "last_accessed_at_epoch_millis") val lastAccessedAtEpochMillis: Long,
    val pinned: Boolean,
    val current: Boolean,
    @ColumnInfo(name = "download_state") val downloadState: String?,
    @ColumnInfo(name = "failure_reason") val failureReason: String?,
    val attempt: Int,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)
