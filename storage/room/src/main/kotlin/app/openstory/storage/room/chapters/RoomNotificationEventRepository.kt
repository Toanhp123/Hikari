package app.openstory.storage.room.chapters

import androidx.room.withTransaction
import app.openstory.chapters.notification.ChapterChangeFact
import app.openstory.chapters.notification.ChapterChangeKind
import app.openstory.chapters.notification.NotificationEventClaim
import app.openstory.chapters.notification.NotificationEventRepository
import app.openstory.chapters.notification.PendingChapterChangeEvent
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import java.util.UUID
import kotlinx.coroutines.flow.Flow

class RoomNotificationEventRepository(
    private val database: OpenStoryDatabase,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val tokenFactory: () -> String = { UUID.randomUUID().toString() },
) : NotificationEventRepository {
    private val dao = database.notificationEventDao()

    override suspend fun claim(limit: Int, nowEpochMillis: Long, leaseMillis: Long): NotificationEventClaim? {
        require(limit in 1..MAX_CLAIM_SIZE)
        require(nowEpochMillis >= 0L)
        require(leaseMillis in 1L..MAX_LEASE_MILLIS)
        return database.withTransaction {
            dao.reclaimStale(nowEpochMillis)
            val ids = dao.claimableIds(limit, nowEpochMillis)
            if (ids.isEmpty()) return@withTransaction null
            val token = tokenFactory()
            val expiresAt = Math.addExact(nowEpochMillis, leaseMillis)
            check(dao.claim(ids, token, expiresAt, nowEpochMillis) == ids.size)
            NotificationEventClaim(token, dao.claimed(token).map { it.toModel() }, expiresAt)
        }
    }

    override suspend fun allocateNotificationId(claimToken: String, eventId: Long): Int =
        database.withTransaction {
            require(eventId > 0L)
            dao.assignedNotificationId(claimToken, eventId)?.let { return@withTransaction it }
            var candidate = ((eventId - 1L) % Int.MAX_VALUE).toInt() + 1
            repeat(MAX_ID_PROBES) {
                if (!dao.notificationIdExists(candidate) &&
                    dao.assignNotificationId(claimToken, eventId, candidate, nowEpochMillis()) == 1
                ) {
                    return@withTransaction candidate
                }
                candidate = if (candidate == Int.MAX_VALUE) 1 else candidate + 1
            }
            error("notification.id_space_exhausted")
        }

    override suspend fun markDelivered(claimToken: String, eventId: Long, notificationId: Int) {
        require(notificationId > 0)
        check(dao.assignedNotificationId(claimToken, eventId) == notificationId)
        check(dao.markTerminal(claimToken, eventId, STATUS_DELIVERED, null, nowEpochMillis()) == 1)
    }

    override suspend fun markInAppOnly(claimToken: String, eventId: Long, reasonCode: String) =
        markTerminal(claimToken, eventId, STATUS_IN_APP_ONLY, reasonCode)

    override suspend fun consume(claimToken: String, eventId: Long, reasonCode: String) =
        markTerminal(claimToken, eventId, STATUS_CONSUMED, reasonCode)

    override suspend fun release(
        claimToken: String,
        eventId: Long,
        retryAtEpochMillis: Long,
        errorCode: String,
    ) {
        require(retryAtEpochMillis >= 0L)
        require(errorCode.isNotBlank())
        check(dao.release(claimToken, eventId, retryAtEpochMillis, errorCode, nowEpochMillis()) == 1)
    }

    override suspend fun reclaimStale(nowEpochMillis: Long): Int = dao.reclaimStale(nowEpochMillis)

    override suspend fun nextWakeAtEpochMillis(): Long? = dao.nextWakeAtEpochMillis()

    override suspend fun hasNewChapterEvidence(storyId: StoryId, chapterId: CanonicalChapterId): Boolean =
        dao.hasNewChapterEvidence(storyId.value, chapterId.value)

    override fun observeRecentInAppOnlyCount(sinceEpochMillis: Long): Flow<Int> =
        dao.observeRecentInAppOnlyCount(sinceEpochMillis)

    private suspend fun markTerminal(claimToken: String, eventId: Long, status: String, reasonCode: String) {
        require(reasonCode.isNotBlank())
        check(dao.markTerminal(claimToken, eventId, status, reasonCode, nowEpochMillis()) == 1)
    }

    private fun ClaimedNotificationEventRow.toModel() = PendingChapterChangeEvent(
        eventId = eventId,
        fact = ChapterChangeFact(
            eventKey = eventKey,
            storyId = StoryId(storyId),
            chapterId = CanonicalChapterId(chapterId),
            releaseId = releaseId?.let(::ChapterReleaseId),
            kind = ChapterChangeKind.valueOf(changeKind),
            chapterCommitFingerprint = chapterCommitFingerprint,
            occurredAtEpochMillis = occurredAtEpochMillis,
        ),
        attemptCount = attemptCount,
    )

    private companion object {
        const val MAX_CLAIM_SIZE = 50
        const val MAX_LEASE_MILLIS = 15 * 60 * 1000L
        const val MAX_ID_PROBES = 10_000
        const val STATUS_DELIVERED = "DELIVERED"
        const val STATUS_IN_APP_ONLY = "IN_APP_ONLY"
        const val STATUS_CONSUMED = "CONSUMED"
    }
}
