package app.openstory.catalog.runtime

import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.failure.CatalogFailureException
import app.openstory.catalog.domain.failure.CatalogStorageOperation
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionExecutor
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogImporter
import app.openstory.catalog.runtime.concurrency.CatalogMutationGate
import app.openstory.catalog.runtime.discover.DiscoverSession
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.retention.ActiveStoryPins
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import app.openstory.catalog.runtime.story.StoryDetailSession
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface CatalogCapabilityActivation {
    data class Unavailable(
        val failure: CatalogFailure = CatalogFailure.SourceUnavailable,
    ) : CatalogCapabilityActivation

    class Available internal constructor(
        private val binding: CatalogSourceBinding,
        private val store: CatalogRuntimeStore,
        private val executor: CatalogAcquisitionExecutor,
        private val activeStoryPins: ActiveStoryPins,
        private val wallClockEpochMs: () -> Long,
        private val scope: CoroutineScope,
    ) : CatalogCapabilityActivation {
        private val discoverSessions = mutableMapOf<CatalogMediaType, DiscoverSession>()
        private val storySessions = mutableMapOf<StorySourceRef, StoryDetailSession>()

        val assetPolicyProvider = SourceAssetPolicyProvider { sourceKey ->
            binding.assetPolicy?.takeIf { sourceKey == binding.catalogSourceKey }
        }

        @Synchronized
        fun discoverSession(mediaType: CatalogMediaType): DiscoverSession =
            discoverSessions.getOrPut(mediaType) {
                DiscoverSession(
                    binding = binding,
                    mediaType = mediaType,
                    readPort = store,
                    executor = executor,
                    scope = scope,
                )
            }

        suspend fun acquireDiscover(mediaType: CatalogMediaType): CatalogAcquisitionResult =
            executor.acquireDiscover(mediaType)

        @Synchronized
        fun storyDetailSession(ref: StorySourceRef): StoryDetailSession =
            storySessions.getOrPut(ref) {
                StoryDetailSession(
                    binding = binding,
                    ref = ref,
                    readPort = store,
                    writePort = store,
                    activeStoryPins = activeStoryPins,
                    executor = executor,
                    wallClockEpochMs = wallClockEpochMs,
                    scope = scope,
                    onReleased = { released ->
                        synchronized(this) {
                            if (storySessions[ref] === released) storySessions.remove(ref)
                        }
                    },
                )
            }
    }
}

class CatalogCapabilitySession internal constructor(
    private val binding: CatalogSourceBinding?,
    private val openStorage: suspend () -> CatalogRuntimeStore,
    private val wallClockEpochMs: () -> Long,
    private val dispatchers: CatalogExecutionDispatchers,
) : AutoCloseable {
    private val activationMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val closed = AtomicBoolean(false)
    private var activation: CatalogCapabilityActivation? = null
    private var openedStore: CatalogRuntimeStore? = null

    suspend fun activate(): CatalogCapabilityActivation = activationMutex.withLock {
        activation?.let { return it }
        check(!closed.get())
        val sourceBinding = binding ?: return CatalogCapabilityActivation.Unavailable().also {
            activation = it
        }
        val store = openMappedStorage()
        val mutationGate = CatalogMutationGate()
        val activeStoryPins = ActiveStoryPins(store, mutationGate)
        val importer = CatalogImporter(store, activeStoryPins, dispatchers)
        val executor = CatalogAcquisitionExecutor(
            binding = sourceBinding,
            importer = importer,
            wallClockEpochMs = wallClockEpochMs,
            dispatchers = dispatchers,
            parentScope = scope,
        )
        CatalogCapabilityActivation.Available(
            binding = sourceBinding,
            store = store,
            executor = executor,
            activeStoryPins = activeStoryPins,
            wallClockEpochMs = wallClockEpochMs,
            scope = scope,
        ).also {
            openedStore = store
            activation = it
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        openedStore?.close()
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
