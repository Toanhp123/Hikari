package app.openstory.storage.room.chapters

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "notification_deliveries",
    primaryKeys = ["event_id"],
    foreignKeys = [
        ForeignKey(
            entity = ChapterChangeEventEntity::class,
            parentColumns = ["event_id"],
            childColumns = ["event_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["status", "next_attempt_at_epoch_millis", "event_id"]),
        Index(value = ["claim_expires_at_epoch_millis"]),
        Index(value = ["claim_token"]),
        Index(value = ["notification_id"], unique = true),
        Index(value = ["updated_at_epoch_millis", "status"]),
    ],
)
internal data class NotificationDeliveryEntity(
    @ColumnInfo(name = "event_id") val eventId: Long,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "claim_token") val claimToken: String?,
    @ColumnInfo(name = "claim_expires_at_epoch_millis") val claimExpiresAtEpochMillis: Long?,
    @ColumnInfo(name = "attempt_count") val attemptCount: Int,
    @ColumnInfo(name = "next_attempt_at_epoch_millis") val nextAttemptAtEpochMillis: Long,
    @ColumnInfo(name = "notification_id") val notificationId: Int?,
    @ColumnInfo(name = "reason_code") val reasonCode: String?,
    @ColumnInfo(name = "last_error_code") val lastErrorCode: String?,
    @ColumnInfo(name = "updated_at_epoch_millis") val updatedAtEpochMillis: Long,
)
