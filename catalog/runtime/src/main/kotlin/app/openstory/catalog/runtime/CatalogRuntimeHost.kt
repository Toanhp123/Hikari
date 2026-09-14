package app.openstory.catalog.runtime

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.source.CatalogAuthorityDescriptor
import app.openstory.catalog.domain.source.CatalogAuthorityResolver
import app.openstory.catalog.runtime.execution.CatalogExecutionDispatchers
import app.openstory.catalog.runtime.source.CatalogSourceBinding
import app.openstory.catalog.runtime.trace.CatalogTraceSink
import java.util.concurrent.atomic.AtomicBoolean

interface CatalogRuntimeHost : AutoCloseable {
    fun descriptor(sourceKey: CatalogSourceKey): CatalogAuthorityDescriptor?

    fun authorityResolver(): CatalogAuthorityResolver

    suspend fun activate(sourceKey: CatalogSourceKey): CatalogCapabilityActivation
}

internal class DefaultCatalogRuntimeHost(
    bindings: Collection<CatalogSourceBinding>,
    private val storeOwner: CatalogStoreOwner,
    private val wallClockEpochMs: () -> Long,
    private val dispatchers: CatalogExecutionDispatchers,
    private val traceSink: CatalogTraceSink,
    private val ownershipCallbacks: CatalogRuntimeOwnershipCallbacks,
    authorityResolver: CatalogAuthorityResolver?,
) : CatalogRuntimeHost {
    private val bindingsByKey = bindings.associateBy(CatalogSourceBinding::catalogSourceKey).also {
        require(it.size == bindings.size)
    }
    private val descriptorsByKey = bindingsByKey.mapValues { (_, binding) -> binding.descriptor() }
    private val resolver = authorityResolver ?: defaultResolver(descriptorsByKey.values)
    private val sessions = mutableMapOf<CatalogSourceKey, CatalogCapabilitySession>()
    private val closed = AtomicBoolean(false)

    override fun descriptor(sourceKey: CatalogSourceKey): CatalogAuthorityDescriptor? =
        descriptorsByKey[sourceKey]

    override fun authorityResolver(): CatalogAuthorityResolver = resolver

    override suspend fun activate(sourceKey: CatalogSourceKey): CatalogCapabilityActivation {
        val binding = bindingsByKey[sourceKey]
        val session = synchronized(sessions) {
            if (closed.get() || binding == null) {
                null
            } else {
                sessions.getOrPut(sourceKey) {
                    CatalogCapabilitySession(
                        binding = binding,
                        storeOwner = storeOwner,
                        wallClockEpochMs = wallClockEpochMs,
                        dispatchers = dispatchers,
                        traceSink = traceSink,
                        ownershipCallbacks = ownershipCallbacks,
                    )
                }
            }
        }
        return session?.activate() ?: CatalogCapabilityActivation.Unavailable()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(sessions) {
            sessions.values.forEach(CatalogCapabilitySession::close)
            sessions.clear()
        }
        storeOwner.close()
    }

    private companion object {
        fun defaultResolver(descriptors: Collection<CatalogAuthorityDescriptor>): CatalogAuthorityResolver {
            val authorityByMedia = CatalogMediaType.entries.associateWith { mediaType ->
                descriptors.firstOrNull { descriptor ->
                    descriptor.capabilities.discover && mediaType in descriptor.mediaTypes
                }?.sourceKey
            }
            return CatalogAuthorityResolver(authorityByMedia::get)
        }
    }
}
