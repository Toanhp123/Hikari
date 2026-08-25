package app.openstory.storage.room.chapters

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

internal data class ClaimedNotificationEventRow(
    val eventId: Long,
    val eventKey: String,
    val storyId: String,
    val chapterId: String,
    val releaseId: String?,
    val changeKind: String,
    val chapterCommitFingerprint: String,
    val occurredAtEpochMillis: Long,
    val attemptCount: Int,
)

@Dao
internal interface NotificationEventDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvents(events: List<ChapterChangeEventEntity>): List<Long>

    @Query("SELECT event_id FROM chapter_change_events WHERE event_key IN (:eventKeys)")
    suspend fun eventIds(eventKeys: Collection<String>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDeliveries(deliveries: List<NotificationDeliveryEntity>)

    @Query(
        "SELECT event_id FROM notification_deliveries WHERE status = 'PENDING' " +
            "AND next_attempt_at_epoch_millis <= :nowEpochMillis " +
            "ORDER BY next_attempt_at_epoch_millis, event_id LIMIT :limit",
    )
    suspend fun claimableIds(limit: Int, nowEpochMillis: Long): List<Long>

    @Query(
        "UPDATE notification_deliveries SET status = 'CLAIMED', claim_token = :token, " +
            "claim_expires_at_epoch_millis = :expiresAtEpochMillis, attempt_count = attempt_count + 1, " +
            "updated_at_epoch_millis = :nowEpochMillis WHERE event_id IN (:eventIds) AND status = 'PENDING'",
    )
    suspend fun claim(
        eventIds: Collection<Long>,
        token: String,
        expiresAtEpochMillis: Long,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "SELECT event.event_id AS eventId, event.event_key AS eventKey, event.story_id AS storyId, " +
            "event.chapter_id AS chapterId, event.release_id AS releaseId, event.change_kind AS changeKind, " +
            "event.chapter_commit_fingerprint AS chapterCommitFingerprint, " +
            "event.occurred_at_epoch_millis AS occurredAtEpochMillis, delivery.attempt_count AS attemptCount " +
            "FROM chapter_change_events AS event INNER JOIN notification_deliveries AS delivery " +
            "ON delivery.event_id = event.event_id WHERE delivery.claim_token = :token " +
            "AND delivery.status = 'CLAIMED' ORDER BY event.occurred_at_epoch_millis, event.event_id",
    )
    suspend fun claimed(token: String): List<ClaimedNotificationEventRow>

    @Query(
        "UPDATE notification_deliveries SET status = 'PENDING', claim_token = NULL, " +
            "claim_expires_at_epoch_millis = NULL, updated_at_epoch_millis = :nowEpochMillis " +
            "WHERE status = 'CLAIMED' AND claim_expires_at_epoch_millis <= :nowEpochMillis",
    )
    suspend fun reclaimStale(nowEpochMillis: Long): Int

    @Query(
        "SELECT MIN(CASE WHEN status = 'CLAIMED' THEN claim_expires_at_epoch_millis " +
            "ELSE next_attempt_at_epoch_millis END) FROM notification_deliveries " +
            "WHERE status IN ('PENDING', 'CLAIMED')",
    )
    suspend fun nextWakeAtEpochMillis(): Long?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM chapter_change_events WHERE story_id = :storyId " +
            "AND chapter_id = :chapterId AND change_kind IN " +
            "('CANONICAL_CHAPTER_CREATED', 'CANONICAL_CHAPTER_RESTORED'))",
    )
    suspend fun hasNewChapterEvidence(storyId: String, chapterId: String): Boolean

    @Query(
        "SELECT notification_id FROM notification_deliveries WHERE event_id = :eventId " +
            "AND claim_token = :claimToken AND status = 'CLAIMED'",
    )
    suspend fun assignedNotificationId(claimToken: String, eventId: Long): Int?

    @Query("SELECT EXISTS(SELECT 1 FROM notification_deliveries WHERE notification_id = :notificationId)")
    suspend fun notificationIdExists(notificationId: Int): Boolean

    @Query(
        "UPDATE notification_deliveries SET notification_id = :notificationId, " +
            "updated_at_epoch_millis = :nowEpochMillis WHERE event_id = :eventId " +
            "AND claim_token = :claimToken AND status = 'CLAIMED' AND notification_id IS NULL",
    )
    suspend fun assignNotificationId(
        claimToken: String,
        eventId: Long,
        notificationId: Int,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE notification_deliveries SET status = :status, claim_token = NULL, " +
            "claim_expires_at_epoch_millis = NULL, reason_code = :reasonCode, last_error_code = NULL, " +
            "updated_at_epoch_millis = :nowEpochMillis WHERE event_id = :eventId " +
            "AND claim_token = :claimToken AND status = 'CLAIMED'",
    )
    suspend fun markTerminal(
        claimToken: String,
        eventId: Long,
        status: String,
        reasonCode: String?,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "UPDATE notification_deliveries SET status = 'PENDING', claim_token = NULL, " +
            "claim_expires_at_epoch_millis = NULL, next_attempt_at_epoch_millis = :retryAtEpochMillis, " +
            "last_error_code = :errorCode, updated_at_epoch_millis = :nowEpochMillis " +
            "WHERE event_id = :eventId AND claim_token = :claimToken AND status = 'CLAIMED'",
    )
    suspend fun release(
        claimToken: String,
        eventId: Long,
        retryAtEpochMillis: Long,
        errorCode: String,
        nowEpochMillis: Long,
    ): Int

    @Query(
        "SELECT COUNT(*) FROM notification_deliveries WHERE status = 'IN_APP_ONLY' " +
            "AND updated_at_epoch_millis >= :sinceEpochMillis",
    )
    fun observeRecentInAppOnlyCount(sinceEpochMillis: Long): Flow<Int>
}
