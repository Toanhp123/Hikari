package app.openstory.catalog.runtime

import app.openstory.catalog.domain.read.DiscoverReadPort
import app.openstory.catalog.domain.read.StoryDetailReadPort
import app.openstory.catalog.domain.write.CatalogWritePort
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import app.openstory.catalog.storage.CatalogStorageFactory
import app.openstory.catalog.storage.RoomCatalogStore

class CatalogRuntimeFactory internal constructor(
    private val binding: CatalogSourceBinding?,
    private val openStorage: suspend () -> CatalogRuntimeStore,
    private val wallClockEpochMs: () -> Long,
    private val dispatchers: CatalogExecutionDispatchers,
) {
    constructor(
        storageFactory: CatalogStorageFactory,
        binding: CatalogSourceBinding?,
        wallClockEpochMs: () -> Long = System::currentTimeMillis,
        dispatchers: CatalogExecutionDispatchers = CatalogExecutionDispatchers(),
    ) : this(
        binding = binding,
        openStorage = { RoomCatalogStoreAdapter(storageFactory.open()) },
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
    )

    fun createSession(): CatalogCapabilitySession = CatalogCapabilitySession(
        binding = binding,
        openStorage = openStorage,
        wallClockEpochMs = wallClockEpochMs,
        dispatchers = dispatchers,
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
