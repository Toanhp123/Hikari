package app.openstory.catalog.runtime

import android.content.Context
import app.openstory.catalog.domain.read.DiscoverReadPort
import app.openstory.catalog.domain.read.StoryDetailReadPort
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import app.openstory.catalog.runtime.trace.CatalogTraceSink
import app.openstory.catalog.storage.CatalogStorageFactory
import app.openstory.catalog.storage.RoomCatalogStore

class CatalogRuntimeFactory internal constructor(
    private val binding: CatalogSourceBinding?,
    private val openStorage: suspend () -> CatalogRuntimeStore,
    private val wallClockEpochMs: () -> Long,
    private val dispatchers: CatalogExecutionDispatchers,
    private val traceSink: CatalogTraceSink = CatalogTraceSink {},
    private val ownershipCallbacks: CatalogRuntimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
) {
    constructor(
        context: Context,
        binding: CatalogSourceBinding?,
        wallClockEpochMs: () -> Long = System::currentTimeMillis,
        dispatchers: CatalogExecutionDispatchers = CatalogExecutionDispatchers(),
        traceSink: CatalogTraceSink = CatalogTraceSink {},
        queryListener: ((String) -> Unit)? = null,
        ownershipCallbacks: CatalogRuntimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
    ) : this(
        storageFactory = CatalogStorageFactory(context, onQuery = queryListener),
        binding = binding,
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
        traceSink = traceSink,
        ownershipCallbacks = ownershipCallbacks,
    )

    private constructor(
        storageFactory: CatalogStorageFactory,
        binding: CatalogSourceBinding?,
        wallClockEpochMs: () -> Long = System::currentTimeMillis,
        dispatchers: CatalogExecutionDispatchers = CatalogExecutionDispatchers(),
        traceSink: CatalogTraceSink = CatalogTraceSink {},
        ownershipCallbacks: CatalogRuntimeOwnershipCallbacks = CatalogRuntimeOwnershipCallbacks(),
    ) : this(
        binding = binding,
        openStorage = { RoomCatalogStoreAdapter(storageFactory.open()) },
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
        traceSink = traceSink,
        ownershipCallbacks = ownershipCallbacks,
    )

    fun createSession(): CatalogCapabilitySession = CatalogCapabilitySession(
        binding = binding,
        openStorage = openStorage,
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
        traceSink = traceSink,
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
