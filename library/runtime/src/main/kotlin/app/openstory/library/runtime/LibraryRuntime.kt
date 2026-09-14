package app.openstory.library.runtime

import android.content.Context
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.common.Clock
import app.openstory.common.SystemClock
import app.openstory.library.domain.LibraryEntry
import app.openstory.library.domain.LibraryPort
import app.openstory.library.domain.LibraryQuery
import app.openstory.library.storage.LibraryStorageFactory
import app.openstory.library.storage.RoomLibraryStore
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow

class LibraryRuntime private constructor(
    private val store: LibraryPort,
    private val closeStore: AutoCloseable,
    clock: Clock,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    val mutations = LibraryMutationOwner(store, clock)

    constructor(
        context: Context,
        clock: Clock = SystemClock,
    ) : this(
        store = LibraryStorageFactory(context).open(),
        clock = clock,
    )

    internal constructor(
        store: LibraryPort,
        clock: Clock,
    ) : this(store, store as? AutoCloseable ?: AutoCloseable {}, clock)

    private constructor(
        store: RoomLibraryStore,
        clock: Clock,
    ) : this(store, store, clock)

    fun observeMembership(ref: StorySourceRef): Flow<LibraryEntry?> = store.observeMembership(ref)

    fun createQuerySession(initialQuery: LibraryQuery): LibraryQuerySession {
        check(!closed.get())
        return LibraryQuerySession(store, initialQuery)
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) closeStore.close()
    }
}
