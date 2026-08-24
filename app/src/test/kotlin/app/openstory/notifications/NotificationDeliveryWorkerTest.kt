package app.openstory.notifications

import android.app.PendingIntent
import android.content.Intent
import app.openstory.chapters.notification.ChapterChangeFact
import app.openstory.chapters.notification.ChapterChangeKind
import app.openstory.chapters.notification.ChapterNotificationContext
import app.openstory.chapters.notification.ChapterNotificationPolicy
import app.openstory.chapters.notification.ChapterNotificationTargetSnapshot
import app.openstory.chapters.notification.ChapterNotificationTargetSource
import app.openstory.chapters.notification.NotificationEventClaim
import app.openstory.chapters.notification.NotificationEventRepository
import app.openstory.chapters.notification.PendingChapterChangeEvent
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationDeliveryWorkerTest {
    @Test
    fun retryAfterPublishUsesTheSamePersistedNotificationId() = runTest {
        val repository = FakeNotificationEventRepository(event())
        val publishedIds = mutableListOf<Int>()
        var firstAttempt = true
        val processor = processor(repository) { notificationId, _ ->
            publishedIds += notificationId
            if (firstAttempt) {
                firstAttempt = false
                error("process died after platform publish")
            }
            PlatformNotificationResult.Published
        }

        assertEquals(NotificationDrainOutcome.RETRY, processor.drain())
        assertEquals(NotificationDrainOutcome.SUCCESS, processor.drain())
        assertEquals(listOf(73, 73), publishedIds)
        assertEquals("DELIVERED", repository.terminalStatus)
    }

    @Test
    fun disabledCategoryAndPermissionDenialBecomeTerminalWithoutRetry() = runTest {
        val disabled = FakeNotificationEventRepository(event())
        val disabledProcessor = processor(
            repository = disabled,
            policy = ChapterNotificationPolicy(false, false, listOf("en")),
        ) { _, _ -> error("disabled category must not publish") }
        assertEquals(NotificationDrainOutcome.SUCCESS, disabledProcessor.drain())
        assertEquals("CONSUMED", disabled.terminalStatus)

        val denied = FakeNotificationEventRepository(event())
        val deniedProcessor = processor(denied) { _, _ ->
            PlatformNotificationResult.InAppOnly("notification.permission_denied")
        }
        assertEquals(NotificationDrainOutcome.SUCCESS, deniedProcessor.drain())
        assertEquals("IN_APP_ONLY", denied.terminalStatus)
    }

    @Test
    fun cancellationReleasesTheClaimAndIsRethrown() = runTest {
        val repository = FakeNotificationEventRepository(event())
        val processor = processor(repository) { _, _ -> throw CancellationException("cancel") }

        assertFailsWith<CancellationException> { processor.drain() }
        assertTrue(repository.released)
        assertEquals("notification.delivery_cancelled", repository.lastErrorCode)
    }

    private fun processor(
        repository: FakeNotificationEventRepository,
        policy: ChapterNotificationPolicy = ChapterNotificationPolicy(true, true, listOf("en")),
        notifier: suspend (Int, ValidatedChapterNotificationTarget) -> PlatformNotificationResult,
    ) = NotificationDeliveryProcessor(
        repository = repository,
        targets = FakeTargetSource,
        validator = ChapterNotificationTargetValidator { candidate, notificationId ->
            val context = RuntimeEnvironment.getApplication()
            ValidatedChapterNotificationTarget(
                candidate.storyId,
                candidate.chapterId,
                candidate.releaseId,
                "Story",
                "Chapter 1",
                candidate.languageTag,
                PendingIntent.getActivity(
                    context,
                    notificationId,
                    Intent(context, app.openstory.MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        },
        notifier = AndroidChapterNotifier(notifier),
        policy = { policy },
        nowEpochMillis = { 1_000L },
    )
}

private class FakeNotificationEventRepository(
    private val event: PendingChapterChangeEvent,
) : NotificationEventRepository {
    var terminalStatus: String? = null
    var released = false
    var lastErrorCode: String? = null

    override suspend fun claim(limit: Int, nowEpochMillis: Long, leaseMillis: Long): NotificationEventClaim? =
        event.takeIf { terminalStatus == null }?.let {
            released = false
            NotificationEventClaim("claim", listOf(it), nowEpochMillis + leaseMillis)
        }

    override suspend fun allocateNotificationId(claimToken: String, eventId: Long): Int = 73

    override suspend fun markDelivered(claimToken: String, eventId: Long, notificationId: Int) {
        terminalStatus = "DELIVERED"
    }

    override suspend fun markInAppOnly(claimToken: String, eventId: Long, reasonCode: String) {
        terminalStatus = "IN_APP_ONLY"
    }

    override suspend fun consume(claimToken: String, eventId: Long, reasonCode: String) {
        terminalStatus = "CONSUMED"
    }

    override suspend fun release(
        claimToken: String,
        eventId: Long,
        retryAtEpochMillis: Long,
        errorCode: String,
    ) {
        released = true
        lastErrorCode = errorCode
    }

    override suspend fun reclaimStale(nowEpochMillis: Long): Int = 0
    override suspend fun nextWakeAtEpochMillis(): Long? = null
    override suspend fun hasNewChapterEvidence(storyId: StoryId, chapterId: CanonicalChapterId) = false
    override fun observeRecentInAppOnlyCount(sinceEpochMillis: Long): Flow<Int> = flowOf(0)
}

private object FakeTargetSource : ChapterNotificationTargetSource {
    override suspend fun context(fact: ChapterChangeFact) = ChapterNotificationContext(
        fact.storyId,
        fact.chapterId,
        chapterTombstoned = false,
        chapterRead = false,
        releases = emptyList(),
    )

    override suspend fun resolveStory(storyId: StoryId): StoryId = storyId

    override suspend fun target(
        storyId: StoryId,
        chapterId: CanonicalChapterId,
        releaseId: ChapterReleaseId?,
    ): ChapterNotificationTargetSnapshot? = null
}

private fun event() = PendingChapterChangeEvent(
    eventId = 1L,
    fact = ChapterChangeFact(
        eventKey = "event-1",
        storyId = StoryId("story:1"),
        chapterId = CanonicalChapterId("chapter:1"),
        releaseId = null,
        kind = ChapterChangeKind.CANONICAL_CHAPTER_CREATED,
        chapterCommitFingerprint = "commit-1",
        occurredAtEpochMillis = 500L,
    ),
    attemptCount = 0,
)
