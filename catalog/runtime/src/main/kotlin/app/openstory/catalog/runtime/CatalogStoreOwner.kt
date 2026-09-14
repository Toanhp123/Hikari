package app.openstory.catalog.runtime

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.runtime.concurrency.CatalogMutationGate
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.retention.ActiveStoryPins
import java.util.concurrent.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal data class CatalogRetentionDomain(
    val store: CatalogRuntimeStore,
    val activeStoryPins: ActiveStoryPins,
)

internal class CatalogStoreOwner(
    private val openStorage: suspend () -> CatalogRuntimeStore,
    private val dispatchers: CatalogExecutionDispatchers,
    private val ownershipCallbacks: CatalogRuntimeOwnershipCallbacks,
) : AutoCloseable {
    private val openMutex = Mutex()
    private val stateLock = Any()
    private var closed = false
    private var domain: CatalogRetentionDomain? = null

    suspend fun retentionDomain(): CatalogRetentionDomain {
        synchronized(stateLock) {
            check(!closed)
            domain?.let { return it }
        }
        return openMutex.withLock {
            synchronized(stateLock) {
                check(!closed)
                domain?.let { return@withLock it }
            }
            val openedStore = openMappedStorage()
            val openedDomain = CatalogRetentionDomain(
                store = openedStore,
                activeStoryPins = ActiveStoryPins(
                    writePort = openedStore,
                    mutationGate = CatalogMutationGate(),
                    onActivePinsChanged = ownershipCallbacks.onActiveStoryPinsChanged,
                    onReleaseMutationTouched = ownershipCallbacks.onStoryReleaseMutationTouched,
                ),
            )
            synchronized(stateLock) {
                if (closed) {
                    openedStore.close()
                    error("Catalog store owner is closed")
                }
                domain = openedDomain
            }
            openedDomain
        }
    }

    override fun close() {
        val store = synchronized(stateLock) {
            if (closed) return
            closed = true
            domain?.store.also { domain = null }
        }
        store?.close()
    }

    private suspend fun openMappedStorage(): CatalogRuntimeStore =
        runCatching { withContext(dispatchers.io) { openStorage() } }
            .getOrElse { error ->
                throw error.toStorageException(CatalogStorageOperation.OPEN)
            }
}

private fun Throwable.toStorageException(operation: CatalogStorageOperation): Throwable = when (this) {
    is CancellationException, is CatalogFailureException -> this
    else -> CatalogFailureException(CatalogFailure.Storage(operation), this)
}
