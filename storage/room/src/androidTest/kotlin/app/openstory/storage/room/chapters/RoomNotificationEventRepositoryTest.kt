package app.openstory.storage.room.chapters

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.storage.room.OpenStoryDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomNotificationEventRepositoryTest {
    @Test
    fun claimsAreBoundedExclusiveRecoverableAndTerminallyIdempotent() = runTest {
        withDatabase { database ->
            seed(database, count = 3)
            var tokenNumber = 0
            var now = 100L
            val repository = RoomNotificationEventRepository(
                database,
                nowEpochMillis = { now },
                tokenFactory = { "claim:${++tokenNumber}" },
            )

            val first = assertNotNull(repository.claim(limit = 2, nowEpochMillis = now, leaseMillis = 50))
            val second = assertNotNull(repository.claim(limit = 2, nowEpochMillis = now, leaseMillis = 50))
            assertEquals(2, first.events.size)
            assertEquals(1, second.events.size)
            assertNull(repository.claim(limit = 2, nowEpochMillis = now, leaseMillis = 50))

            assertFailsWith<IllegalStateException> {
                repository.consume("forged-token", first.events.first().eventId, "notification.invalid")
            }
            repository.markInAppOnly(first.token, first.events.first().eventId, "notification.permission_denied")
            assertEquals(1, repository.observeRecentInAppOnlyCount(0).first())

            now = 151
            assertEquals(2, repository.reclaimStale(now))
            val reclaimed = assertNotNull(repository.claim(2, now, 50))
            assertEquals(2, reclaimed.events.size)
        }
    }

    @Test
    fun persistedNotificationIdIsReusedAfterAStaleClaim() = runTest {
        withDatabase { database ->
            seed(database, count = 1)
            var now = 10L
            var tokenNumber = 0
            val repository = RoomNotificationEventRepository(
                database,
                nowEpochMillis = { now },
                tokenFactory = { "claim:${++tokenNumber}" },
            )
            val first = assertNotNull(repository.claim(1, now, 10))
            val notificationId = repository.allocateNotificationId(first.token, first.events.single().eventId)
            now = 21
            repository.reclaimStale(now)
            val retry = assertNotNull(repository.claim(1, now, 10))
            assertEquals(notificationId, repository.allocateNotificationId(retry.token, retry.events.single().eventId))
            repository.markDelivered(retry.token, retry.events.single().eventId, notificationId)
            assertNull(repository.claim(1, now, 10))
        }
    }

    private suspend fun withDatabase(block: suspend (OpenStoryDatabase) -> Unit) {
        val database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            OpenStoryDatabase::class.java,
        ).build()
        try {
            block(database)
        } finally {
            database.close()
        }
    }

    private fun seed(database: OpenStoryDatabase, count: Int) {
        repeat(count) { index ->
            val eventId = index + 1
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO chapter_change_events " +
                    "(event_id, event_key, story_id, chapter_id, release_id, change_kind, " +
                    "chapter_commit_fingerprint, occurred_at_epoch_millis) VALUES (?, ?, ?, ?, NULL, ?, ?, ?)",
                arrayOf<Any?>(
                    eventId,
                    "event:$eventId",
                    "story:$eventId",
                    "chapter:$eventId",
                    "CANONICAL_CHAPTER_CREATED",
                    "commit:$eventId",
                    eventId.toLong(),
                ),
            )
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO notification_deliveries " +
                    "(event_id, status, claim_token, claim_expires_at_epoch_millis, attempt_count, " +
                    "next_attempt_at_epoch_millis, notification_id, reason_code, last_error_code, " +
                    "updated_at_epoch_millis) VALUES (?, 'PENDING', NULL, NULL, 0, 0, NULL, NULL, NULL, 0)",
                arrayOf(eventId),
            )
        }
    }
}
