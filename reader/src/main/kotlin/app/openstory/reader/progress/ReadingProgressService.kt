package app.openstory.reader.progress

import app.openstory.common.Clock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

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
    scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private val pending = AtomicReference<ProgressUpdate?>(null)
    private val updates = Channel<ProgressUpdate>(Channel.CONFLATED)
    private val writeMutex = Mutex()

    init {
        scope.launch {
            while (true) {
                updates.receive()
                awaitQuietPeriod()
                writePending()
            }
        }
    }

    fun update(value: ProgressUpdate) {
        pending.set(value)
        updates.trySend(value)
    }

    suspend fun flush() {
        writePending()
    }

    private suspend fun awaitQuietPeriod() {
        while (withTimeoutOrNull(debounceMillis) { updates.receive() } != null) {
            // Keep waiting until viewport updates have been quiet for the debounce interval.
        }
    }

    private suspend fun writePending() = writeMutex.withLock {
        val update = pending.get() ?: return@withLock
        val existing = repository.find(update.storyId, update.canonicalChapterId)
        repository.save(update.merge(existing, clock.nowEpochMillis()))
        pending.compareAndSet(update, null)
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
