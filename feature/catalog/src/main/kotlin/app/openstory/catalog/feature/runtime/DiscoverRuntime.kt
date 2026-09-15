package app.openstory.catalog.feature.runtime

import app.openstory.catalog.domain.failure.CatalogFailure
import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.source.CatalogAuthorityResolver
import app.openstory.catalog.feature.CatalogCompositionDiagnostics
import app.openstory.catalog.feature.NoOpCatalogCompositionDiagnostics
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import app.openstory.catalog.runtime.discover.DiscoverSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

internal interface DiscoverRuntime : AutoCloseable {
    suspend fun activate(): DiscoverRuntimeActivation
}

internal sealed interface DiscoverRuntimeActivation {
    data class Unavailable(val failure: CatalogFailure) : DiscoverRuntimeActivation

    data class Available(
        val observe: (CatalogMediaType) -> Flow<DiscoverSessionState>,
        val refresh: suspend (CatalogMediaType) -> CatalogAcquisitionResult,
        val quiesce: suspend (CatalogMediaType) -> Unit = {},
    ) : DiscoverRuntimeActivation
}

internal fun createFrozenDiscoverRuntime(
    mediaType: CatalogMediaType,
    authorityResolver: CatalogAuthorityResolver,
    activateCatalog: suspend (CatalogSourceKey) -> CatalogCapabilityActivation,
    diagnostics: CatalogCompositionDiagnostics = NoOpCatalogCompositionDiagnostics,
): DiscoverRuntime {
    val frozenSourceKey = authorityResolver.authorityFor(mediaType)
    return object : DiscoverRuntime {
        override suspend fun activate(): DiscoverRuntimeActivation =
            when (val sourceKey = frozenSourceKey) {
                null -> DiscoverRuntimeActivation.Unavailable(CatalogFailure.SourceUnavailable)
                else -> when (val activation = activateCatalog(sourceKey)) {
                    is CatalogCapabilityActivation.Unavailable ->
                        DiscoverRuntimeActivation.Unavailable(activation.failure)
                    is CatalogCapabilityActivation.Available -> DiscoverRuntimeActivation.Available(
                        observe = { requestedMediaType ->
                            activation.discoverSession(requestedMediaType).states
                                .onStart { diagnostics.discoverCollectorStarted() }
                                .onCompletion { diagnostics.discoverCollectorStopped() }
                        },
                        refresh = { requestedMediaType ->
                            activation.discoverSession(requestedMediaType).refresh()
                        },
                        quiesce = { requestedMediaType ->
                            activation.discoverSession(requestedMediaType).quiesce()
                        },
                    )
                }
            }

        override fun close() = Unit
    }
}
