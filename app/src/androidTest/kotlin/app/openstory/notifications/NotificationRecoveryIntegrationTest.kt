package app.openstory.notifications

import android.content.Context
import android.app.NotificationManager
import android.Manifest
import android.os.Build
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.openstory.chapters.notification.ChapterChangeFact
import app.openstory.chapters.notification.ChapterNotificationContext
import app.openstory.chapters.notification.ChapterNotificationPolicy
import app.openstory.chapters.notification.ChapterNotificationTargetSnapshot
import app.openstory.chapters.notification.ChapterNotificationTargetSource
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import app.openstory.storage.room.OpenStoryDatabase
import app.openstory.storage.room.chapters.RoomNotificationEventRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationRecoveryIntegrationTest {
    @Test
    fun lostWakeAndProcessDeathRecoverToOneTerminalDelivery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "notification-recovery-integration.db"
        context.deleteDatabase(databaseName)
        var database = openDatabase(context, databaseName)
        try {
            seedEvent(database, eventId = 1L)
            var now = 1_000L
            val firstProcess = RoomNotificationEventRepository(
                database,
                nowEpochMillis = { now },
                tokenFactory = { "process-1" },
            )
            val abandoned = requireNotNull(firstProcess.claim(1, now, leaseMillis = 50))
            val assignedId = firstProcess.allocateNotificationId(abandoned.token, abandoned.events.single().eventId)

            database.close()
            now = 1_051L
            database = openDatabase(context, databaseName)
            val restarted = RoomNotificationEventRepository(
                database,
                nowEpochMillis = { now },
                tokenFactory = { "process-2" },
            )
            val publishedIds = mutableListOf<Int>()
            val processor = processor(
                context,
                restarted,
                nowEpochMillis = { now },
                notifier = AndroidChapterNotifier { notificationId, _ ->
                    publishedIds += notificationId
                    PlatformNotificationResult.Published
                },
            )

            assertEquals(NotificationDrainOutcome.SUCCESS, processor.drain())
            assertEquals(NotificationDrainOutcome.SUCCESS, processor.drain())
            assertEquals(listOf(assignedId), publishedIds)
            assertNull(restarted.claim(1, now, leaseMillis = 50))
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun permissionDenialIsTerminalAndLaterGrantDoesNotPublishHistory() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "notification-permission-integration.db"
        context.deleteDatabase(databaseName)
        val database = openDatabase(context, databaseName)
        try {
            NotificationChannelConfig.create(context)
            assumeFalse(NotificationPermissionGate(context).status().permissionGranted)
            seedEvent(database, eventId = 2L)
            val repository = RoomNotificationEventRepository(
                database,
                nowEpochMillis = { 2_000L },
                tokenFactory = { "permission-claim" },
            )
            val dispatcher = NotificationDispatcher(
                context,
                NotificationPermissionGate(context),
                StoryNotificationBuilder(context),
            )
            val denied = processor(context, repository, nowEpochMillis = { 2_000L }, notifier = dispatcher)

            assertEquals(NotificationDrainOutcome.SUCCESS, denied.drain())
            assertEquals(1, repository.observeRecentInAppOnlyCount(0).first())

            grantNotificationPermission(context)
            val granted = processor(context, repository, nowEpochMillis = { 3_000L }, notifier = dispatcher)
            assertEquals(NotificationDrainOutcome.SUCCESS, granted.drain())
            assertEquals(0, context.getSystemService(NotificationManager::class.java).activeNotifications.size)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun processor(
        context: Context,
        repository: RoomNotificationEventRepository,
        nowEpochMillis: () -> Long,
        notifier: AndroidChapterNotifier,
    ) = NotificationDeliveryProcessor(
        repository = repository,
        targets = StableNotificationTargets,
        validator = NotificationDeepLinkFactory(context, StableNotificationTargets),
        notifier = notifier,
        policy = { ChapterNotificationPolicy(true, true, listOf("en")) },
        nowEpochMillis = nowEpochMillis,
    )

    private fun openDatabase(context: Context, name: String): OpenStoryDatabase =
        Room.databaseBuilder(context, OpenStoryDatabase::class.java, name).build()

    private fun grantNotificationPermission(context: Context) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun seedEvent(database: OpenStoryDatabase, eventId: Long) {
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
                eventId * 1_000,
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

    private object StableNotificationTargets : ChapterNotificationTargetSource {
        override suspend fun context(fact: ChapterChangeFact) = ChapterNotificationContext(
            storyId = fact.storyId,
            chapterId = fact.chapterId,
            chapterTombstoned = false,
            chapterRead = false,
            releases = emptyList(),
        )

        override suspend fun resolveStory(storyId: StoryId): StoryId = storyId

        override suspend fun target(
            storyId: StoryId,
            chapterId: CanonicalChapterId,
            releaseId: ChapterReleaseId?,
        ) = ChapterNotificationTargetSnapshot(
            storyId = storyId,
            chapterId = chapterId,
            releaseId = releaseId,
            storyTitle = "Story",
            chapterLabel = "Chapter",
            languageTag = null,
        )
    }
}
