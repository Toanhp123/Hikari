package app.openstory.library.runtime

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.identity.SourceStoryIdV1
import app.openstory.catalog.domain.identity.SourceStoryKey
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.common.FakeClock
import app.openstory.library.domain.LibraryEntry
import app.openstory.library.domain.LibraryFilter
import app.openstory.library.domain.LibraryMutationResult
import app.openstory.library.domain.LibraryPort
import app.openstory.library.domain.LibraryPresentationSnapshot
import app.openstory.library.domain.LibraryQuery
import app.openstory.library.domain.LibraryWindow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryMutationOwnerTest {
    @Test
    fun repeatedAddKeepsSavedAtAndRemoveThenAddUsesTheNewClockTime() = runTest {
        val port = FakeLibraryPort()
        val clock = FakeClock(10)
        val owner = LibraryMutationOwner(port, clock)
        val ref = ref("saved")
        val snapshot = LibraryPresentationSnapshot("Saved", null, null)

        assertEquals(LibraryMutationResult.CHANGED, owner.add(ref, CatalogMediaType.MANGA, snapshot))
        clock.advanceBy(10)
        assertEquals(LibraryMutationResult.NO_OP, owner.add(ref, CatalogMediaType.MANGA, snapshot))
        assertEquals(10, port.entries.getValue(ref).savedAtEpochMs)

        assertEquals(LibraryMutationResult.CHANGED, owner.remove(ref))
        clock.advanceBy(10)
        assertEquals(LibraryMutationResult.CHANGED, owner.add(ref, CatalogMediaType.MANGA, snapshot))
        assertEquals(30, port.entries.getValue(ref).savedAtEpochMs)
    }

    @Test
    fun concurrentMutationsNeverEnterThePortAtTheSameTime() = runTest {
        val port = OverlapDetectingPort()
        val owner = LibraryMutationOwner(port, FakeClock(10))
        val ref = ref("serialized")
        val first = async {
            owner.add(ref, CatalogMediaType.MANGA, LibraryPresentationSnapshot("Saved", null, null))
        }
        port.addEntered.await()

        val second = async { owner.remove(ref) }
        runCurrent()
        port.releaseAdd.complete(Unit)
        first.await()
        second.await()

        assertFalse(port.overlapObserved)
    }

    private class FakeLibraryPort : LibraryPort {
        val entries = linkedMapOf<StorySourceRef, LibraryEntry>()
        private val observations = mutableMapOf<StorySourceRef, MutableStateFlow<LibraryEntry?>>()

        override fun observeMembership(ref: StorySourceRef): Flow<LibraryEntry?> =
            observations.getOrPut(ref) { MutableStateFlow(entries[ref]) }

        override fun observeWindow(query: LibraryQuery): Flow<LibraryWindow> = flowOf(LibraryWindow(emptyList(), null))

        override suspend fun add(entry: LibraryEntry): LibraryMutationResult {
            if (entry.ref in entries) return LibraryMutationResult.NO_OP
            entries[entry.ref] = entry
            observations.getOrPut(entry.ref) { MutableStateFlow(null) }.value = entry
            return LibraryMutationResult.CHANGED
        }

        override suspend fun remove(ref: StorySourceRef): LibraryMutationResult {
            if (entries.remove(ref) == null) return LibraryMutationResult.NO_OP
            observations.getOrPut(ref) { MutableStateFlow(null) }.value = null
            return LibraryMutationResult.CHANGED
        }

        override suspend fun enrichSnapshot(
            ref: StorySourceRef,
            snapshot: LibraryPresentationSnapshot,
        ): LibraryMutationResult {
            val existing = entries[ref] ?: return LibraryMutationResult.NO_OP
            if (existing.snapshot == snapshot) return LibraryMutationResult.NO_OP
            entries[ref] = existing.copy(snapshot = snapshot)
            observations.getOrPut(ref) { MutableStateFlow(null) }.value = entries[ref]
            return LibraryMutationResult.CHANGED
        }
    }

    private class OverlapDetectingPort : LibraryPort {
        val addEntered = CompletableDeferred<Unit>()
        val releaseAdd = CompletableDeferred<Unit>()
        var overlapObserved = false
        private var mutationActive = false

        override fun observeMembership(ref: StorySourceRef): Flow<LibraryEntry?> = flowOf(null)
        override fun observeWindow(query: LibraryQuery): Flow<LibraryWindow> = flowOf(LibraryWindow(emptyList(), null))

        override suspend fun add(entry: LibraryEntry): LibraryMutationResult {
            mutationActive = true
            addEntered.complete(Unit)
            releaseAdd.await()
            mutationActive = false
            return LibraryMutationResult.CHANGED
        }

        override suspend fun remove(ref: StorySourceRef): LibraryMutationResult {
            overlapObserved = mutationActive
            return LibraryMutationResult.NO_OP
        }

        override suspend fun enrichSnapshot(
            ref: StorySourceRef,
            snapshot: LibraryPresentationSnapshot,
        ) = LibraryMutationResult.NO_OP
    }

    private fun ref(sourceStoryId: String): StorySourceRef = StorySourceRef(
        storyId = SourceStoryIdV1.derive(SourceStoryKey(SOURCE_KEY, sourceStoryId)),
        catalogSourceKey = SOURCE_KEY,
        sourceStoryId = sourceStoryId,
    )

    private companion object {
        val SOURCE_KEY = CatalogSourceKey("fixture.source")
    }
}
