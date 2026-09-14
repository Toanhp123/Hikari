package app.openstory.catalog.runtime

import android.content.Context
import app.openstory.catalog.domain.read.DiscoverReadPort
import app.openstory.catalog.domain.read.StoryDetailReadPort
import app.openstory.catalog.domain.source.CatalogAuthorityResolver
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import app.openstory.catalog.runtime.trace.CatalogTraceSink
import app.openstory.catalog.storage.CatalogStorageFactory
import app.openstory.catalog.storage.RoomCatalogStore

class CatalogRuntimeFactory internal constructor(
    private val bindings: List<CatalogSourceBinding>,
    private val openStorage: suspend () -> CatalogRuntimeStore,
    private val wallClockEpochMs: () -> Long,
    private val dispatchers: CatalogExecutionDispatchers,
    private val traceSink: CatalogTraceSink = CatalogTraceSink {},
    private val ownershipCallbacks: CatalogRuntimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
    private val authorityResolver: CatalogAuthorityResolver? = null,
) {
    internal constructor(
        binding: CatalogSourceBinding?,
        openStorage: suspend () -> CatalogRuntimeStore,
        wallClockEpochMs: () -> Long,
        dispatchers: CatalogExecutionDispatchers,
        traceSink: CatalogTraceSink = CatalogTraceSink {},
        ownershipCallbacks: CatalogRuntimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
    ) : this(
        bindings = listOfNotNull(binding),
        openStorage = openStorage,
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
        traceSink = traceSink,
        ownershipCallbacks = ownershipCallbacks,
    )

    constructor(
        context: Context,
        binding: CatalogSourceBinding?,
        wallClockEpochMs: () -> Long = System::currentTimeMillis,
        dispatchers: CatalogExecutionDispatchers = CatalogExecutionDispatchers(),
        traceSink: CatalogTraceSink = CatalogTraceSink {},
        queryListener: ((String) -> Unit)? = null,
        ownershipCallbacks: CatalogRuntimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
    ) : this(
        context = context,
        bindings = listOfNotNull(binding),
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
        traceSink = traceSink,
        queryListener = queryListener,
        ownershipCallbacks = ownershipCallbacks,
    )

    constructor(
        context: Context,
        bindings: Collection<CatalogSourceBinding>,
        authorityResolver: CatalogAuthorityResolver? = null,
        wallClockEpochMs: () -> Long = System::currentTimeMillis,
        dispatchers: CatalogExecutionDispatchers = CatalogExecutionDispatchers(),
        traceSink: CatalogTraceSink = CatalogTraceSink {},
        queryListener: ((String) -> Unit)? = null,
        ownershipCallbacks: CatalogRuntimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
    ) : this(
        storageFactory = CatalogStorageFactory(context, onQuery = queryListener),
        bindings = bindings.toList(),
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
        traceSink = traceSink,
        ownershipCallbacks = ownershipCallbacks,
        authorityResolver = authorityResolver,
    )

    private constructor(
        storageFactory: CatalogStorageFactory,
        bindings: List<CatalogSourceBinding>,
        wallClockEpochMs: () -> Long = System::currentTimeMillis,
        dispatchers: CatalogExecutionDispatchers = CatalogExecutionDispatchers(),
        traceSink: CatalogTraceSink = CatalogTraceSink {},
        ownershipCallbacks: CatalogRuntimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
        authorityResolver: CatalogAuthorityResolver? = null,
    ) : this(
        bindings = bindings,
        openStorage = { RoomCatalogStoreAdapter(storageFactory.open()) },
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
        traceSink = traceSink,
        ownershipCallbacks = ownershipCallbacks,
        authorityResolver = authorityResolver,
    )

    internal constructor(
        bindings: Collection<CatalogSourceBinding>,
        openStorage: suspend () -> CatalogRuntimeStore,
        wallClockEpochMs: () -> Long,
        dispatchers: CatalogExecutionDispatchers,
        traceSink: CatalogTraceSink = CatalogTraceSink {},
        ownershipCallbacks: CatalogRuntimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
        authorityResolver: CatalogAuthorityResolver? = null,
    ) : this(
        bindings = bindings.toList(),
        openStorage = openStorage,
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
        traceSink = traceSink,
        ownershipCallbacks = ownershipCallbacks,
        authorityResolver = authorityResolver,
    )

    fun createHost(): CatalogRuntimeHost = DefaultCatalogRuntimeHost(
        bindings = bindings,
        storeOwner = createStoreOwner(),
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
        traceSink = traceSink,
        ownershipCallbacks = ownershipCallbacks,
        authorityResolver = authorityResolver,
    )

    fun createSession(): CatalogCapabilitySession {
        require(bindings.size <= 1) { "A direct capability session accepts at most one authority." }
        val storeOwner = createStoreOwner()
        return CatalogCapabilitySession(
            binding = bindings.singleOrNull(),
            storeOwner = storeOwner,
            wallClockEpochMs = wallClockEpochMs,
            dispatchers = dispatchers,
            traceSink = traceSink,
            ownershipCallbacks = ownershipCallbacks,
            closeStoreOwner = storeOwner,
        )
    }

    private fun createStoreOwner() = CatalogStoreOwner(
        openStorage = openStorage,
        dispatchers = dispatchers,
        ownershipCallbacks = ownershipCallbacks,
    )
}

internal interface CatalogRuntimeStore :
    DiscoverReadPort,
    StoryDetailReadPort,
    CatalogWritePort,
    AutoCloseable

private class RoomCatalogStoreAdapter(
    private val delegate: RoomCatalogStore,
) : CatalogRuntimeStore,
    DiscoverReadPort by delegate,
    StoryDetailReadPort by delegate,
    CatalogWritePort by delegate,
    AutoCloseable by delegate
