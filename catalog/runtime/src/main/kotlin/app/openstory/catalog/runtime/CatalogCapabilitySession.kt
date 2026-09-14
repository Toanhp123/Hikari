package app.openstory.catalog.runtime

import app.openstory.catalog.domain.asset.SourceAssetPolicyProvider
import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.identity.StorySourceRef
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionExecutor
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.acquisition.CatalogImporter
import app.openstory.catalog.runtime.discover.DiscoverSession
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.retention.ActiveStoryPins
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import app.openstory.catalog.runtime.story.StoryDetailSession
import app.openstory.catalog.runtime.trace.CatalogTrace
import app.openstory.catalog.runtime.trace.CatalogTraceSink
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
        private val traceSink: CatalogTraceSink,
    ) : CatalogCapabilityActivation {
        private val discoverSessions = mutableMapOf<CatalogMediaType, DiscoverSession>()
        private val storySessions = mutableMapOf<StorySourceRef, StoryDetailSession>()
        private val firstDiscoverSnapshotTraced = AtomicBoolean(false)

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
                    onFirstSnapshot = {
                        if (firstDiscoverSnapshotTraced.compareAndSet(false, true)) {
                            traceSink.mark(CatalogTrace.DISCOVER_FIRST_SNAPSHOT)
                        }
                    },
                )
            }

        suspend fun acquireDiscover(mediaType: CatalogMediaType): CatalogAcquisitionResult =
            executor.acquireDiscover(mediaType)

        internal fun activeWorkCount(): Int = executor.activeWorkCount()

        internal suspend fun activeStoryPinCount(): Int = activeStoryPins.snapshot().size

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
                    traceSink = traceSink,
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
    private val storeOwner: CatalogStoreOwner,
    private val wallClockEpochMs: () -> Long,
    private val dispatchers: CatalogExecutionDispatchers,
    private val traceSink: CatalogTraceSink,
    private val ownershipCallbacks: CatalogRuntimeOwnershipCallbacks,
    private val closeStoreOwner: AutoCloseable? = null,
) : AutoCloseable {
    private val activationMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val closed = AtomicBoolean(false)
    private var activation: CatalogCapabilityActivation? = null

    suspend fun activate(): CatalogCapabilityActivation = activationMutex.withLock {
        activation?.let { return it }
        check(!closed.get())
        val sourceBinding = binding ?: return CatalogCapabilityActivation.Unavailable().also {
            activation = it
        }
        traceSink.mark(CatalogTrace.ACTIVATION_START)
        val retentionDomain = storeOwner.retentionDomain()
        val store = retentionDomain.store
        traceSink.mark(CatalogTrace.STORAGE_READY)
        val activeStoryPins = retentionDomain.activeStoryPins
        val importer = CatalogImporter(store, activeStoryPins, dispatchers)
        val executor = CatalogAcquisitionExecutor(
            binding = sourceBinding,
            importer = importer,
            wallClockEpochMs = wallClockEpochMs,
            dispatchers = dispatchers,
            parentScope = scope,
            onActiveWorkChanged = ownershipCallbacks.onActiveWorkChanged,
            onDiscoverMutationTouched = ownershipCallbacks.onDiscoverMutationTouched,
        )
        CatalogCapabilityActivation.Available(
            binding = sourceBinding,
            store = store,
            executor = executor,
            activeStoryPins = activeStoryPins,
            wallClockEpochMs = wallClockEpochMs,
            scope = scope,
            traceSink = traceSink,
        ).also {
            activation = it
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        scope.cancel()
        activation = null
        closeStoreOwner?.close()
    }
}
