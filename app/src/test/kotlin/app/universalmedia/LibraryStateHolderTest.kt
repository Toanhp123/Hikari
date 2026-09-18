package app.universalmedia

import app.universalmedia.core.domain.LibraryCard
import app.universalmedia.core.domain.LibraryQueries
import app.universalmedia.core.domain.LibraryRootSummary
import app.universalmedia.core.domain.LocalAccessState
import app.universalmedia.core.domain.RootRegistrationEvidence
import app.universalmedia.core.domain.StorageRoot
import app.universalmedia.core.domain.StorageRootStore
import app.universalmedia.core.model.MediaId
import app.universalmedia.core.model.MediaKind
import app.universalmedia.core.model.RootId
import app.universalmedia.feature.library.LibraryError
import app.universalmedia.feature.library.LibraryScanStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryStateHolderTest {
    @Test
    fun registrationStaysBusyAcrossSuspensionAndRejectsDuplicateSubmission() = runBlocking {
        val evidence = CompletableDeferred<RootRegistrationEvidence?>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val queries = object : LibraryQueries {
            override suspend fun observeLibraryCards(onCards: suspend (List<LibraryCard>) -> Unit) =
                onCards(emptyList())
            override suspend fun observeLibraryRoots(
                onRoots: suspend (List<LibraryRootSummary>) -> Unit,
            ) = onRoots(emptyList())
        }
        val store = object : StorageRootStore {
            override suspend fun registerOrReauthorize(
                evidence: RootRegistrationEvidence,
            ): StorageRoot = error("must not persist failed grant")
            override suspend fun get(rootId: RootId): StorageRoot? = null
        }
        try {
            val holder = LibraryStateHolder(queries, LibraryCoordinator(store) {}, scope)
            var reads = 0
            holder.register {
                reads++
                evidence.await()
            }
            assertEquals(true, holder.state.value.isAddingRoot)
            holder.register {
                reads++
                null
            }
            assertEquals(1, reads)
            evidence.complete(null)
            assertEquals(false, holder.state.value.isAddingRoot)
            assertEquals(LibraryError.REGISTRATION, holder.state.value.error)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun durableScanUpdatesNeverHideLibraryAndFailedObservationCanRetry() = runBlocking {
        val media = MediaId.generate()
        val root = RootId.generate()
        val roots =
            MutableStateFlow(
                listOf(LibraryRootSummary(root, LocalAccessState.READABLE, true, null)),
            )
        var failCards = true
        val queries = object : LibraryQueries {
            override suspend fun observeLibraryCards(onCards: suspend (List<LibraryCard>) -> Unit) {
                if (failCards) error("database unavailable")
                onCards(listOf(LibraryCard(media, MediaKind.VIDEO, "movie.mp4")))
            }
            override suspend fun observeLibraryRoots(
                onRoots: suspend (List<LibraryRootSummary>) -> Unit,
            ) {
                roots.collect { onRoots(it) }
            }
        }
        val unusedStore = object : StorageRootStore {
            override suspend fun registerOrReauthorize(
                evidence: RootRegistrationEvidence,
            ): StorageRoot = error("unused")
            override suspend fun get(rootId: RootId): StorageRoot? = null
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val holder = LibraryStateHolder(queries, LibraryCoordinator(unusedStore) {}, scope)
            assertEquals(LibraryError.LIBRARY, holder.state.value.error)
            holder.retry(root)
            assertEquals(LibraryError.LIBRARY, holder.state.value.error)
            failCards = false
            holder.refresh()
            assertEquals(media, holder.state.value.cards.single().mediaId)
            assertEquals(LibraryScanStatus.RUNNING, holder.state.value.roots.single().status)
            roots.value = listOf(LibraryRootSummary(root, LocalAccessState.ACCESS_LOST, true, null))
            assertEquals(LibraryScanStatus.ACCESS_LOST, holder.state.value.roots.single().status)
            assertEquals(media, holder.state.value.cards.single().mediaId)
        } finally {
            scope.cancel()
        }
    }
}
