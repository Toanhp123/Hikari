package app.universalmedia

import app.universalmedia.core.domain.LocalAccessState
import app.universalmedia.core.domain.LocalRootDescriptor
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.StorageRootStore
import app.universalmedia.core.model.RootId
import app.universalmedia.feature.library.LibraryError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LibraryCoordinatorTest {
    private val evidence =
        RootRegistrationEvidence(LocalRootDescriptor("provider", "opaque"), true, 1)
    private val root =
        StorageRoot(RootId.generate(), evidence.descriptor, 1, LocalAccessState.READABLE)
    private val events = mutableListOf<String>()
    private val store = object : StorageRootStore {
        override suspend fun registerOrReauthorize(
            evidence: RootRegistrationEvidence,
        ): StorageRoot {
            events += "saved"
            return root
        }
        override suspend fun get(rootId: RootId): StorageRoot? = root
    }

    @Test
    fun scanReceivesPersistedRootOnlyAfterRegistrationCommits() = runBlocking {
        val coordinator = LibraryCoordinator(store) { id ->
            assertEquals(root.id, id)
            events += "scheduled"
        }
        assertNull(coordinator.register(evidence))
        assertEquals(listOf("saved", "scheduled"), events)
    }

    @Test
    fun schedulingFailureRetainsSavedRootAndCanRetry() = runBlocking {
        var fail = true
        val coordinator = LibraryCoordinator(store) {
            if (fail) error("scheduler unavailable")
        }
        assertEquals(LibraryError.SCHEDULING, coordinator.register(evidence))
        assertEquals(listOf("saved"), events)
        fail = false
        assertNull(coordinator.retry(root.id))
        assertEquals(listOf("saved"), events)
    }

    @Test
    fun failedPersistenceNeverEnqueuesScan() = runBlocking {
        val failingStore = object : StorageRootStore {
            override suspend fun registerOrReauthorize(
                evidence: RootRegistrationEvidence,
            ): StorageRoot = error("disk full")
            override suspend fun get(rootId: RootId): StorageRoot? = null
        }
        val coordinator = LibraryCoordinator(failingStore) { events += "scheduled" }
        assertEquals(LibraryError.STORAGE, coordinator.register(evidence))
        assertEquals(emptyList<String>(), events)
    }

    @Test
    fun cancellationIsNotReportedAsRegistrationFailure() {
        val coordinator = LibraryCoordinator(store) { throw CancellationException() }
        assertThrows(CancellationException::class.java) {
            runBlocking { coordinator.register(evidence) }
        }
    }
}
