package app.openstory.storage.room.chapters

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "chapter_change_events",
    indices = [
        Index(value = ["event_key"], unique = true),
        Index(value = ["story_id", "chapter_id", "change_kind"]),
        Index(value = ["occurred_at_epoch_millis", "event_id"]),
    ],
)
internal data class ChapterChangeEventEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "event_id") val eventId: Long = 0,
    @ColumnInfo(name = "event_key") val eventKey: String,
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "chapter_id") val chapterId: String,
    @ColumnInfo(name = "release_id") val releaseId: String?,
    @ColumnInfo(name = "change_kind") val changeKind: String,
    @ColumnInfo(name = "chapter_commit_fingerprint") val chapterCommitFingerprint: String,
    @ColumnInfo(name = "occurred_at_epoch_millis") val occurredAtEpochMillis: Long,
)
