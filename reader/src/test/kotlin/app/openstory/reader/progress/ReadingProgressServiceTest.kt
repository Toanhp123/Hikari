package app.openstory.reader.progress

import app.openstory.common.FakeClock
import app.openstory.common.id.CanonicalChapterId
import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.StoryId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingProgressServiceTest {
    @Test
    fun debouncesWritesAndKeepsCompletionMonotonicAcrossReleaseSwitches() = runTest {
        val repository = FakeProgressRepository()
        val clock = FakeClock(1_000)
        val service = ReadingProgressService(repository, clock, this, debounceMillis = 100)

        service.update(update("release-a", 0.3f, completed = true))
        service.update(update("release-a", 0.8f, completed = true))
        advanceTimeBy(101)
        assertEquals(1, repository.saveCount)
        assertEquals(1_000, repository.value.value?.completedAtEpochMillis)

        clock.advanceBy(100)
        service.update(update("release-b", 0.2f, completed = false))
        service.flush()
        val restored = repository.value.value
        assertEquals("release-b", restored?.releaseId?.value)
        assertEquals(0.2f, restored?.position?.fraction)
        assertEquals(1_000, restored?.completedAtEpochMillis)
    }

    private fun update(releaseId: String, fraction: Float, completed: Boolean) = ProgressUpdate(
        StoryId("story"),
        CanonicalChapterId("chapter"),
        ChapterReleaseId(releaseId),
        "fingerprint",
        ReadingPosition("block", 0, fraction),
        completed,
    )
}

private class FakeProgressRepository : ReadingProgressRepository {
    val value = MutableStateFlow<ReadingProgress?>(null)
    private val all = MutableStateFlow<List<ReadingProgress>>(emptyList())
    var saveCount = 0

    override fun observeAll(): Flow<List<ReadingProgress>> = all
    override fun observe(storyId: StoryId, chapterId: CanonicalChapterId): Flow<ReadingProgress?> = value
    override suspend fun find(storyId: StoryId, chapterId: CanonicalChapterId) = value.value
    override suspend fun save(progress: ReadingProgress) {
        saveCount += 1
        value.value = progress
        all.value = listOf(progress)
    }
}
