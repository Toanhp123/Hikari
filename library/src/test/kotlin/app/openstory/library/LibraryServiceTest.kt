package app.openstory.library

import app.openstory.common.FakeClock
import app.openstory.common.id.StoryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class LibraryServiceTest {
    @Test
    fun addIsImmediateIdempotentAndPreservesExistingStatus() = runTest {
        val repository = FakeLibraryRepository()
        val clock = FakeClock(1_000L)
        val service = LibraryService(repository, clock)
        val storyId = StoryId("story:library-service")

        val first = service.add(storyId)
        clock.advanceBy(100L)
        val reading = service.changeStatus(storyId, LibraryStatus.READING)
        clock.advanceBy(100L)
        val repeated = service.add(storyId, LibraryStatus.COMPLETED)

        assertEquals(LibraryStatus.WANT_TO_READ, first.status)
        assertEquals(1_000L, first.addedAt)
        assertEquals(LibraryStatus.READING, reading?.status)
        assertEquals(1_100L, reading?.updatedAt)
        assertEquals(reading, repeated)
        assertEquals(listOf(reading), service.observe().first())
    }

    @Test
    fun changeStatusAndRemoveDoNothingForMissingMembership() = runTest {
        val repository = FakeLibraryRepository()
        val service = LibraryService(repository, FakeClock(2_000L))
        val storyId = StoryId("story:not-added")

        assertNull(service.changeStatus(storyId, LibraryStatus.READING))
        service.remove(storyId)

        assertEquals(emptyList(), service.observe().first())
    }
}

private class FakeLibraryRepository : LibraryRepository {
    private val entries = linkedMapOf<StoryId, LibraryEntry>()
    private val state = MutableStateFlow<List<LibraryEntry>>(emptyList())

    override fun observe(): Flow<List<LibraryEntry>> = state

    override suspend fun add(
        storyId: StoryId,
        status: LibraryStatus,
        addedAt: Long,
    ): LibraryEntry = entries[storyId] ?: LibraryEntry(
        storyId = storyId,
        status = status,
        addedAt = addedAt,
        updatedAt = addedAt,
    ).also { entry ->
        entries[storyId] = entry
        publish()
    }

    override suspend fun remove(storyId: StoryId) {
        if (entries.remove(storyId) != null) publish()
    }

    override suspend fun changeStatus(
        storyId: StoryId,
        status: LibraryStatus,
        updatedAt: Long,
    ): LibraryEntry? {
        val existing = entries[storyId] ?: return null
        if (existing.status == status) return existing
        return existing.copy(
            status = status,
            updatedAt = updatedAt,
        ).also { entry ->
            entries[storyId] = entry
            publish()
        }
    }

    private fun publish() {
        state.value = entries.values.sortedBy { it.storyId.value }
    }
}
