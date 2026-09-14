package app.openstory.library.runtime

import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.common.Clock
import app.openstory.library.domain.LibraryEntry
import app.openstory.library.domain.LibraryMutationResult
import app.openstory.library.domain.LibraryPort
import app.openstory.library.domain.LibraryPresentationSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LibraryMutationOwner(
    private val port: LibraryPort,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun add(
        ref: StorySourceRef,
        originMediaContext: CatalogMediaType,
        snapshot: LibraryPresentationSnapshot,
    ): LibraryMutationResult = mutex.withLock {
        port.add(
            LibraryEntry(
                ref = ref,
                originMediaContext = originMediaContext,
                savedAtEpochMs = clock.nowEpochMillis(),
                snapshot = snapshot,
            ),
        )
    }

    suspend fun remove(ref: StorySourceRef): LibraryMutationResult = mutex.withLock {
        port.remove(ref)
    }

    suspend fun enrichSnapshot(
        ref: StorySourceRef,
        snapshot: LibraryPresentationSnapshot,
    ): LibraryMutationResult = mutex.withLock {
        port.enrichSnapshot(ref, snapshot)
    }
}
