package app.openstory.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.openstory.catalog.orchestration.CanonicalEngineWorkScheduler
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WorkManagerCanonicalEngineWorkScheduler(
    private val context: Context,
) : CanonicalEngineWorkScheduler {
    private val drainKickPending = AtomicBoolean(false)

    override fun scheduleDrain() {
        if (!drainKickPending.compareAndSet(false, true)) return
        try {
            val request = OneTimeWorkRequestBuilder<CanonicalEngineWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                DRAIN_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        } catch (_: RuntimeException) {
            drainKickPending.set(false)
            // The Room queue is durable; app start and the daily safety sweep provide later wakeups.
        }
    }

    /** Allows one new kick to be appended while the current serial drain is running. */
    fun onDrainStarted() {
        drainKickPending.set(false)
    }

    fun scheduleRetryWakeup(nextAttemptAtEpochMillis: Long) {
        require(nextAttemptAtEpochMillis >= 0L)
        try {
            val now = System.currentTimeMillis().coerceAtLeast(0L)
            val request = OneTimeWorkRequestBuilder<CanonicalEngineRetryWakeWorker>()
                .setInitialDelay(
                    canonicalRetryWakeDelayMillis(now, nextAttemptAtEpochMillis),
                    TimeUnit.MILLISECONDS,
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                canonicalRetryWakeWorkName(nextAttemptAtEpochMillis),
                ExistingWorkPolicy.KEEP,
                request,
            )
        } catch (_: RuntimeException) {
            // The durable next-attempt timestamp remains authoritative even if this wakeup is lost.
        }
    }

    fun ensureDailySafety() {
        try {
            val request = PeriodicWorkRequestBuilder<CanonicalEngineSafetyWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SAFETY_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        } catch (_: RuntimeException) {
            // Scheduling is best effort; application startup retries registration on the next process start.
        }
    }

    private companion object {
        const val DRAIN_WORK_NAME = "canonical-engine-drain"
        const val SAFETY_WORK_NAME = "canonical-engine-safety"
    }
}
