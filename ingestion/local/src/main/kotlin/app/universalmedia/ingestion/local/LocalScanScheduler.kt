package app.universalmedia.ingestion.local

import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import app.universalmedia.core.model.RootId
import java.util.concurrent.TimeUnit

class LocalScanScheduler(private val workManager: WorkManager) {
    fun enqueue(rootId: RootId): Operation = workManager.enqueueUniqueWork(
        "scan-root:${rootId.value}",
        ExistingWorkPolicy.KEEP,
        request(rootId).build(),
    )

    internal companion object {
        fun request(rootId: RootId): OneTimeWorkRequest.Builder {
            val input = Data.Builder()
                .putString(LocalScanWorker.ROOT_ID, rootId.value.toString())
                .build()
            return OneTimeWorkRequestBuilder<LocalScanWorker>()
                .setInputData(input)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        }
    }
}
