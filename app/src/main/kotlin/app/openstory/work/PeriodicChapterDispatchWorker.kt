package app.openstory.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.openstory.chapters.sync.ChapterSyncBatch
import app.openstory.chapters.sync.ChapterSyncBatchCursor
import app.openstory.chapters.sync.ChapterSyncBatchPlanner
import app.openstory.chapters.sync.ChapterSyncCandidateSource
import app.openstory.common.id.StoryId
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CancellationException

class PeriodicChapterDispatchWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = dispatch(cursor = null)
}

class PeriodicChapterContinuationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val cursor = inputData.getString(WorkInput.CHAPTER_SYNC_CURSOR)?.let { encoded ->
            try {
                ChapterSyncCursorCodec().decode(encoded)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return if (cursor == null) Result.failure() else dispatch(cursor)
    }
}

private suspend fun CoroutineWorker.dispatch(cursor: ChapterSyncBatchCursor?): ListenableWorker.Result {
    val candidates = eligibleCandidatesOrNull()
    val batch = candidates?.let { ChapterSyncBatchPlanner().plan(it, cursor) }
    val workManager = batch?.let { workManagerOrNull() }
    return if (batch == null || workManager == null) {
        ListenableWorker.Result.retry()
    } else {
        when (
            enqueuePeriodicChapterBatch(
                batch = batch,
                enqueueStory = { storyId -> enqueueStoryWork(workManager, storyId) },
                enqueueContinuation = { continuation -> enqueueContinuationWork(workManager, continuation) },
            )
        ) {
            PeriodicDispatchEnqueueDecision.SUCCESS -> ListenableWorker.Result.success()
            PeriodicDispatchEnqueueDecision.RETRY -> ListenableWorker.Result.retry()
        }
    }
}

private suspend fun CoroutineWorker.eligibleCandidatesOrNull() = try {
    val source = EntryPointAccessors.fromApplication(
        applicationContext,
        PeriodicChapterDispatchEntryPoint::class.java,
    ).chapterSyncCandidateSource()
    source.eligibleCandidates()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    null
}

private fun CoroutineWorker.workManagerOrNull() =
    try {
        WorkManager.getInstance(applicationContext)
    } catch (_: RuntimeException) {
        null
    }

private fun enqueueContinuationWork(
    workManager: WorkManager,
    continuation: ChapterSyncBatchCursor,
) {
        val continuationRequest = OneTimeWorkRequestBuilder<PeriodicChapterContinuationWorker>()
            .setConstraints(networkConstraints())
            .setInputData(
                workDataOf(
                    WorkInput.CHAPTER_SYNC_CURSOR to ChapterSyncCursorCodec().encode(continuation),
                ),
            )
            .build()
        workManager.enqueueUniqueWork(
            WorkNames.LIBRARY_CHAPTER_CONTINUATION,
            ExistingWorkPolicy.REPLACE,
            continuationRequest,
        )
}

internal enum class PeriodicDispatchEnqueueDecision {
    SUCCESS,
    RETRY,
}

internal fun enqueuePeriodicChapterBatch(
    batch: ChapterSyncBatch,
    enqueueStory: (StoryId) -> Unit,
    enqueueContinuation: (ChapterSyncBatchCursor) -> Unit,
): PeriodicDispatchEnqueueDecision {
    batch.selected.forEach { candidate ->
        try {
            enqueueStory(candidate.storyId)
        } catch (_: RuntimeException) {
            // Keep walking so an early platform failure cannot starve later stories.
        }
    }
    val continuation = batch.continuation ?: return PeriodicDispatchEnqueueDecision.SUCCESS
    return try {
        enqueueContinuation(continuation)
        PeriodicDispatchEnqueueDecision.SUCCESS
    } catch (_: RuntimeException) {
        PeriodicDispatchEnqueueDecision.RETRY
    }
}

private fun enqueueStoryWork(
    workManager: WorkManager,
    storyId: StoryId,
) {
    val request = OneTimeWorkRequestBuilder<InitialChapterSyncWorker>()
        .setConstraints(networkConstraints())
        .setInputData(workDataOf(WorkInput.STORY_ID to storyId.value))
        .build()
    workManager.enqueueUniqueWork(
        WorkNames.storyChapterSync(storyId),
        ExistingWorkPolicy.KEEP,
        request,
    )
}

private fun networkConstraints(): Constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PeriodicChapterDispatchEntryPoint {
    fun chapterSyncCandidateSource(): ChapterSyncCandidateSource
}
