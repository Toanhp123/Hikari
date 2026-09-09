package app.openstory.catalog.feature.seed

import android.content.Context
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.read.DiscoverPersistenceState
import app.openstory.catalog.feature.VariantCatalogBinding
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import app.openstory.catalog.runtime.CatalogRuntimeFactory
import app.openstory.catalog.runtime.acquisition.CatalogAcquisitionResult
import kotlinx.coroutines.flow.first

public object BenchmarkCatalogFixture {
    public suspend fun prepare(context: Context) {
        val binding = requireNotNull(VariantCatalogBinding.binding)
        val session = CatalogRuntimeFactory(context.applicationContext, binding).createSession()
        try {
            val activation = session.activate() as? CatalogCapabilityActivation.Available
                ?: error("Benchmark Catalog source is unavailable.")
            CatalogMediaType.entries.forEach { mediaType ->
                check(activation.acquireDiscover(mediaType) == CatalogAcquisitionResult.Success) {
                    "Benchmark Catalog fixture import failed for $mediaType."
                }
                activation.discoverSession(mediaType).states.first { state ->
                    val published = state.persistence as? DiscoverPersistenceState.Published
                    published?.provenance?.catalogSourceKey == binding.catalogSourceKey &&
                        published.provenance.sourceVersion == binding.sourceVersion
                }
            }
        } finally {
            session.close()
        }
    }
}
