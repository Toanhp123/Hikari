package app.universalmedia.ingestion.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.universalmedia.core.domain.ScanCancellation
import app.universalmedia.core.model.RootId
import java.util.UUID

class LocalScanWorker internal constructor(
    context: Context,
    parameters: WorkerParameters,
    private val runner: LocalRootScanRunner,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val value = inputData.getString(ROOT_ID) ?: return Result.failure()
        val rootId = try {
            RootId(UUID.fromString(value))
        } catch (_: IllegalArgumentException) {
            return Result.failure()
        }
        return when (runner.run(rootId, ScanCancellation { isStopped })) {
            LocalScanResult.COMPLETE, LocalScanResult.INCOMPLETE -> Result.success()
            LocalScanResult.RETRY -> Result.retry()
            LocalScanResult.FAILURE, LocalScanResult.ROOT_NOT_FOUND -> Result.failure()
        }
    }

    internal companion object {
        const val ROOT_ID = "root_id"
    }
}

class LocalScanWorkerFactory(private val runner: LocalRootScanRunner) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = if (workerClassName == LocalScanWorker::class.java.name) {
        LocalScanWorker(appContext, workerParameters, runner)
    } else {
        null
    }
}
