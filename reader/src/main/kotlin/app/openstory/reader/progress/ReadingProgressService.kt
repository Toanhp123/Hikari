package app.openstory.reader.progress

import app.openstory.common.Clock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProgressUpdate(
    val storyId: StoryId,
    val canonicalChapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId,
    val contentFingerprint: String,
    val position: ReadingPosition,
    val completed: Boolean,
)

class ReadingProgressService(
    private val repository: ReadingProgressRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private var pending: ProgressUpdate? = null
    private var pendingWrite: Job? = null
    private val writeMutex = Mutex()

    fun update(value: ProgressUpdate) {
        pending = value
        pendingWrite?.cancel()
        pendingWrite = scope.launch {
            delay(debounceMillis)
            pendingWrite = null
            writePending()
        }
    }

    suspend fun flush() {
        pendingWrite?.cancel()
        pendingWrite = null
        writePending()
    }

    private suspend fun writePending() = writeMutex.withLock {
        val update = pending ?: return@withLock
        val existing = repository.find(update.storyId, update.canonicalChapterId)
        repository.save(update.merge(existing, clock.nowEpochMillis()))
        if (pending == update) pending = null
    }

    private fun ProgressUpdate.merge(existing: ReadingProgress?, now: Long): ReadingProgress {
        val completion = when {
            existing?.completedAtEpochMillis != null -> existing.completedAtEpochMillis
            completed -> now
            else -> null
        }
        return ReadingProgress(
            storyId = storyId,
            canonicalChapterId = canonicalChapterId,
            releaseId = releaseId,
            contentFingerprint = contentFingerprint,
            position = position,
            completedAtEpochMillis = completion,
            updatedAtEpochMillis = now,
        )
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 500L
    }
}
