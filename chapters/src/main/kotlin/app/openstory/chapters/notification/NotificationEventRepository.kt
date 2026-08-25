package app.openstory.chapters.notification

import kotlinx.coroutines.flow.Flow
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.StoryId

interface NotificationEventRepository {
    suspend fun claim(limit: Int, nowEpochMillis: Long, leaseMillis: Long): NotificationEventClaim?
    suspend fun allocateNotificationId(claimToken: String, eventId: Long): Int
    suspend fun markDelivered(claimToken: String, eventId: Long, notificationId: Int)
    suspend fun markInAppOnly(claimToken: String, eventId: Long, reasonCode: String)
    suspend fun consume(claimToken: String, eventId: Long, reasonCode: String)
    suspend fun release(claimToken: String, eventId: Long, retryAtEpochMillis: Long, errorCode: String)
    suspend fun reclaimStale(nowEpochMillis: Long): Int
    suspend fun nextWakeAtEpochMillis(): Long?
    suspend fun hasNewChapterEvidence(storyId: StoryId, chapterId: CanonicalChapterId): Boolean
    fun observeRecentInAppOnlyCount(sinceEpochMillis: Long): Flow<Int>
}

data class NotificationEventClaim(
    val token: String,
    val events: List<PendingChapterChangeEvent>,
    val expiresAtEpochMillis: Long,
)

data class PendingChapterChangeEvent(
    val eventId: Long,
    val fact: ChapterChangeFact,
    val attemptCount: Int,
)
