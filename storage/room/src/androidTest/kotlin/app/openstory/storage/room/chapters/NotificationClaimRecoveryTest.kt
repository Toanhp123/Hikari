package app.openstory.storage.room.chapters

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.chapters.aggregation.AggregationPlan
import app.openstory.chapters.model.CanonicalChapter
import app.openstory.chapters.model.ChapterKind
import app.openstory.chapters.model.ParsedChapterLabel
import app.openstory.chapters.repository.ChapterCommitResult
import app.openstory.chapters.repository.ChapterMutation
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationClaimRecoveryTest {
    @Test
    fun staleClaimReusesPersistedPlatformIdAndTerminatesOnce() = runTest {
        withDatabase { database ->
            val storyId = StoryId("story:recovery")
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO stories (story_id, content_type) VALUES (?, 'MANGA')",
                arrayOf(storyId.value),
            )
            val chapter = CanonicalChapter(
                id = CanonicalChapterId("chapter:recovery"),
                storyId = storyId,
                parsedLabel = ParsedChapterLabel(
                    kind = ChapterKind.NUMBERED,
                    volume = null,
                    chapter = BigDecimal.ONE,
                    part = null,
                    normalizedTitle = null,
                ),
                displayLabel = "Chapter 1",
                tombstoned = false,
            )
            assertIs<ChapterCommitResult.Success>(
                RoomChapterRepository(database).commit(
                    ChapterMutation(
                        storyId = storyId,
                        releases = emptyList(),
                        plan = AggregationPlan(
                            creates = listOf(chapter),
                            links = emptyList(),
                            unlinks = emptySet(),
                            tombstones = emptySet(),
                            reviewCandidates = emptyList(),
                        ),
                    ),
                ),
            )
            var now = System.currentTimeMillis() + 1_000L
            var token = 0
            val repository = RoomNotificationEventRepository(
                database,
                nowEpochMillis = { now },
                tokenFactory = { "claim:${++token}" },
            )
            val abandoned = assertNotNull(repository.claim(1, now, leaseMillis = 10))
            val assigned = repository.allocateNotificationId(abandoned.token, abandoned.events.single().eventId)

            now += 11L
            val recovered = assertNotNull(repository.claim(1, now, leaseMillis = 10))
            assertEquals(assigned, repository.allocateNotificationId(recovered.token, recovered.events.single().eventId))
            repository.markDelivered(recovered.token, recovered.events.single().eventId, assigned)

            assertNull(repository.claim(1, now, leaseMillis = 10))
        }
    }

    @Test
    fun adversarialEventIdsReceiveDistinctPersistedNotificationIds() = runTest {
        withDatabase { database ->
            seed(database, 1L)
            seed(database, 1L + Int.MAX_VALUE)
            val repository = RoomNotificationEventRepository(
                database,
                nowEpochMillis = { 100L },
                tokenFactory = { "collision-claim" },
            )
            val claim = assertNotNull(repository.claim(2, 100L, leaseMillis = 10))
            val first = repository.allocateNotificationId(claim.token, claim.events[0].eventId)
            val second = repository.allocateNotificationId(claim.token, claim.events[1].eventId)

            assertNotEquals(first, second)
            assertEquals(first, repository.allocateNotificationId(claim.token, claim.events[0].eventId))
            assertEquals(second, repository.allocateNotificationId(claim.token, claim.events[1].eventId))
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

    private fun seed(database: OpenStoryDatabase, eventId: Long) {
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
                eventId,
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
