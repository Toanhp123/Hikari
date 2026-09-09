package app.openstory.catalog.runtime.concurrency

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CatalogMutationGate {
    private val mutex = Mutex()

    suspend fun <T> withMutation(block: suspend () -> T): T = mutex.withLock { block() }
}
