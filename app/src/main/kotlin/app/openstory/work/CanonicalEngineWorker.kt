package app.openstory.work

import android.content.Context
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import app.openstory.catalog.orchestration.CanonicalEngineMaintenanceService
import app.openstory.catalog.orchestration.CanonicalMaintenanceReport
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

private const val CANONICAL_ENGINE_BATCH_LIMIT = 32
internal const val MIN_CANONICAL_RETRY_WAKE_DELAY_MILLIS = 10_000L

class CanonicalEngineWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CanonicalEngineWorkerEntryPoint::class.java,
        )
        entryPoint.scheduler().onDrainStarted()
        return try {
            val startedAtElapsedMillis = SystemClock.elapsedRealtime()
            var report = entryPoint.maintenanceService().drainReady(CANONICAL_ENGINE_BATCH_LIMIT)
            while (
                report.nextAttemptAtEpochMillis?.let { it <= System.currentTimeMillis() } == true &&
                SystemClock.elapsedRealtime() - startedAtElapsedMillis < CANONICAL_ENGINE_RUN_BUDGET_MILLIS
            ) {
                report = entryPoint.maintenanceService().drainReady(CANONICAL_ENGINE_BATCH_LIMIT)
            }
            scheduleCanonicalFollowUp(entryPoint.scheduler(), report)
            decideCanonicalWorkerResult().toWorkerResult()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class CanonicalEngineSafetyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            CanonicalEngineWorkerEntryPoint::class.java,
        )
        return try {
            val report = entryPoint.maintenanceService().runConsistencySafetyPass(CANONICAL_ENGINE_BATCH_LIMIT)
            scheduleCanonicalFollowUp(entryPoint.scheduler(), report)
            decideCanonicalSafetyWorkerResult().toWorkerResult()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class CanonicalEngineRetryWakeWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val scheduler = EntryPointAccessors.fromApplication(
            applicationContext,
            CanonicalEngineWorkerEntryPoint::class.java,
        ).scheduler()
        scheduler.scheduleDrain()
        return Result.success()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface CanonicalEngineWorkerEntryPoint {
    fun maintenanceService(): CanonicalEngineMaintenanceService
    fun scheduler(): WorkManagerCanonicalEngineWorkScheduler
}

internal enum class CanonicalWorkRunDecision {
    SUCCESS,
    RETRY,
    FAILURE,
}

internal enum class CanonicalWorkerWake {
    NONE,
    DRAIN,
    RETRY_WAKE,
}


internal fun decideCanonicalWorkerResult(): CanonicalWorkRunDecision = CanonicalWorkRunDecision.SUCCESS

internal fun decideCanonicalSafetyWorkerResult(): CanonicalWorkRunDecision = CanonicalWorkRunDecision.SUCCESS

internal suspend fun runCanonicalEngineWork(
    work: suspend () -> CanonicalMaintenanceReport,
): CanonicalWorkRunDecision = try {
    work()
    decideCanonicalWorkerResult()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    CanonicalWorkRunDecision.RETRY
}

internal fun decideCanonicalWorkerWake(
    nowEpochMillis: Long,
    nextAttemptAtEpochMillis: Long?,
): CanonicalWorkerWake {
    require(nowEpochMillis >= 0L)
    require(nextAttemptAtEpochMillis == null || nextAttemptAtEpochMillis >= 0L)
    return when {
        nextAttemptAtEpochMillis == null -> CanonicalWorkerWake.NONE
        nextAttemptAtEpochMillis <= nowEpochMillis -> CanonicalWorkerWake.DRAIN
        else -> CanonicalWorkerWake.RETRY_WAKE
    }
}

private fun scheduleCanonicalFollowUp(
    scheduler: WorkManagerCanonicalEngineWorkScheduler,
    report: CanonicalMaintenanceReport,
) {
    val now = System.currentTimeMillis().coerceAtLeast(0L)
    when (decideCanonicalWorkerWake(now, report.nextAttemptAtEpochMillis)) {
        CanonicalWorkerWake.NONE -> Unit
        CanonicalWorkerWake.DRAIN -> scheduler.scheduleDrain()
        CanonicalWorkerWake.RETRY_WAKE -> scheduler.scheduleRetryWakeup(requireNotNull(report.nextAttemptAtEpochMillis))
    }
}

internal fun canonicalRetryWakeWorkName(nextAttemptAtEpochMillis: Long): String {
    require(nextAttemptAtEpochMillis >= 0L)
    return "canonical-engine-retry-wake:$nextAttemptAtEpochMillis"
}

internal fun canonicalRetryWakeDelayMillis(
    nowEpochMillis: Long,
    nextAttemptAtEpochMillis: Long,
): Long {
    require(nowEpochMillis >= 0L)
    require(nextAttemptAtEpochMillis >= 0L)
    if (nextAttemptAtEpochMillis <= nowEpochMillis) return MIN_CANONICAL_RETRY_WAKE_DELAY_MILLIS
    return maxOf(MIN_CANONICAL_RETRY_WAKE_DELAY_MILLIS, nextAttemptAtEpochMillis - nowEpochMillis)
}

private fun CanonicalWorkRunDecision.toWorkerResult(): ListenableWorker.Result = when (this) {
    CanonicalWorkRunDecision.SUCCESS -> ListenableWorker.Result.success()
    CanonicalWorkRunDecision.RETRY -> ListenableWorker.Result.retry()
    CanonicalWorkRunDecision.FAILURE -> ListenableWorker.Result.failure()
}

private const val CANONICAL_ENGINE_RUN_BUDGET_MILLIS = 5_000L
