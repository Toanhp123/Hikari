package app.openstory.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.openstory.chapters.notification.ChapterChangeKind
import app.openstory.chapters.notification.ChapterNotificationClassifier
import app.openstory.chapters.notification.ChapterNotificationDecision
import app.openstory.chapters.notification.ChapterNotificationPolicy
import app.openstory.chapters.notification.ChapterNotificationTargetSource
import app.openstory.chapters.notification.NotificationEventRepository
import app.openstory.chapters.notification.PendingChapterChangeEvent
import app.openstory.settings.AppSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class NotificationDeliveryWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            NotificationDeliveryEntryPoint::class.java,
        )
        val processor = NotificationDeliveryProcessor(
            repository = dependencies.notificationEventRepository(),
            targets = dependencies.chapterNotificationTargetSource(),
            validator = dependencies.notificationDeepLinkFactory(),
            notifier = dependencies.androidChapterNotifier(),
            policy = {
                val settings = dependencies.appSettingsRepository().settings.first().normalized()
                ChapterNotificationPolicy(
                    notifyNewCanonicalChapters = settings.notifyNewCanonicalChapters,
                    notifyPreferredLanguageReleases = settings.notifyPreferredLanguageReleases,
                    contentLanguageOrder = settings.contentLanguageOrder,
                )
            },
        )
        return when (processor.drain()) {
            NotificationDrainOutcome.SUCCESS -> Result.success()
            NotificationDrainOutcome.RETRY -> Result.retry()
        }
    }
}

internal enum class NotificationDrainOutcome { SUCCESS, RETRY }

internal class NotificationDeliveryProcessor(
    private val repository: NotificationEventRepository,
    private val targets: ChapterNotificationTargetSource,
    private val validator: ChapterNotificationTargetValidator,
    private val notifier: AndroidChapterNotifier,
    private val policy: suspend () -> ChapterNotificationPolicy,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    @Suppress("ReturnCount")
    suspend fun drain(): NotificationDrainOutcome {
        val claim = try {
            repository.claim(CLAIM_LIMIT, nowEpochMillis(), CLAIM_LEASE_MILLIS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return NotificationDrainOutcome.RETRY
        } ?: return if (repository.nextWakeAtEpochMillis() != null) {
            NotificationDrainOutcome.RETRY
        } else {
            NotificationDrainOutcome.SUCCESS
        }

        val currentPolicy = policy()
        val coveredChapters = claim.events.asSequence()
            .filter { it.fact.kind != ChapterChangeKind.RELEASE_LINKED }
            .map { it.fact.chapterId }
            .toSet()

        var retryRequested = false
        for (event in claim.events) {
            try {
                val covered = coveredChapters.contains(event.fact.chapterId) ||
                    (event.fact.kind == ChapterChangeKind.RELEASE_LINKED &&
                        repository.hasNewChapterEvidence(event.fact.storyId, event.fact.chapterId))
                retryRequested = deliver(event, claim.token, currentPolicy, covered) || retryRequested
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) {
                    runCatching {
                        repository.release(
                            claim.token,
                            event.eventId,
                            nowEpochMillis(),
                            "notification.delivery_cancelled",
                        )
                    }
                }
                throw cancelled
            } catch (_: Exception) {
                runCatching {
                    repository.release(
                        claim.token,
                        event.eventId,
                        nowEpochMillis(),
                        "notification.delivery_failed",
                    )
                }
                retryRequested = true
            }
        }
        return if (claim.events.size == CLAIM_LIMIT || retryRequested) {
            NotificationDrainOutcome.RETRY
        } else {
            NotificationDrainOutcome.SUCCESS
        }
    }

    private suspend fun deliver(
        event: PendingChapterChangeEvent,
        claimToken: String,
        policy: ChapterNotificationPolicy,
        coveredByNewChapterEvent: Boolean,
    ): Boolean {
        val context = targets.context(event.fact)
        return when (
            val decision = ChapterNotificationClassifier().classify(
                event.fact,
                context,
                policy,
                coveredByNewChapterEvent,
            )
        ) {
            is ChapterNotificationDecision.Consume ->
                repository.consume(claimToken, event.eventId, decision.reasonCode).let { false }

            is ChapterNotificationDecision.Publish -> {
                val notificationId = repository.allocateNotificationId(claimToken, event.eventId)
                val target = validator.validate(decision.candidate, notificationId)
                if (target == null) {
                    repository.consume(claimToken, event.eventId, "notification.target_stale")
                    return false
                }
                when (val result = notifier.publish(notificationId, target)) {
                    PlatformNotificationResult.Published ->
                        repository.markDelivered(claimToken, event.eventId, notificationId).let { false }

                    is PlatformNotificationResult.InAppOnly ->
                        repository.markInAppOnly(claimToken, event.eventId, result.reasonCode).let { false }

                    is PlatformNotificationResult.RetryableFailure -> {
                        repository.release(
                            claimToken,
                            event.eventId,
                            nowEpochMillis(),
                            result.errorCode,
                        )
                        true
                    }
                }
            }
        }
    }

    private companion object {
        const val CLAIM_LIMIT = 20
        const val CLAIM_LEASE_MILLIS = 5 * 60 * 1000L
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NotificationDeliveryEntryPoint {
    fun notificationEventRepository(): NotificationEventRepository
    fun chapterNotificationTargetSource(): ChapterNotificationTargetSource
    fun notificationDeepLinkFactory(): NotificationDeepLinkFactory
    fun androidChapterNotifier(): AndroidChapterNotifier
    fun appSettingsRepository(): AppSettingsRepository
}
