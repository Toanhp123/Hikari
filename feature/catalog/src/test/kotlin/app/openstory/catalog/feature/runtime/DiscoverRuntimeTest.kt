package app.openstory.catalog.feature.runtime

import app.openstory.catalog.domain.identity.CatalogSourceKey
import app.openstory.catalog.domain.model.CatalogMediaType
import app.openstory.catalog.domain.source.CatalogAuthorityResolver
import app.openstory.catalog.runtime.CatalogCapabilityActivation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoverRuntimeTest {
    @Test
    fun discoverRuntimeFreezesAuthorityResolvedAtCreation() = runTest {
        val first = CatalogSourceKey("authority.first")
        val second = CatalogSourceKey("authority.second")
        var current = first
        val activations = mutableListOf<CatalogSourceKey>()
        val runtime = createFrozenDiscoverRuntime(
            mediaType = CatalogMediaType.MANGA,
            authorityResolver = CatalogAuthorityResolver { current },
            activateCatalog = { sourceKey ->
                activations += sourceKey
                CatalogCapabilityActivation.Unavailable()
            },
        )

        current = second
        runtime.activate()

        assertEquals(listOf(first), activations)
    }
}
